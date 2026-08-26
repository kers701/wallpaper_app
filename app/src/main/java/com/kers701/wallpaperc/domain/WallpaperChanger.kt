package com.kers701.wallpaperc.domain

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.kers701.wallpaperc.data.local.LocalFallbackStore
import com.kers701.wallpaperc.data.local.WallpaperDao
import com.kers701.wallpaperc.data.local.WallpaperEntity
import com.kers701.wallpaperc.data.prefs.SettingsRepository
import com.kers701.wallpaperc.data.remote.WallhavenApi
import com.kers701.wallpaperc.data.wallpaper.SystemWallpaperSetter
import kotlinx.coroutines.flow.first
import java.io.File

class WallpaperChanger(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val api: WallhavenApi,
    private val setter: SystemWallpaperSetter,
    private val dao: WallpaperDao,
    private val localStore: LocalFallbackStore = LocalFallbackStore(context)
) {
    suspend fun changeOnce(forceIgnoreScreenOff: Boolean = false): ChangeResult {
        val settings = settingsRepo.settingsFlow.first()

        if (!forceIgnoreScreenOff && settings.skipWhenScreenOff && isScreenOff()) {
            return ChangeResult.Failure("息屏已跳过本次更换")
        }

        if (settings.forceLocalMode) {
            // 隔离时本地也要两张
            if (settings.isolateHomeLock && settings.target == WallpaperTarget.Both && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val home = applyLocalFallback(settings, "强制本地模式", WallpaperTarget.Home, excludeIds = emptySet())
                if (home is ChangeResult.Failure) return home
                val used = setOf((home as ChangeResult.Success).item.id.substringBefore("_"))
                val lock = applyLocalFallback(settings, "强制本地模式", WallpaperTarget.Lock, excludeIds = used)
                return combineIsolate(home, lock)
            }
            return applyLocalFallback(settings, "强制本地模式", settings.target, emptySet())
        }

        // 桌面锁屏隔离：必须触发两次独立下载与两次 setBitmap
        if (settings.isolateHomeLock &&
            settings.target == WallpaperTarget.Both &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        ) {
            val home = changeForTarget(settings, WallpaperTarget.Home, excludeIds = emptySet())
            if (home is ChangeResult.Failure) return home
            val homeId = (home as ChangeResult.Success).item.id
            val lock = changeForTarget(settings, WallpaperTarget.Lock, excludeIds = setOf(homeId))
            return combineIsolate(home, lock)
        }

        return changeForTarget(settings, settings.target, emptySet())
    }

    private fun combineIsolate(home: ChangeResult, lock: ChangeResult): ChangeResult {
        return when {
            home is ChangeResult.Success && lock is ChangeResult.Success ->
                ChangeResult.Success(
                    lock.item,
                    lock.localPath,
                    detail = "桌面[${home.item.id}]→锁屏[${lock.item.id}]（两次独立设置）"
                )
            home is ChangeResult.Success && lock is ChangeResult.Failure ->
                ChangeResult.Success(home.item, home.localPath, detail = "桌面已设，锁屏失败：${lock.message}")
            else -> home
        }
    }

    private suspend fun changeForTarget(
        settings: AppSettings,
        target: WallpaperTarget,
        excludeIds: Set<String>
    ): ChangeResult {
        val category = api.nextCategory(settings)
        val keyword = pickKeyword(settings)
        val (dw, dh) = setter.screenSize()

        var candidates: List<WallpaperItem> = emptyList()
        var lastError: String? = null
        var fromWallhaven = false
        var wallhavenTried = false
        var wallhavenHttpError = false

        wallhavenTried = true
        try {
            candidates = api.search(settings, category, keyword, 1, dw, dh)
            candidates = filterOrientation(candidates, settings).filter { it.id !in excludeIds }
            if (candidates.isEmpty()) {
                candidates = api.search(settings, category, keyword, 2, dw, dh)
                candidates = filterOrientation(candidates, settings).filter { it.id !in excludeIds }
            }
            fromWallhaven = candidates.isNotEmpty()
            if (candidates.isEmpty()) lastError = "Wallhaven 没找到符合要求的壁纸"
        } catch (e: Exception) {
            wallhavenHttpError = true
            lastError = "Wallhaven 连接失败: ${e.message}"
            val keys = settings.apiKeys.filter { it.isNotBlank() }
            if (keys.size > 1) {
                val next = (settings.apiKeyIndex + 1) % keys.size
                settingsRepo.setApiKeyIndex(next)
                try {
                    candidates = api.search(settings.copy(apiKeyIndex = next), category, keyword, 1, dw, dh)
                    candidates = filterOrientation(candidates, settings).filter { it.id !in excludeIds }
                    fromWallhaven = candidates.isNotEmpty()
                    if (fromWallhaven) {
                        lastError = null
                        wallhavenHttpError = false
                    } else lastError = "Wallhaven 没找到符合要求的壁纸"
                } catch (e2: Exception) {
                    lastError = "Wallhaven 连接失败: ${e2.message}"
                }
            }
        }

        if (candidates.isNotEmpty()) {
            val item = candidates.firstOrNull { !dao.exists(it.id) && it.id !in excludeIds }
                ?: candidates.firstOrNull { it.id !in excludeIds }
                ?: candidates.first()
            when (val r = downloadAndSet(item, settings, category, fromWallhaven, keyword, target)) {
                is ChangeResult.Success -> return r
                is ChangeResult.Failure -> lastError = "Wallhaven 下载失败: ${r.message}"
            }
        }

        val fallbackUrls = settings.fallbackApiUrls()
        if (settings.networkFallbackEnabled && fallbackUrls.isNotEmpty()) {
            val errors = mutableListOf<String>()
            for ((i, url) in fallbackUrls.withIndex()) {
                try {
                    val fb = api.fetchFallbackApi(url, maxOf(settings.minWidth, dw), maxOf(settings.minHeight, dh))
                    // 兜底图用时间戳 id，隔离时天然不同
                    val unique = fb.copy(id = fb.id + "_${target.name}_${System.nanoTime() % 100000}")
                    when (val r = downloadAndSet(unique, settings, category, false, null, target)) {
                        is ChangeResult.Success -> return r
                        is ChangeResult.Failure -> errors += "#${i + 1} ${r.message}"
                    }
                } catch (e: Exception) {
                    errors += "#${i + 1} ${e.message ?: e.javaClass.simpleName}"
                }
            }
            lastError = "兜底 API 全部失败(${fallbackUrls.size}): ${errors.joinToString("；")}" +
                (lastError?.let { "（此前：$it）" } ?: "")
        } else if (!settings.networkFallbackEnabled) {
            if (wallhavenTried && !fromWallhaven) {
                lastError = if (wallhavenHttpError) lastError ?: "Wallhaven 连接失败"
                else "Wallhaven 没找到符合要求的壁纸"
            }
        } else if (fallbackUrls.isEmpty()) {
            lastError = (lastError ?: "Wallhaven 未成功") + "（未配置有效兜底 API URL）"
        }

        if (settings.localFallbackEnabled) {
            return applyLocalFallback(settings, lastError ?: "上游均未成功", target, excludeIds)
        }
        return ChangeResult.Failure(lastError ?: "未找到符合条件的壁纸")
    }

    private fun filterOrientation(list: List<WallpaperItem>, settings: AppSettings): List<WallpaperItem> {
        return when (settings.orientationFilter) {
            OrientationFilter.None -> list
            OrientationFilter.NoLandscape -> list.filter {
                if (it.width <= 0 || it.height <= 0) true else it.height >= it.width
            }
            OrientationFilter.NoPortrait -> list.filter {
                if (it.width <= 0 || it.height <= 0) true else it.width > it.height
            }
        }
    }

    private suspend fun applyLocalFallback(
        settings: AppSettings,
        reason: String,
        target: WallpaperTarget,
        excludeIds: Set<String>
    ): ChangeResult {
        if (!settings.localFallbackEnabled && !settings.forceLocalMode) {
            return ChangeResult.Failure(reason)
        }
        val files = localStore.listImages(settings)
            .filter { f ->
                val id = "local_${f.name}"
                id !in excludeIds && !excludeIds.any { f.name in it }
            }
        if (files.isEmpty()) {
            return ChangeResult.Failure("本地无可用图片（原因：$reason）")
        }
        val file = files.random()
        val item = WallpaperItem(
            id = "local_${file.name}",
            pathUrl = file.absolutePath,
            thumbsUrl = null,
            width = 0, height = 0,
            purity = "local", category = "local", source = "local"
        )
        if (!setter.setFromFile(file, target, settings.fitMode)) {
            return ChangeResult.Failure("系统设置壁纸失败")
        }
        val size = file.length()
        dao.insert(
            WallpaperEntity(
                id = "${item.id}_${target.name}",
                path = file.absolutePath,
                category = item.category,
                purity = item.purity,
                sourceUrl = file.absolutePath,
                setAt = System.currentTimeMillis(),
                width = 0, height = 0,
                fileSize = size,
                source = "local",
                keyword = ""
            )
        )
        settingsRepo.setLastChangeAt(System.currentTimeMillis())
        return ChangeResult.Success(
            item.copy(fileSize = size, category = "local←$reason"),
            file.absolutePath,
            detail = "${target.label}（本地）"
        )
    }

    private suspend fun downloadAndSet(
        item: WallpaperItem,
        settings: AppSettings,
        category: String,
        fromWallhaven: Boolean,
        usedKeyword: String?,
        target: WallpaperTarget
    ): ChangeResult {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val dest = File(dir, "${item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_${target.name}.jpg")

        val ok = if (item.source == "local") true
        else {
            val prefetched = item.prefetchedBytes
            if (prefetched != null && prefetched.isNotEmpty()) {
                runCatching {
                    dest.parentFile?.mkdirs()
                    dest.writeBytes(prefetched)
                    true
                }.getOrDefault(false)
            } else api.downloadToFile(item.pathUrl, dest)
        }
        val finalFile = if (item.source == "local") File(item.pathUrl) else dest
        if (!ok || !finalFile.exists() || finalFile.length() == 0L) {
            return ChangeResult.Failure("下载失败 (${item.source})")
        }

        if (!setter.setFromFile(finalFile, target, settings.fitMode)) {
            return ChangeResult.Failure("系统设置壁纸失败（部分机型锁屏需额外权限）")
        }

        val fileSize = finalFile.length()
        val kwRecord = if (item.source == "wallhaven") (usedKeyword ?: "") else ""
        dao.insert(
            WallpaperEntity(
                id = "${item.id}_${target.name}",
                path = finalFile.absolutePath,
                category = item.category,
                purity = item.purity,
                sourceUrl = item.pathUrl,
                setAt = System.currentTimeMillis(),
                width = item.width,
                height = item.height,
                fileSize = fileSize,
                source = item.source,
                keyword = kwRecord
            )
        )

        if (settings.categoryMode == CategoryMode.Rotate) {
            settingsRepo.setLastCategory(category)
        }

        if (fromWallhaven && item.source == "wallhaven") {
            var tags = item.tags
            if (tags.isEmpty()) {
                tags = runCatching {
                    api.fetchWallpaperTags(item.id, settings.nextApiKey())
                }.getOrDefault(emptyList())
            }
            val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleaned.isNotEmpty()) settingsRepo.setJumpKeywords(cleaned)
        }

        val active = settings.activeKeywords()
        if (settings.useKeywords && active.isNotEmpty()) {
            if (settings.jumpModeEnabled && settings.jumpKeywords.isNotEmpty()) {
                settingsRepo.setJumpKeywordIndex(settings.jumpKeywordIndex + 1)
            } else {
                settingsRepo.setKeywordIndex(settings.keywordIndex + 1)
            }
        }

        if (item.source != "local") trimCache(dir, keep = 40)
        settingsRepo.setLastChangeAt(System.currentTimeMillis())
        return ChangeResult.Success(
            item.copy(fileSize = fileSize),
            finalFile.absolutePath,
            detail = "${target.label}" + if (kwRecord.isNotBlank()) " · 词:$kwRecord" else ""
        )
    }

    private fun pickKeyword(settings: AppSettings): String? {
        if (!settings.useKeywords) return null
        val list = settings.activeKeywords()
        if (list.isEmpty()) return null
        return list[settings.activeKeywordIndex().mod(list.size)]
    }

    private fun isScreenOff(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isInteractive
    }

    private fun trimCache(dir: File, keep: Int) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { it.delete() }
    }
}
