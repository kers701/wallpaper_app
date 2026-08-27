package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.BgMode
import com.kers.killove.jhsy.domain.CategoryMode
import com.kers.killove.jhsy.domain.OrientationFilter
import com.kers.killove.jhsy.domain.Purity
import com.kers.killove.jhsy.domain.ResolutionMode
import com.kers.killove.jhsy.domain.TranslateProvider
import com.kers.killove.jhsy.domain.UiTextColor
import com.kers.killove.jhsy.domain.WallpaperFitMode
import com.kers.killove.jhsy.domain.WallpaperTarget
import com.kers.killove.jhsy.domain.CloudBackupProvider
import com.kers.killove.jhsy.domain.CardStyle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 配置备份 / 恢复。
 * **不包含 PIN**（pinHash / pinEnabled）。
 */
object ConfigBackup {
    const val FILE_NAME = "jhsy_config_backup.json"
    private const val VERSION = 1

    fun defaultFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

    fun toJson(settings: AppSettings): String {
        val o = JSONObject()
        o.put("version", VERSION)
        o.put("app", "镜花水月")
        // 不含 PIN
        o.put("enabled", settings.enabled)
        o.put("intervalMinutes", settings.intervalMinutes)
        o.put("purity", settings.purity.name)
        o.put("categoryMode", settings.categoryMode.name)
        o.put("target", settings.target.name)
        o.put("resolutionMode", settings.resolutionMode.name)
        o.put("minWidth", settings.minWidth)
        o.put("minHeight", settings.minHeight)
        o.put("useForegroundService", settings.useForegroundService)
        o.put("skipWhenScreenOff", settings.skipWhenScreenOff)
        o.put("orientationFilter", settings.orientationFilter.name)
        o.put("fitMode", settings.fitMode.name)
        o.put("isolateHomeLock", settings.isolateHomeLock)
        o.put("powerSaveEnabled", settings.powerSaveEnabled)
        o.put("powerSaveBatteryThreshold", settings.powerSaveBatteryThreshold)
        o.put("superServiceEnabled", settings.superServiceEnabled)
        o.put("translateProvider", settings.translateProvider.name)
        o.put("translateApiKey", settings.translateApiKey)
        o.put("translateSecret", settings.translateSecret)
        o.put("translateRegion", settings.translateRegion)
        o.put("uiScrimAlpha", settings.uiScrimAlpha.toDouble())
        o.put("uiCardAlpha", settings.uiCardAlpha.toDouble())
        o.put("uiTextColor", settings.uiTextColor.name)
        o.put("cardStyle", settings.cardStyle.name)
        o.put("apiKeys", JSONArray(settings.apiKeys))
        o.put("apiKeyIndex", settings.apiKeyIndex)
        o.put("keywords", JSONArray(settings.keywords))
        o.put("keywordsRemoteUrl", settings.keywordsRemoteUrl)
        o.put("useKeywords", settings.useKeywords)
        o.put("jumpModeEnabled", settings.jumpModeEnabled)
        o.put("jumpKeywords", JSONArray(settings.jumpKeywords))
        o.put("jumpKeywordIndex", settings.jumpKeywordIndex)
        o.put("networkFallbackEnabled", settings.networkFallbackEnabled)
        o.put("fallbackApiUrl", settings.fallbackApiUrl)
        o.put("localFallbackEnabled", settings.localFallbackEnabled)
        o.put("forceLocalMode", settings.forceLocalMode)
        o.put("localFallbackDir", settings.localFallbackDir)
        o.put("localFallbackUseCache", settings.localFallbackUseCache)
        o.put("localFallbackCacheSkipNewest", settings.localFallbackCacheSkipNewest)
        o.put("cloudBackupProvider", settings.cloudBackupProvider.name)
        o.put("cloudBackupUrl", settings.cloudBackupUrl)
        o.put("cloudBackupUser", settings.cloudBackupUser)
        o.put("cloudBackupPassword", settings.cloudBackupPassword)
        o.put("cloudBackupPath", settings.cloudBackupPath)
        o.put("cloudBackupOrientSplit", settings.cloudBackupOrientSplit)
        o.put("cloudBackupWifiOnly", settings.cloudBackupWifiOnly)
        o.put("locationAvoidEnabled", settings.locationAvoidEnabled)
        o.put("locationAvoidRadiusMeters", settings.locationAvoidRadiusMeters)
        o.put("proxyEnabled", settings.proxyEnabled)
        o.put("proxyHost", settings.proxyHost)
        o.put("proxyPort", settings.proxyPort)
        o.put("proxyUser", settings.proxyUser)
        o.put("proxyPassword", settings.proxyPassword)
        o.put("amapApiKey", settings.amapApiKey)
        o.put("avoidanceLocationsJson", settings.avoidanceLocationsJson)
        o.put("locationFallbackEnabled", settings.locationFallbackEnabled)
        o.put("locationExtremeFallbackEnabled", settings.locationExtremeFallbackEnabled)
        o.put("bgApiUrl", settings.bgApiUrl)
        o.put("bgLocalPath", settings.bgLocalPath)
        o.put("bgMode", settings.bgMode.name)
        // 运行时状态可选备份，便于恢复后继续
        o.put("lastCategory", settings.lastCategory)
        o.put("keywordIndex", settings.keywordIndex)
        return o.toString(2)
    }

