package com.kers.killove.jhsy.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.kers.killove.jhsy.data.local.LocalFallbackStore
import com.kers.killove.jhsy.data.local.NextWallpaperStore
import com.kers.killove.jhsy.data.local.OverviewCacheStore
import com.kers.killove.jhsy.data.local.WallpaperDao
import com.kers.killove.jhsy.data.local.WallpaperEntity
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.data.remote.WallhavenApi
import com.kers.killove.jhsy.data.local.PageCacheStore
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
import com.kers.killove.jhsy.util.ForegroundAppHelper
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import com.kers.killove.jhsy.util.DataSaverBudget
import com.kers.killove.jhsy.util.RunLog
import com.kers.killove.jhsy.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class WallpaperChanger(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val api: WallhavenApi,
    private val setter: SystemWallpaperSetter,
    private val dao: WallpaperDao,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val localStore: LocalFallbackStore = LocalFallbackStore(context)
) {
    private val pageCache by lazy { PageCacheStore.from(context) }
    private val nextStore by lazy { NextWallpaperStore(context) }
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentTrigger: TriggerType = TriggerType.Auto

    companion object {
        private const val CACHE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024 // 10GB
        private const val HISTORY_KEEP = 77
    }

    /** FGS 周期调用：处理预下载 5 分钟重试 */
    suspend fun tickPrefetchMaintenance() {
        val settings = settingsRepo.settingsFlow.first()
        processPrefetchRetries(settings)
    }

    /**
     * @param liveDownloadOnly 手动立即更换：不用预下载图、成功后不预取下一张、不触发定时相关副作用
     */
    suspend fun changeOnce(
        forceIgnoreScreenOff: Boolean = false,
        triggerType: TriggerType = TriggerType.Auto,
        liveDownloadOnly: Boolean = false
    ): ChangeResult {
        currentTrigger = triggerType
        var settings = settingsRepo.settingsFlow.first()
        RunLog.i(context, "changeOnce start trigger=${triggerType.code} force=$forceIgnoreScreenOff")

        // 定位避让：进入/离开避让区时调整纯度与强制本地
        if (settings.locationAvoidEnabled && settings.avoidanceLocations().isNotEmpty()) {
            settings = applyLocationAvoidance(settings)
        }

        // 通知「健康/心跳」运行模式：覆盖用户纯度配置（每次更换重新随机）
        settings = applyPurityRuntimeMode(settings)

        if (!forceIgnoreScreenOff && settings.skipWhenScreenOff && isScreenOff()) {
            return ChangeResult.Failure("息屏已跳过本次更换")
        }

        // 省电：未充电且电量低于阈值 → 休眠（手动立即更换仍执行 force 场景可跳过? 用户说休眠，立即更换应仍可用）
        if (!forceIgnoreScreenOff && shouldPowerSaveSleep(settings)) {
            return ChangeResult.Failure(
                "省电模式休眠中（电量 ${batteryPercent()}% < ${settings.powerSaveBatteryThreshold}%）"
            )
        }


        // 前台黑名单应用：休眠不换（以文件桥+设置合并后的列表为准，避免跨进程勾选状态不一致）
        if (!forceIgnoreScreenOff) {
            val bl = ProcessBridgePrefs.mergeBlacklist(context, settings.blacklistPackages)
            if (bl.isNotEmpty() && ForegroundAppHelper.isBlacklistedForeground(context, bl)) {
                val fg = ForegroundAppHelper.currentForegroundPackage(context) ?: "?"
                return ChangeResult.Failure("黑名单应用在前台，休眠（$fg）")
            }
        }

        // 省流量：当日缓存写入 ≥20GB 则今日自动停换（手动仍可 force）
        if (!forceIgnoreScreenOff && settings.dataSaverEnabled &&
            DataSaverBudget.shouldStopToday(context, true)
        ) {
            RunLog.i(context, "data_saver stop today bytes=${DataSaverBudget.todayBytes(context)}")
            return ChangeResult.Failure(
                String.format(
                    java.util.Locale.CHINA,
                    "省流量：今日缓存已约 %.1f GB≥20，今日不再自动更换",
                    DataSaverBudget.todayGb(context)
                )
            )
        }

        maybeClearCacheIfHuge()

        if (settings.forceLocalMode) {
            if (settings.isolateHomeLock && settings.target == WallpaperTarget.Both && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val home = applyLocalFallback(settings, "强制本地模式", WallpaperTarget.Home, emptySet(), countChange = false)
                if (home is ChangeResult.Failure) return home
                val used = setOf((home as ChangeResult.Success).item.id)
                val lock = applyLocalFallback(settings, "强制本地模式", WallpaperTarget.Lock, used, countChange = false)
                val combined = combineIsolate(home, lock)
                if (combined is ChangeResult.Success) settingsRepo.incrementChangeCount()
                return combined
            }
            return applyLocalFallback(settings, "强制本地模式", settings.target, emptySet())
        }

        // 自动路径才做预下载重试；手动 live 不碰预取队列
        if (!liveDownloadOnly) {
            processPrefetchRetries(settings)
        }

        // 桌面锁屏隔离
        if (settings.isolateHomeLock &&
            settings.target == WallpaperTarget.Both &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        ) {
            val kwHome = pickKeyword(settings, offset = 0)
            val kwLock = pickKeyword(settings, offset = 1)
            val home = changeForTarget(
                settings, WallpaperTarget.Home, emptySet(), forceKeyword = kwHome,
                skipPrefetchUse = liveDownloadOnly, countChange = false,
                touchScheduleClock = !liveDownloadOnly
            )
            if (home is ChangeResult.Failure) return home
            val homeId = (home as ChangeResult.Success).item.id
            val lock = changeForTarget(
                settings, WallpaperTarget.Lock, setOf(homeId), forceKeyword = kwLock,
                skipPrefetchUse = liveDownloadOnly, countChange = false,
                touchScheduleClock = !liveDownloadOnly
            )
            // 隔离用了两个词，索引 +2；次数只 +1（桌面+锁屏算一次）
            advanceKeywordIndex(settings, steps = 2)
            val combined = combineIsolate(home, lock)
            if (combined is ChangeResult.Success) {
                settingsRepo.incrementChangeCount()
                if (!liveDownloadOnly) {
                    schedulePrefetch(
                        listOf(WallpaperTarget.Home, WallpaperTarget.Lock),
                        setOf(homeId, (lock as? ChangeResult.Success)?.item?.id ?: "")
                    )
                }
            }
            return combined
        }

        val single = changeForTarget(
            settings, settings.target, emptySet(), forceKeyword = null,
            skipPrefetchUse = liveDownloadOnly,
            touchScheduleClock = !liveDownloadOnly
        )
        if (!liveDownloadOnly && single is ChangeResult.Success) {
            schedulePrefetch(listOf(settings.target), setOf(single.item.id))
        }
        return single
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
        advanceKeyword: Boolean = true,
        skipPrefetchUse: Boolean = false,
        countChange: Boolean = true,
        /** false=手动立即换：不写 lastChangeAt，不参与定时 */
        touchScheduleClock: Boolean = true
    ): ChangeResult {
        // 手动立即更换：强制当场下载，不消费预下载槽
        if (!skipPrefetchUse) {
            val ready = nextStore.takeReady(target)
            if (ready != null) {
                onProgress(0.2f, "使用预下载壁纸…")
                when (val r = applyReadySlot(ready, settings, target, advanceKeyword, countChange, touchScheduleClock)) {
                    is ChangeResult.Success -> {
                        onProgress(1f, "预下载已应用")
                        return r
                    }
                    is ChangeResult.Failure -> {
                        nextStore.clear(target)
                    }
                }
            }
        }

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
                when (val r = downloadAndSet(item, settings, category, fromWallhaven, keyword, target, advanceKeyword, countChange, touchScheduleClock)) {
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
                    when (val r = downloadAndSet(alt, settings, category, true, keyword, target, advanceKeyword, countChange, touchScheduleClock)) {
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
                    when (val r = downloadAndSet(unique, settings, category, false, null, target, advanceKeyword = false, countChange = countChange, touchScheduleClock = touchScheduleClock)) {
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
        excludeIds: Set<String>,
        countChange: Boolean = true,
        touchScheduleClock: Boolean = true
    ): ChangeResult {
        if (!settings.localFallbackEnabled && !settings.forceLocalMode) {
            return ChangeResult.Failure(reason)
        }
        val files = localStore.listFallbackCandidates(settings).filter { f ->
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
                keyword = "",
                triggerType = currentTrigger.code
            )
        )
        dao.trimToKeep(HISTORY_KEEP)
        OverviewCacheStore.update(context, target, file)
        if (touchScheduleClock) {
            settingsRepo.setLastChangeAt(System.currentTimeMillis())
            ProcessBridgePrefs.setLastChangeAt(context, System.currentTimeMillis())
        }
        if (countChange) settingsRepo.incrementChangeCount()
        if (fileSize > 0L) {
            DataSaverBudget.addBytes(context, fileSize)
            RunLog.i(context, "wallpaper set id=${slot.id} target=$target size=$fileSize today=${DataSaverBudget.todayBytes(context)}")
        }
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
        advanceKeyword: Boolean,
        countChange: Boolean = true,
        touchScheduleClock: Boolean = true
    ): ChangeResult {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val targetSuffix = when (target) {
            WallpaperTarget.Home -> "home"
            WallpaperTarget.Lock -> "lock"
            WallpaperTarget.Both -> "both"
        }
        val dest = File(dir, "${item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_$targetSuffix.jpg")

        val ok = if (item.source == "local") {
            onProgress(1f, "本地图")
            true
        } else {
            val prefetched = item.prefetchedBytes
            if (prefetched != null && prefetched.isNotEmpty()) {
                onProgress(0.5f, "写入缓存…")
                runCatching {
                    dest.parentFile?.mkdirs()
                    dest.writeBytes(prefetched)
                    true
                }.getOrDefault(false)
            } else {
                onProgress(0f, "下载中…")
                api.downloadToFile(item.pathUrl, dest) { read, total ->
                    val frac = if (total > 0L) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                    val label = if (total > 0L) {
                        "下载 ${read / 1024}KB / ${total / 1024}KB"
                    } else {
                        "下载 ${read / 1024}KB"
                    }
                    onProgress(frac, label)
                }
            }
        }
        val finalFile = if (item.source == "local") File(item.pathUrl) else dest
        if (!ok || !finalFile.exists() || finalFile.length() == 0L) {
            return ChangeResult.Failure("下载失败 (${item.source})")
        }

        onProgress(0.95f, "正在设置壁纸…")
        if (!setter.setFromFile(finalFile, target, settings.fitMode)) {
            return ChangeResult.Failure("系统设置壁纸失败（部分机型锁屏需额外权限）")
        }
        onProgress(1f, "完成")

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
                keyword = kwRecord,
                triggerType = currentTrigger.code
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
        OverviewCacheStore.update(context, target, finalFile)
        if (touchScheduleClock) {
            settingsRepo.setLastChangeAt(System.currentTimeMillis())
            ProcessBridgePrefs.setLastChangeAt(context, System.currentTimeMillis())
        }
        if (countChange) settingsRepo.incrementChangeCount()
        if (fileSize > 0L) {
            DataSaverBudget.addBytes(context, fileSize)
            RunLog.i(context, "wallpaper set id=${slot.id} target=$target size=$fileSize today=${DataSaverBudget.todayBytes(context)}")
        }
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

    // ─── 预下载：更换后异步拉下一张（仅 Wallhaven + 兜底 API） ───

    private fun schedulePrefetch(targets: List<WallpaperTarget>, excludeIds: Set<String>) {
        prefetchScope.launch {
            runCatching {
                val settings = settingsRepo.settingsFlow.first()
                if (settings.forceLocalMode) return@launch
                for ((i, target) in targets.withIndex()) {
                    if (nextStore.hasReady(target)) continue
                    if (nextStore.retriesExhausted(target) && nextStore.failCount(target) >= 2) {
                        // 上次预取已失败两次，等下次更换触发时再下；此处仍尝试一次新周期
                        nextStore.clearFail(target)
                    }
                    val kw = pickKeyword(settings, offset = i)
                    prefetchDownload(settings, target, excludeIds, kw)
                }
            }
        }
    }

    private suspend fun processPrefetchRetries(settings: AppSettings) {
        if (settings.forceLocalMode) return
        val targets = if (settings.isolateHomeLock && settings.target == WallpaperTarget.Both) {
            listOf(WallpaperTarget.Home, WallpaperTarget.Lock)
        } else {
            listOf(settings.target)
        }
        for ((i, target) in targets.withIndex()) {
            if (nextStore.shouldRetryOnce(target)) {
                onProgress(0f, "预下载重试中…")
                val kw = pickKeyword(settings, offset = i)
                prefetchDownload(settings, target, emptySet(), kw)
            }
        }
    }

    /**
     * 搜索并下载到 next_queue，不设置壁纸。
     * 失败：记 1 次，5 分钟后由 processPrefetchRetries 再试；再失败不再自动重试。
     */
    private suspend fun prefetchDownload(
        settings: AppSettings,
        target: WallpaperTarget,
        excludeIds: Set<String>,
        forceKeyword: String?
    ) {
        val category = api.nextCategory(settings)
        val keyword = forceKeyword ?: pickKeyword(settings, offset = 0)
        val (dw, dh) = setter.screenSize()
        var lastError: String? = null

        // Wallhaven
        try {
            val pageResult = api.searchRandomCachedPage(settings, category, keyword, dw, dh, pageCache)
            var candidates = filterOrientation(pageResult.items, settings).filter { it.id !in excludeIds }
            if (candidates.isEmpty()) {
                candidates = filterOrientation(
                    api.searchRandomCachedPage(settings, category, keyword, dw, dh, pageCache).items,
                    settings
                ).filter { it.id !in excludeIds }
            }
            val item = pickCandidate(candidates, excludeIds)
            if (item != null) {
                if (savePrefetchFile(item, target, keyword, category)) return
                lastError = "预下载写入失败"
            } else {
                lastError = "Wallhaven 无候选"
            }
        } catch (e: Exception) {
            lastError = "Wallhaven: ${e.message}"
        }

        // 兜底 API
        if (settings.networkFallbackEnabled) {
            for (url in settings.fallbackApiUrls()) {
                try {
                    val fb = api.fetchFallbackApi(url, maxOf(settings.minWidth, dw), maxOf(settings.minHeight, dh))
                    val unique = fb.copy(id = fb.id + "_pref_${target.name}_${System.nanoTime() % 100000}")
                    if (savePrefetchFile(unique, target, null, category)) return
                } catch (_: Exception) {
                }
            }
        }

        nextStore.markFail(target)
        // lastError 仅用于调试，不打断主流程
        lastError?.let { /* no-op */ }
    }

    private suspend fun savePrefetchFile(
        item: WallpaperItem,
        target: WallpaperTarget,
        keyword: String?,
        category: String
    ): Boolean {
        val dest = nextStore.fileFor(target)
        dest.parentFile?.mkdirs()
        val ok = if (item.source == "local") {
            false
        } else {
            val prefetched = item.prefetchedBytes
            if (prefetched != null && prefetched.isNotEmpty()) {
                runCatching {
                    dest.writeBytes(prefetched)
                    true
                }.getOrDefault(false)
            } else {
                api.downloadToFile(item.pathUrl, dest) { read, total ->
                    val frac = if (total > 0L) (read.toFloat() / total).coerceIn(0f, 0.99f) else 0f
                    onProgress(frac, "预下载 ${read / 1024}KB")
                }
            }
        }
        if (!ok || !dest.exists() || dest.length() < 32L) {
            runCatching { dest.delete() }
            return false
        }
        nextStore.put(
            NextWallpaperStore.Slot(
                target = target.name,
                id = item.id,
                path = dest.absolutePath,
                sourceUrl = item.pathUrl,
                keyword = keyword.orEmpty(),
                category = item.category.ifBlank { category },
                purity = item.purity,
                width = item.width,
                height = item.height,
                source = item.source,
                readyAt = System.currentTimeMillis()
            )
        )
        onProgress(1f, "下一张已预下载")
        return true
    }

    private suspend fun applyReadySlot(
        slot: NextWallpaperStore.Slot,
        settings: AppSettings,
        target: WallpaperTarget,
        advanceKeyword: Boolean,
        countChange: Boolean = true,
        touchScheduleClock: Boolean = true
    ): ChangeResult {
        val file = slot.file()
        if (!file.exists() || file.length() < 32L) {
            return ChangeResult.Failure("预下载文件丢失")
        }
        onProgress(0.9f, "正在设置预下载壁纸…")
        if (!setter.setFromFile(file, target, settings.fitMode)) {
            return ChangeResult.Failure("系统设置壁纸失败（预下载）")
        }
        // 移入正式缓存目录，避免 next_queue 被下次预取覆盖时丢历史
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val targetSuffix = when (target) {
            WallpaperTarget.Home -> "home"
            WallpaperTarget.Lock -> "lock"
            WallpaperTarget.Both -> "both"
        }
        val finalFile = File(dir, "${slot.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_$targetSuffix.jpg")
        runCatching {
            if (file.absolutePath != finalFile.absolutePath) {
                file.copyTo(finalFile, overwrite = true)
            }
        }
        val useFile = if (finalFile.exists()) finalFile else file
        val fileSize = useFile.length()
        dao.insert(
            WallpaperEntity(
                id = "${slot.id}_${target.name}",
                path = useFile.absolutePath,
                category = slot.category,
                purity = slot.purity,
                sourceUrl = slot.sourceUrl,
                setAt = System.currentTimeMillis(),
                width = slot.width,
                height = slot.height,
                fileSize = fileSize,
                source = slot.source,
                keyword = slot.keyword,
                triggerType = currentTrigger.code
            )
        )
        dao.trimToKeep(HISTORY_KEEP)
        if (advanceKeyword && settings.useKeywords && settings.activeKeywords().isNotEmpty()) {
            advanceKeywordIndex(settings, steps = 1)
        }
        OverviewCacheStore.update(context, target, useFile)
        if (touchScheduleClock) {
            settingsRepo.setLastChangeAt(System.currentTimeMillis())
            ProcessBridgePrefs.setLastChangeAt(context, System.currentTimeMillis())
        }
        if (countChange) settingsRepo.incrementChangeCount()
        if (fileSize > 0L) {
            DataSaverBudget.addBytes(context, fileSize)
            RunLog.i(context, "wallpaper set id=${slot.id} target=$target size=$fileSize today=${DataSaverBudget.todayBytes(context)}")
        }
        val item = WallpaperItem(
            id = slot.id,
            pathUrl = slot.sourceUrl,
            thumbsUrl = null,
            width = slot.width,
            height = slot.height,
            purity = slot.purity,
            category = slot.category,
            source = slot.source,
            fileSize = fileSize
        )
        return ChangeResult.Success(
            item,
            useFile.absolutePath,
            detail = "${target.label}（预下载）" + if (slot.keyword.isNotBlank()) " · 词:${slot.keyword}" else ""
        )
    }

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



    /**
     * 通知栏纯度运行模式（跨进程）：
     * - health 健康：R8 / R13 / 仅 Sketchy 三选一随机
     * - heartbeat 心跳：除 R8 外所有纯度随机
     * - normal：遵循用户设置（本函数不改）
     */
    private fun applyPurityRuntimeMode(settings: AppSettings): AppSettings {
        return when (ProcessBridgePrefs.purityMode(context)) {
            ProcessBridgePrefs.MODE_HEALTH -> {
                val p = listOf(Purity.R8, Purity.R13, Purity.Only13).random()
                settings.copy(purity = p)
            }
            ProcessBridgePrefs.MODE_HEARTBEAT -> {
                val p = listOf(
                    Purity.R13, Purity.R18, Purity.Only13, Purity.Only18, Purity.R18D
                ).random()
                settings.copy(purity = p)
            }
            else -> settings
        }
    }

    /** 定位避让：进入避让区启用绿色模式（R13/仅Sketchy随机）与极限本地；离开后恢复 */
    private suspend fun applyLocationAvoidance(settings: AppSettings): AppSettings {
        val (inZone, _) = LocationHelper.isInAvoidZone(context, settings.avoidanceLocations(), settings.locationAvoidRadiusMeters.toDouble())
        if (inZone) {
            if (!settings.locationInAvoidZone) {
                var next = settings.copy(
                    locationInAvoidZone = true,
                    locationSavedPurity = settings.purity.code,
                    locationSavedForceLocal = settings.forceLocalMode
                )
                if (settings.locationFallbackEnabled) {
                    // 绿色模式：区内在 R13 与「仅 Sketchy」之间随机，不再固定锁 R13
                    val green = listOf(Purity.R13, Purity.Only13).random()
                    next = next.copy(purity = green)
                }
                if (settings.locationExtremeFallbackEnabled) {
                    next = next.copy(forceLocalMode = true)
                }
                settingsRepo.save(next)
                return next
            }
            // 已在区内：每次更换再次随机绿色纯度（R13 / 仅 Sketchy）
            if (settings.locationFallbackEnabled) {
                val green = listOf(Purity.R13, Purity.Only13).random()
                if (green != settings.purity) {
                    val next = settings.copy(purity = green)
                    settingsRepo.save(next)
                    return next
                }
            }
            return settings
        } else {
            if (settings.locationInAvoidZone) {
                val restoredPurity = if (settings.locationSavedPurity.isNotBlank()) {
                    Purity.fromCode(settings.locationSavedPurity)
                } else {
                    settings.purity
                }
                val next = settings.copy(
                    locationInAvoidZone = false,
                    purity = restoredPurity,
                    forceLocalMode = settings.locationSavedForceLocal,
                    locationSavedPurity = "",
                    locationSavedForceLocal = false
                )
                settingsRepo.save(next)
                return next
            }
            return settings
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
