package com.kers701.wallpaperc.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

        // 强制本地 或 无网 → 本地兜底
        if (settings.forceLocalMode || !isNetworkAvailable()) {
            return applyLocalFallback(
                settings,
                reason = if (settings.forceLocalMode) "强制本地模式" else "无网络"
            )
        }

        val category = api.nextCategory(settings)
        val keyword = pickKeyword(settings)

        var candidates: List<WallpaperItem> = emptyList()
        var lastError: String? = null
        var fromWallhaven = false

        try {
            candidates = api.search(settings, category, keyword)
            if (candidates.isEmpty()) {
                candidates = api.search(settings, category, keyword, page = 2)
            }
            fromWallhaven = candidates.isNotEmpty()
        } catch (e: Exception) {
            lastError = e.message
            val keys = settings.apiKeys.filter { it.isNotBlank() }
            if (keys.size > 1) {
                val next = (settings.apiKeyIndex + 1) % keys.size
                settingsRepo.setApiKeyIndex(next)
                try {
                    val retrySettings = settings.copy(apiKeyIndex = next)
                    candidates = api.search(retrySettings, category, keyword)
                    fromWallhaven = candidates.isNotEmpty()
                } catch (e2: Exception) {
                    lastError = e2.message
                }
            }
        }

        if (candidates.isNotEmpty()) {
            val item = candidates.firstOrNull { !dao.exists(it.id) } ?: candidates.first()
            return downloadAndSet(item, settings, category, fromWallhaven = fromWallhaven)
        }

        // 网络兜底 API
        if (settings.networkFallbackEnabled && settings.fallbackApiUrl.isNotBlank()) {
            try {
                val fb = api.fetchFallbackApi(
                    settings.fallbackApiUrl,
                    settings.minWidth,
                    settings.minHeight
                )
                return downloadAndSet(fb, settings, category, fromWallhaven = false)
            } catch (e: Exception) {
                lastError = "网络兜底失败: ${e.message}"
            }
        }

        // 本地兜底
        if (settings.localFallbackEnabled) {
            return applyLocalFallback(settings, reason = lastError ?: "Wallhaven 无结果")
        }

        return ChangeResult.Failure(lastError ?: "未找到符合条件的壁纸")
    }

    private suspend fun applyLocalFallback(settings: AppSettings, reason: String): ChangeResult {
        if (!settings.localFallbackEnabled && !settings.forceLocalMode) {
            return ChangeResult.Failure("本地兜底未开启（$reason）")
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
                source = "local"
            )
        )
        settingsRepo.setLastChangeAt(System.currentTimeMillis())
        return ChangeResult.Success(item.copy(fileSize = size), file.absolutePath)
    }

    private suspend fun downloadAndSet(
        item: WallpaperItem,
        settings: AppSettings,
        category: String,
        fromWallhaven: Boolean
    ): ChangeResult {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val dest = File(dir, "${item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.jpg")

        val ok = if (item.source == "local") {
            true
        } else {
            api.downloadToFile(item.pathUrl, dest)
        }
        val finalFile = if (item.source == "local") File(item.pathUrl) else dest
        if (!ok || !finalFile.exists()) {
            if (settings.localFallbackEnabled) {
                return applyLocalFallback(settings, reason = "下载失败")
            }
            return ChangeResult.Failure("下载失败")
        }

        val setOk = setter.setFromFile(finalFile, settings.target)
        if (!setOk) {
            return ChangeResult.Failure("系统设置壁纸失败（部分机型锁屏需额外权限）")
        }

        val fileSize = finalFile.length()
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
                source = item.source
            )
        )

        if (settings.categoryMode == CategoryMode.Rotate) {
            settingsRepo.setLastCategory(category)
        }

        // 跃迁：仅 Wallhaven 成功且非兜底时，用标签覆盖跃迁列表
        if (fromWallhaven && item.source == "wallhaven" && item.tags.isNotEmpty()) {
            val cleaned = item.tags
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (cleaned.isNotEmpty()) {
                settingsRepo.setJumpKeywords(cleaned)
            }
        }

        // 推进关键词下标
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
        return ChangeResult.Success(item.copy(fileSize = fileSize), finalFile.absolutePath)
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

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun trimCache(dir: File, keep: Int) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { it.delete() }
    }
}