    /**
     * 解析备份 JSON，合并到 [base]；始终保留 base 的 PIN 字段。
     */
    fun fromJson(json: String, base: AppSettings): AppSettings {
        val o = JSONObject(json.trim())
        fun strList(key: String): List<String> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        }
        fun enumPurity(n: String) = runCatching { Purity.valueOf(n) }.getOrDefault(base.purity)
        fun enumCat(n: String) = runCatching { CategoryMode.valueOf(n) }.getOrDefault(base.categoryMode)
        fun enumTarget(n: String) = runCatching { WallpaperTarget.valueOf(n) }.getOrDefault(base.target)
        fun enumRes(n: String) = runCatching { ResolutionMode.valueOf(n) }.getOrDefault(base.resolutionMode)
        fun enumOri(n: String) = runCatching { OrientationFilter.valueOf(n) }.getOrDefault(base.orientationFilter)
        fun enumFit(n: String) = runCatching { WallpaperFitMode.valueOf(n) }.getOrDefault(base.fitMode)
        fun enumTr(n: String) = runCatching { TranslateProvider.valueOf(n) }.getOrDefault(base.translateProvider)
        fun enumUi(n: String) = runCatching { UiTextColor.valueOf(n) }.getOrDefault(base.uiTextColor)
        fun enumBg(n: String) = runCatching { BgMode.valueOf(n) }.getOrDefault(base.bgMode)

