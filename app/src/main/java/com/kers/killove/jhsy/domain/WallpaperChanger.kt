package com.kers.killove.jhsy.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.kers.killove.jhsy.data.local.LocalFallbackStore
import com.kers.killove.jhsy.data.local.WallpaperDao
import com.kers.killove.jhsy.data.local.WallpaperEntity
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.data.remote.WallhavenApi
import com.kers.killove.jhsy.data.local.PageCacheStore
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
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
    private val pageCache by lazy { PageCacheStore.from(context) }

    companion object {
        private const val CACHE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024 // 10GB
        private const val HISTORY_KEEP = 77
    }

    suspend fun changeOnce(forceIgnoreScreenOff: Boolean = false): ChangeResult {
        val settings = settingsRepo.settingsFlow.first()

        if (!forceIgnoreScreenOff && settings.skipWhenScreenOff && isScreenOff()) {
            return ChangeResult.Failure("息屏已跳过本次更换")
        }

        // 省电：未充电且电量低于阈值 → 休眠（手动立即更换仍执行 force 场景可跳过? 用户说休眠，立即更换应仍可用）
        if (!forceIgnoreScreenOff && shouldPowerSaveSleep(settings)) {
            return ChangeResult.Failure(
                "省电模式休眠中（电量 ${batteryPercent()}% < ${settings.powerSaveBatteryThreshold}%）"
            )
        }

        maybeClearCacheIfHuge()

        if (settings.forceLocalMode) {
            if (settings.isolateHomeLock && settings.target == WallpaperTarget.Both && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val home = applyLocalFallback(settings, "强制本地模式", WallpaperTarget.Home, emptySet())
                if (home is ChangeResult.Failure) return home
                val used = setOf((home as ChangeResult.Success).item.id)
                val lock = applyLocalFallback(settings, "强制本地模式", WallpaperTarget.Lock, used)
                return combineIsolate(home, lock)
            }
            return applyLocalFallback(settings, "强制本地模式", settings.target, emptySet())
        }

        // 桌面锁屏隔离：两次下载，各用不同关键词
        if (settings.isolateHomeLock &&
            settings.target == WallpaperTarget.Both &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        ) {
            val kwHome = pickKeyword(settings, offset = 0)
            val kwLock = pickKeyword(settings, offset = 1)
            val home = changeForTarget(settings, WallpaperTarget.Home, emptySet(), forceKeyword = kwHome)
            if (home is ChangeResult.Failure) return home
            val homeId = (home as ChangeResult.Success).item.id
            val lock = changeForTarget(settings, WallpaperTarget.Lock, setOf(homeId), forceKeyword = kwLock)
            // 隔离用了两个词，索引 +2
            advanceKeywordIndex(settings, steps = 2)
            return combineIsolate(home, lock)
        }

        return changeForTarget(settings, settings.target, emptySet(), forceKeyword = null)
    }

    private fun combineIsolate(home: ChangeResult, lock: ChangeResult): ChangeResult {
        return when {
            home is ChangeResult.Success && lock is ChangeResult.Success ->
                ChangeResult.Success(
                    lock.item,
                    lock.localPath,
                    detail = "桌面[${home.item.id}]词:${extractKw(home)} → 锁屏[${lock.item.id}]词:${extractKw(lock)}"
                )
            home is ChangeResult.Success && lock is ChangeResult.Failure ->
                ChangeResult.Success(home.item, home.localPath, detail = "桌面已设，锁屏失败：${lock.message}")
            else -> home
        }
    }

    private fun extractKw(r: ChangeResult.Success): String {
        val d = r.detail
        val m = Regex("""词:([^\s·]+)""").find(d)
        return m?.groupValues?.getOrNull(1) ?: "—"
    }

    private suspend fun changeForTarget(
        settings: AppSettings,
        target: WallpaperTarget,
        excludeIds: Set<String>,
        forceKeyword: String?,
        advanceKeyword: Boolean = true
    ): ChangeResult {
        val category = api.nextCategory(settings)
        val keyword = forceKeyword ?: pickKeyword(settings, offset = 0)
        val (dw, dh) = setter.screenSize()

        var candidates: List<WallpaperItem> = emptyList()
        var lastError: String? = null
        var fromWallhaven = false
        var wallhavenTried = false
        var wallhavenHttpError = false

        wallhavenTried = true
        try {
            val pageResult = api.searchRandomCachedPage(
                settings, category, keyword, dw, dh, pageCache
            )
            candidates = filterOrientation(pageResult.items, settings).filter { it.id !in excludeIds }
            if (candidates.isEmpty()) {
                val retry = api.searchRandomCachedPage(
                    settings, category, keyword, dw, dh, pageCache
                )
                candidates = filterOrientation(retry.items, settings).filter { it.id !in excludeIds }
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
                    val pageResult = api.searchRandomCachedPage(
                        settings.copy(apiKeyIndex = next), category, keyword, dw, dh, pageCache
                    )
                    candidates = filterOrientation(pageResult.items, settings).filter { it.id !in excludeIds }
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
            val item = pickCandidate(candidates, excludeIds)
            if (item != null) {
                when (val r = downloadAndSet(item, settings, category, fromWallhaven, keyword, target, advanceKeyword)) {
                    is ChangeResult.Success -> return r
                    is ChangeResult.Failure -> lastError = "Wallhaven 下载失败: ${r.message}"
                }
            }
            // 本页都用过：再随机一页
            try {
                val more = filterOrientation(
                    api.searchRandomCachedPage(settings, category, keyword, dw, dh, pageCache).items,
                    settings
                ).filter { it.id !in excludeIds }
                val alt = pickCandidate(more, excludeIds)
                if (alt != null) {
                    when (val r = downloadAndSet(alt, settings, category, true, keyword, target, advanceKeyword)) {
                        is ChangeResult.Success -> return r
                        is ChangeResult.Failure -> lastError = "Wallhaven 下载失败: ${r.message}"
                    }
                }
            } catch (_: Exception) {
            }
        }

        val fallbackUrls = settings.fallbackApiUrls()
        if (settings.networkFallbackEnabled && fallbackUrls.isNotEmpty()) {
            val errors = mutableListOf<String>()
            for ((i, url) in fallbackUrls.withIndex()) {
                try {
                    val fb = api.fetchFallbackApi(url, maxOf(settings.minWidth, dw), maxOf(settings.minHeight, dh))
                    val unique = fb.copy(id = fb.id + "_${target.name}_${System.nanoTime() % 100000}")
                    when (val r = downloadAndSet(unique, settings, category, false, null, target, advanceKeyword = false)) {
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

    private suspend fun pickCandidate(
        candidates: List<WallpaperItem>,
        excludeIds: Set<String>
    ): WallpaperItem? {
        if (candidates.isEmpty()) return null
        val pool = candidates.filter { it.id !in excludeIds }.ifEmpty { candidates }
        val unseen = mutableListOf<WallpaperItem>()
        val seen = mutableListOf<WallpaperItem>()
        for (c in pool.shuffled()) {
            val used = runCatching {
                dao.existsBaseOrUrl(c.id, c.pathUrl)
            }.getOrDefault(false)
            if (used) seen += c else unseen += c
        }
        // 优先未用过；全用过则打乱已用过的再抽，避免永远 1～4 张死循环
        return (unseen.ifEmpty { seen }).randomOrNull()
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
        val files = localStore.listImages(settings).filter { f ->
            val id = "local_${f.name}"
            id !in excludeIds
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
                id = "${item.id}_${target.name}_${System.currentTimeMillis() % 100000}",
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
        dao.trimToKeep(HISTORY_KEEP)
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
        target: WallpaperTarget,
        advanceKeyword: Boolean
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
        dao.trimToKeep(HISTORY_KEEP)

        if (settings.categoryMode == CategoryMode.Rotate) {
            settingsRepo.setLastCategory(category)
        }

        // 跃迁：用本图标签覆盖跃迁列表，但排除「本次搜索用过的关键词」，避免原地打转
        if (fromWallhaven && item.source == "wallhaven") {
            var tags = item.tags
            if (tags.isEmpty()) {
                tags = runCatching {
                    api.fetchWallpaperTags(item.id, settings.nextApiKey())
                }.getOrDefault(emptyList())
            }
            val cleaned = filterJumpTags(tags, usedKeyword)
            if (cleaned.isNotEmpty()) {
                settingsRepo.setJumpKeywords(cleaned)
            }
            // 若过滤后为空：不覆盖旧跃迁列表，避免被清空后无法继续跃迁
        }

        // 非隔离路径才在这里 +1；隔离在外层 +2
        if (advanceKeyword && settings.useKeywords && settings.activeKeywords().isNotEmpty()) {
            advanceKeywordIndex(settings, steps = 1)
        }

        if (item.source != "local") trimCache(dir, keep = 40)
        settingsRepo.setLastChangeAt(System.currentTimeMillis())
        return ChangeResult.Success(
            item.copy(fileSize = fileSize),
            finalFile.absolutePath,
            detail = "${target.label}" + if (kwRecord.isNotBlank()) " · 词:$kwRecord" else ""
        )
    }


    /**
     * 跃迁标签清洗：去空、去重，并忽略与本次搜索词相同的标签（大小写不敏感）。
     * 多词搜索（如 "red car"）时，整句与分词都会排除。
     */
    private fun filterJumpTags(tags: List<String>, usedKeyword: String?): List<String> {
        val exclude = mutableSetOf<String>()
        val raw = usedKeyword?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            exclude += raw.lowercase()
            raw.split(Regex("\\s+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                .forEach { exclude += it }
        }
        return tags.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { it.lowercase() !in exclude }
            .distinct()
            .toList()
    }

    private fun pickKeyword(settings: AppSettings, offset: Int): String? {
        if (!settings.useKeywords) return null
        val list = settings.activeKeywords()
        if (list.isEmpty()) return null
        val base = settings.activeKeywordIndex()
        return list[(base + offset).mod(list.size)]
    }

    private suspend fun advanceKeywordIndex(settings: AppSettings, steps: Int) {
        if (!settings.useKeywords || settings.activeKeywords().isEmpty()) return
        if (settings.jumpModeEnabled && settings.jumpKeywords.isNotEmpty()) {
            settingsRepo.setJumpKeywordIndex(settings.jumpKeywordIndex + steps)
        } else {
            settingsRepo.setKeywordIndex(settings.keywordIndex + steps)
        }
    }

    private fun shouldPowerSaveSleep(settings: AppSettings): Boolean {
        if (!settings.powerSaveEnabled) return false
        if (isCharging()) return false
        val pct = batteryPercent() ?: return false
        return pct < settings.powerSaveBatteryThreshold.coerceIn(5, 50)
    }

    private fun isCharging(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        ) return true
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return plugged != 0
    }

    private fun batteryPercent(): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val fromBm = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (fromBm != null && fromBm in 0..100) return fromBm
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    private fun isScreenOff(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isInteractive
    }

    private fun trimCache(dir: File, keep: Int) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { it.delete() }
    }

    /** 应用数据目录超过 10GB 时清空 wallpapers 缓存 */
    private fun maybeClearCacheIfHuge() {
        try {
            val root = context.filesDir ?: return
            val total = dirSize(root)
            if (total < CACHE_LIMIT_BYTES) return
            val wp = File(root, "wallpapers")
            if (wp.isDirectory) {
                wp.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) sum += f.length()
        }
        return sum
    }
}
