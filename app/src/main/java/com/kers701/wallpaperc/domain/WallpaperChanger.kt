package com.kers701.wallpaperc.domain

import android.content.Context
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
            return applyLocalFallback(settings, reason = "强制本地模式")
        }

        val category = api.nextCategory(settings)
        val keyword = pickKeyword(settings)

        var candidates: List<WallpaperItem> = emptyList()
        var lastError: String? = null
        var fromWallhaven = false
        var wallhavenTried = false
        var wallhavenHttpError = false

        wallhavenTried = true
        try {
            candidates = api.search(settings, category, keyword)
            if (candidates.isEmpty()) {
                candidates = api.search(settings, category, keyword, page = 2)
            }
            fromWallhaven = candidates.isNotEmpty()
            if (candidates.isEmpty()) {
                lastError = "Wallhaven 没找到符合要求的壁纸"
            }
        } catch (e: Exception) {
            wallhavenHttpError = true
            lastError = "Wallhaven 连接失败: ${e.message}"
            val keys = settings.apiKeys.filter { it.isNotBlank() }
            if (keys.size > 1) {
                val next = (settings.apiKeyIndex + 1) % keys.size
                settingsRepo.setApiKeyIndex(next)
                try {
                    val retrySettings = settings.copy(apiKeyIndex = next)
                    candidates = api.search(retrySettings, category, keyword)
                    fromWallhaven = candidates.isNotEmpty()
                    if (fromWallhaven) {
                        lastError = null
                        wallhavenHttpError = false
                    } else {
                        lastError = "Wallhaven 没找到符合要求的壁纸"
                    }
                } catch (e2: Exception) {
                    lastError = "Wallhaven 连接失败: ${e2.message}"
                }
            }
        }

        if (candidates.isNotEmpty()) {
            val item = candidates.firstOrNull { !dao.exists(it.id) } ?: candidates.first()
            when (val r = downloadAndSet(
                item, settings, category,
                fromWallhaven = fromWallhaven,
                usedKeyword = keyword
            )) {
                is ChangeResult.Success -> return r
                is ChangeResult.Failure -> {
                    lastError = "Wallhaven 下载失败: ${r.message}"
                }
            }
        }

        if (settings.networkFallbackEnabled && settings.fallbackApiUrl.isNotBlank()) {
            try {
                val fb = api.fetchFallbackApi(
                    settings.fallbackApiUrl,
                    settings.minWidth,
                    settings.minHeight
                )
                when (val r = downloadAndSet(
                    fb, settings, category,
                    fromWallhaven = false,
                    usedKeyword = null
                )) {
                    is ChangeResult.Success -> return r
                    is ChangeResult.Failure -> {
                        lastError = "兜底 API 失败: ${r.message}" +
                            (lastError?.let { "（此前：$it）" } ?: "")
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                lastError = "兜底 API 失败: $msg" +
                    (lastError?.let { "（此前：$it）" } ?: "")
            }
        } else if (!settings.networkFallbackEnabled) {
            if (wallhavenTried && !fromWallhaven) {
                lastError = if (wallhavenHttpError) {
                    lastError ?: "Wallhaven 连接失败"
                } else {
                    "Wallhaven 没找到符合要求的壁纸"
                }
            }
        } else if (settings.fallbackApiUrl.isBlank()) {
            lastError = (lastError ?: "Wallhaven 未成功") + "（未配置兜底 API URL）"
        }

        if (settings.localFallbackEnabled) {
            return applyLocalFallback(
                settings,
                reason = lastError ?: "上游均未成功"
            )
        }

        return ChangeResult.Failure(
            lastError
                ?: if (!settings.networkFallbackEnabled) {
                    "Wallhaven 没找到符合要求的壁纸"
                } else {
                    "未找到符合条件的壁纸"
                }
        )
    }

    private suspend fun applyLocalFallback(settings: AppSettings, reason: String): ChangeResult {
        if (!settings.localFallbackEnabled && !settings.forceLocalMode) {
            return ChangeResult.Failure(reason)
        }
        val item = localStore.pickRandom(settings)
            ?: return ChangeResult.Failure(
                "本地无可用图片。请将 jpg/png 放入：${localStore.resolveDir(settings).absolutePath}（原因：$reason）"
            )
        val file = File(item.pathUrl)
        if (!file.exists()) {
            return ChangeResult.Failure("本地文件不存在: ${file.path}")
        }
        val setOk = setter.setFromFile(file, settings.target)
        if (!setOk) {
            return ChangeResult.Failure("系统设置壁纸失败")
        }
        val size = file.length()
        dao.insert(
            WallpaperEntity(
                id = item.id,
                path = file.absolutePath,
                category = item.category,
                purity = item.purity,
                sourceUrl = file.absolutePath,
                setAt = System.currentTimeMillis(),
                width = item.width,
                height = item.height,
                fileSize = size,
                source = "local",
                keyword = ""
            )
        )
        settingsRepo.setLastChangeAt(System.currentTimeMillis())
        return ChangeResult.Success(
            item.copy(fileSize = size, category = "local←$reason"),
            file.absolutePath
        )
    }

    private suspend fun downloadAndSet(
        item: WallpaperItem,
        settings: AppSettings,
        category: String,
        fromWallhaven: Boolean,
        usedKeyword: String?
    ): ChangeResult {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val dest = File(dir, "${item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.jpg")

        val ok = if (item.source == "local") {
            true
        } else {
            val prefetched = item.prefetchedBytes
            if (prefetched != null && prefetched.isNotEmpty()) {
                runCatching {
                    dest.parentFile?.mkdirs()
                    dest.writeBytes(prefetched)
                    true
                }.getOrDefault(false)
            } else {
                api.downloadToFile(item.pathUrl, dest)
            }
        }
        val finalFile = if (item.source == "local") File(item.pathUrl) else dest
        if (!ok || !finalFile.exists() || finalFile.length() == 0L) {
            return ChangeResult.Failure("下载失败 (${item.source})")
        }

        val setOk = setter.setFromFile(finalFile, settings.target)
        if (!setOk) {
            return ChangeResult.Failure("系统设置壁纸失败（部分机型锁屏需额外权限）")
        }

        val fileSize = finalFile.length()
        val kwRecord = if (item.source == "wallhaven") (usedKeyword ?: "") else ""
        dao.insert(
            WallpaperEntity(
                id = item.id,
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

        // 跃迁：Wallhaven 成功时用标签覆盖跃迁列表（搜索常无 tags，需详情补拉）
        if (fromWallhaven && item.source == "wallhaven") {
            var tags = item.tags
            if (tags.isEmpty()) {
                tags = runCatching {
                    api.fetchWallpaperTags(item.id, settings.nextApiKey())
                }.getOrDefault(emptyList())
            }
            val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleaned.isNotEmpty()) {
                settingsRepo.setJumpKeywords(cleaned)
            }
        }

        val active = settings.activeKeywords()
        if (settings.useKeywords && active.isNotEmpty()) {
            if (settings.jumpModeEnabled && settings.jumpKeywords.isNotEmpty()) {
                settingsRepo.setJumpKeywordIndex(settings.jumpKeywordIndex + 1)
            } else {
                settingsRepo.setKeywordIndex(settings.keywordIndex + 1)
            }
        }

        if (item.source != "local") {
            trimCache(dir, keep = 30)
        }
        settingsRepo.setLastChangeAt(System.currentTimeMillis())
        return ChangeResult.Success(
            item.copy(fileSize = fileSize, tags = if (item.tags.isNotEmpty()) item.tags else emptyList()),
            finalFile.absolutePath
        )
    }

    private fun pickKeyword(settings: AppSettings): String? {
        if (!settings.useKeywords) return null
        val list = settings.activeKeywords()
        if (list.isEmpty()) return null
        val idx = settings.activeKeywordIndex().mod(list.size)
        return list[idx]
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
