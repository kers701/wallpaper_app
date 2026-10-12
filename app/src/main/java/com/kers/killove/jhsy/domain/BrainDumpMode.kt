package com.kers.killove.jhsy.domain

import org.json.JSONObject

/**
 * 抛弃大脑模式：临时劫持部分设置；关闭后从快照还原。
 * 未列入的字段一律不改动。
 */
object BrainDumpMode {

    // 不可用 const：trimIndent() 非编译期常量
    val FALLBACK_API: String = """
https://t.alcy.cc/mp
https://api.lolicon.app/setu/v2?aspectRatio=gt2.0lt2.2&r18=2
https://api.r10086.com/樱道随机图片api接口.php?图片系列=Fate
https://api.r10086.com/樱道随机图片api接口.php?图片系列=原神
https://api.r10086.com/樱道随机图片api接口.php?图片系列=罪恶王冠
https://api.r10086.com/樱道随机图片api接口.php?图片系列=动漫综合1
""".trimIndent()

    const val BG_API = "https://t.alcy.cc/mp"

    /** 开启时强制写入的字段（在已有 settings 上 copy） */
    fun apply(s: AppSettings): AppSettings = s.copy(
        intervalMinutes = 15,
        showWallpaperPreview = true,
        sorting = SearchSorting.Random,
        cacheRetention = CacheRetention.Day1,
        jumpModeEnabled = true,
        annihilationModeEnabled = true,
        illusionModeEnabled = true,
        networkFallbackEnabled = true,
        fallbackApiUrl = FALLBACK_API,
        localFallbackEnabled = true,
        forceLocalMode = false,
        localFallbackUseCache = true,
        localFallbackCacheSkipNewest = 7,
        proxyEnabled = false,
        accelModeEnabled = false,
        superProxyEnabled = false,
        destinyEnabled = true,
        locationAvoidEnabled = false,
        bgApiUrl = BG_API,
        pastLifeEnabled = false,
        pastLifeDeepExplore = false,
        pastLifeIsolateSplit = false,
        translateAiMode = false,
        translateProvider = TranslateProvider.Off,
        brainDumpMode = true
    )

    /** 仅备份会被劫持的字段 */
    fun snapshotJson(s: AppSettings): String {
        val o = JSONObject()
        o.put("intervalMinutes", s.intervalMinutes)
        o.put("showWallpaperPreview", s.showWallpaperPreview)
        o.put("sorting", s.sorting.code)
        o.put("cacheRetention", s.cacheRetention.code)
        o.put("jumpModeEnabled", s.jumpModeEnabled)
        o.put("annihilationModeEnabled", s.annihilationModeEnabled)
        o.put("illusionModeEnabled", s.illusionModeEnabled)
        o.put("networkFallbackEnabled", s.networkFallbackEnabled)
        o.put("fallbackApiUrl", s.fallbackApiUrl)
        o.put("localFallbackEnabled", s.localFallbackEnabled)
        o.put("forceLocalMode", s.forceLocalMode)
        o.put("localFallbackUseCache", s.localFallbackUseCache)
        o.put("localFallbackCacheSkipNewest", s.localFallbackCacheSkipNewest)
        o.put("proxyEnabled", s.proxyEnabled)
        o.put("accelModeEnabled", s.accelModeEnabled)
        o.put("superProxyEnabled", s.superProxyEnabled)
        o.put("destinyEnabled", s.destinyEnabled)
        o.put("locationAvoidEnabled", s.locationAvoidEnabled)
        o.put("bgApiUrl", s.bgApiUrl)
        o.put("pastLifeEnabled", s.pastLifeEnabled)
        o.put("pastLifeDeepExplore", s.pastLifeDeepExplore)
        o.put("pastLifeIsolateSplit", s.pastLifeIsolateSplit)
        o.put("translateAiMode", s.translateAiMode)
        o.put("translateProvider", s.translateProvider.name)
        return o.toString()
    }

    fun restore(current: AppSettings, json: String): AppSettings {
        return try {
            val o = JSONObject(json)
            current.copy(
                intervalMinutes = o.optInt("intervalMinutes", current.intervalMinutes).coerceIn(5, 180),
                showWallpaperPreview = o.optBoolean("showWallpaperPreview", current.showWallpaperPreview),
                sorting = SearchSorting.fromCode(o.optString("sorting", current.sorting.code)),
                cacheRetention = CacheRetention.fromCode(o.optString("cacheRetention", current.cacheRetention.code)),
                jumpModeEnabled = o.optBoolean("jumpModeEnabled", current.jumpModeEnabled),
                annihilationModeEnabled = o.optBoolean("annihilationModeEnabled", current.annihilationModeEnabled),
                illusionModeEnabled = o.optBoolean("illusionModeEnabled", current.illusionModeEnabled),
                networkFallbackEnabled = o.optBoolean("networkFallbackEnabled", current.networkFallbackEnabled),
                fallbackApiUrl = o.optString("fallbackApiUrl", current.fallbackApiUrl),
                localFallbackEnabled = o.optBoolean("localFallbackEnabled", current.localFallbackEnabled),
                forceLocalMode = o.optBoolean("forceLocalMode", current.forceLocalMode),
                localFallbackUseCache = o.optBoolean("localFallbackUseCache", current.localFallbackUseCache),
                localFallbackCacheSkipNewest = o.optInt("localFallbackCacheSkipNewest", current.localFallbackCacheSkipNewest).coerceIn(0, 50),
                proxyEnabled = o.optBoolean("proxyEnabled", current.proxyEnabled),
                accelModeEnabled = o.optBoolean("accelModeEnabled", current.accelModeEnabled),
                superProxyEnabled = o.optBoolean("superProxyEnabled", current.superProxyEnabled),
                destinyEnabled = o.optBoolean("destinyEnabled", current.destinyEnabled),
                locationAvoidEnabled = o.optBoolean("locationAvoidEnabled", current.locationAvoidEnabled),
                bgApiUrl = o.optString("bgApiUrl", current.bgApiUrl),
                pastLifeEnabled = o.optBoolean("pastLifeEnabled", current.pastLifeEnabled),
                pastLifeDeepExplore = o.optBoolean("pastLifeDeepExplore", current.pastLifeDeepExplore),
                pastLifeIsolateSplit = o.optBoolean("pastLifeIsolateSplit", current.pastLifeIsolateSplit),
                translateAiMode = o.optBoolean("translateAiMode", current.translateAiMode),
                translateProvider = runCatching {
                    TranslateProvider.valueOf(o.optString("translateProvider", current.translateProvider.name))
                }.getOrDefault(current.translateProvider),
                brainDumpMode = false
            )
        } catch (_: Exception) {
            current.copy(brainDumpMode = false)
        }
    }

    /** 保存设置时：若仍处于抛弃大脑模式，强制保持劫持值 */
    fun enforceIfActive(s: AppSettings): AppSettings =
        if (s.brainDumpMode) apply(s) else s
}