        return base.copy(
            enabled = o.optBoolean("enabled", base.enabled),
            intervalMinutes = o.optInt("intervalMinutes", base.intervalMinutes).coerceIn(5, 180),
            purity = enumPurity(o.optString("purity", base.purity.name)),
            categoryMode = enumCat(o.optString("categoryMode", base.categoryMode.name)),
            target = enumTarget(o.optString("target", base.target.name)),
            resolutionMode = enumRes(o.optString("resolutionMode", base.resolutionMode.name)),
            minWidth = o.optInt("minWidth", base.minWidth).coerceAtLeast(480),
            minHeight = o.optInt("minHeight", base.minHeight).coerceAtLeast(480),
            useForegroundService = o.optBoolean("useForegroundService", base.useForegroundService),
            skipWhenScreenOff = o.optBoolean("skipWhenScreenOff", base.skipWhenScreenOff),
            orientationFilter = enumOri(o.optString("orientationFilter", base.orientationFilter.name)),
            fitMode = enumFit(o.optString("fitMode", base.fitMode.name)),
            isolateHomeLock = o.optBoolean("isolateHomeLock", base.isolateHomeLock),
            powerSaveEnabled = o.optBoolean("powerSaveEnabled", base.powerSaveEnabled),
            powerSaveBatteryThreshold = o.optInt("powerSaveBatteryThreshold", base.powerSaveBatteryThreshold).coerceIn(5, 50),
            superServiceEnabled = o.optBoolean("superServiceEnabled", base.superServiceEnabled),
            translateProvider = enumTr(o.optString("translateProvider", base.translateProvider.name)),
            translateApiKey = o.optString("translateApiKey", base.translateApiKey),
            translateSecret = o.optString("translateSecret", base.translateSecret),
            translateRegion = o.optString("translateRegion", base.translateRegion),
            uiScrimAlpha = o.optDouble("uiScrimAlpha", base.uiScrimAlpha.toDouble()).toFloat().coerceIn(0.15f, 0.85f),
            uiCardAlpha = o.optDouble("uiCardAlpha", base.uiCardAlpha.toDouble()).toFloat().coerceIn(0f, 0.7f),
            uiTextColor = enumUi(o.optString("uiTextColor", base.uiTextColor.name)),
            cardStyle = runCatching { CardStyle.valueOf(o.optString("cardStyle", base.cardStyle.name)) }.getOrDefault(base.cardStyle),
            apiKeys = strList("apiKeys").ifEmpty { base.apiKeys },
            apiKeyIndex = o.optInt("apiKeyIndex", base.apiKeyIndex).coerceAtLeast(0),
            keywords = strList("keywords"),
            keywordsRemoteUrl = o.optString("keywordsRemoteUrl", base.keywordsRemoteUrl),
            useKeywords = o.optBoolean("useKeywords", base.useKeywords),
            jumpModeEnabled = o.optBoolean("jumpModeEnabled", base.jumpModeEnabled),
            jumpKeywords = strList("jumpKeywords"),
            jumpKeywordIndex = o.optInt("jumpKeywordIndex", base.jumpKeywordIndex).coerceAtLeast(0),
            networkFallbackEnabled = o.optBoolean("networkFallbackEnabled", base.networkFallbackEnabled),
            fallbackApiUrl = o.optString("fallbackApiUrl", base.fallbackApiUrl),
            localFallbackEnabled = o.optBoolean("localFallbackEnabled", base.localFallbackEnabled),
            forceLocalMode = o.optBoolean("forceLocalMode", base.forceLocalMode),
            localFallbackDir = o.optString("localFallbackDir", base.localFallbackDir),
            localFallbackUseCache = o.optBoolean("localFallbackUseCache", base.localFallbackUseCache),
            localFallbackCacheSkipNewest = o.optInt("localFallbackCacheSkipNewest", base.localFallbackCacheSkipNewest).coerceIn(0, 50),
            cloudBackupProvider = runCatching { CloudBackupProvider.valueOf(o.optString("cloudBackupProvider", base.cloudBackupProvider.name)) }.getOrDefault(base.cloudBackupProvider),
            cloudBackupUrl = o.optString("cloudBackupUrl", base.cloudBackupUrl),
            cloudBackupUser = o.optString("cloudBackupUser", base.cloudBackupUser),
            cloudBackupPassword = o.optString("cloudBackupPassword", base.cloudBackupPassword),
            cloudBackupPath = o.optString("cloudBackupPath", base.cloudBackupPath),
            cloudBackupOrientSplit = o.optBoolean("cloudBackupOrientSplit", base.cloudBackupOrientSplit),
            cloudBackupWifiOnly = o.optBoolean("cloudBackupWifiOnly", base.cloudBackupWifiOnly),
            locationAvoidEnabled = o.optBoolean("locationAvoidEnabled", base.locationAvoidEnabled),
            locationAvoidRadiusMeters = o.optInt("locationAvoidRadiusMeters", base.locationAvoidRadiusMeters).coerceIn(5, 500),
            proxyEnabled = o.optBoolean("proxyEnabled", base.proxyEnabled),
            proxyHost = o.optString("proxyHost", base.proxyHost),
            proxyPort = o.optInt("proxyPort", base.proxyPort),
            proxyUser = o.optString("proxyUser", base.proxyUser),
            proxyPassword = o.optString("proxyPassword", base.proxyPassword),
            amapApiKey = o.optString("amapApiKey", base.amapApiKey),
            avoidanceLocationsJson = o.optString("avoidanceLocationsJson", base.avoidanceLocationsJson),
            locationFallbackEnabled = o.optBoolean("locationFallbackEnabled", base.locationFallbackEnabled),
            locationExtremeFallbackEnabled = o.optBoolean("locationExtremeFallbackEnabled", base.locationExtremeFallbackEnabled),
            bgApiUrl = o.optString("bgApiUrl", base.bgApiUrl),
            bgLocalPath = o.optString("bgLocalPath", base.bgLocalPath),
            bgMode = enumBg(o.optString("bgMode", base.bgMode.name)),
            lastCategory = o.optString("lastCategory", base.lastCategory),
            keywordIndex = o.optInt("keywordIndex", base.keywordIndex).coerceAtLeast(0),
            // PIN 永不从备份恢复
            pinHash = base.pinHash,
            pinEnabled = base.pinEnabled
        )
    }

    fun writeToFile(context: Context, settings: AppSettings): File {
        val file = defaultFile(context)
        file.parentFile?.mkdirs()
        file.writeText(toJson(settings), Charsets.UTF_8)
        return file
    }

    fun readFromFile(context: Context, base: AppSettings, file: File = defaultFile(context)): AppSettings {
        if (!file.exists()) throw IllegalStateException("备份文件不存在: ${file.absolutePath}")
        return fromJson(file.readText(Charsets.UTF_8), base)
    }
}
