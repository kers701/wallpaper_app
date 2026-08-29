package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.BgMode
import com.kers.killove.jhsy.domain.CardStyle
import com.kers.killove.jhsy.domain.CategoryMode
import com.kers.killove.jhsy.domain.CloudBackupProvider
import com.kers.killove.jhsy.domain.OrientationFilter
import com.kers.killove.jhsy.domain.ProxySelectMode
import com.kers.killove.jhsy.domain.ProxyType
import com.kers.killove.jhsy.domain.Purity
import com.kers.killove.jhsy.domain.ResolutionMode
import com.kers.killove.jhsy.domain.TranslateProvider
import com.kers.killove.jhsy.domain.UiTextColor
import com.kers.killove.jhsy.domain.WallpaperFitMode
import com.kers.killove.jhsy.domain.WallpaperTarget
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 配置备份 / 恢复。
 *
 * - **不包含 PIN**（pinHash / pinEnabled），恢复时始终保留本机 PIN。
 * - 覆盖代理节点、超级代理路径/订阅、云备份、定位避让、省流量等全部可配置项。
 * - 支持从远程 URL 拉取备份 JSON（http/https）。
 *
 * 注意：超级代理「内核路径 / 配置文件路径」是本机私有路径，换机后需重新选择文件导入。
 */
object ConfigBackup {
    const val FILE_NAME = "jhsy_config_backup.json"
    private const val VERSION = 2

    fun defaultFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

    fun toJson(settings: AppSettings): String {
        val o = JSONObject()
        o.put("version", VERSION)
        o.put("app", "镜花水月")
        o.put("note", "不含 PIN；超级代理本机路径换机需重选")

        // —— 调度与壁纸 ——
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
        o.put("dataSaverEnabled", settings.dataSaverEnabled)
        o.put("overviewMinimalMode", settings.overviewMinimalMode)

        // —— 翻译 / UI ——
        o.put("translateProvider", settings.translateProvider.name)
        o.put("translateApiKey", settings.translateApiKey)
        o.put("translateSecret", settings.translateSecret)
        o.put("translateRegion", settings.translateRegion)
        o.put("uiScrimAlpha", settings.uiScrimAlpha.toDouble())
        o.put("uiCardAlpha", settings.uiCardAlpha.toDouble())
        o.put("uiTextColor", settings.uiTextColor.name)
        o.put("cardStyle", settings.cardStyle.name)

        // —— 密钥与关键词 ——
        o.put("apiKeys", JSONArray(settings.apiKeys))
        o.put("apiKeyIndex", settings.apiKeyIndex)
        o.put("keywords", JSONArray(settings.keywords))
        o.put("keywordsRemoteUrl", settings.keywordsRemoteUrl)
        o.put("useKeywords", settings.useKeywords)
        o.put("jumpModeEnabled", settings.jumpModeEnabled)
        o.put("jumpKeywords", JSONArray(settings.jumpKeywords))
        o.put("jumpKeywordIndex", settings.jumpKeywordIndex)
        o.put("blacklistPackages", JSONArray(settings.blacklistPackages))

        // —— 兜底 ——
        o.put("networkFallbackEnabled", settings.networkFallbackEnabled)
        o.put("fallbackApiUrl", settings.fallbackApiUrl)
        o.put("localFallbackEnabled", settings.localFallbackEnabled)
        o.put("forceLocalMode", settings.forceLocalMode)
        o.put("localFallbackDir", settings.localFallbackDir)
        o.put("localFallbackUseCache", settings.localFallbackUseCache)
        o.put("localFallbackCacheSkipNewest", settings.localFallbackCacheSkipNewest)

        // —— 云备份 ——
        o.put("cloudBackupProvider", settings.cloudBackupProvider.name)
        o.put("cloudBackupUrl", settings.cloudBackupUrl)
        o.put("cloudBackupUser", settings.cloudBackupUser)
        o.put("cloudBackupPassword", settings.cloudBackupPassword)
        o.put("cloudBackupPath", settings.cloudBackupPath)
        o.put("cloudBackupOrientSplit", settings.cloudBackupOrientSplit)
        o.put("cloudBackupWifiOnly", settings.cloudBackupWifiOnly)

        // —— 定位避让 ——
        o.put("locationAvoidEnabled", settings.locationAvoidEnabled)
        o.put("locationAvoidRadiusMeters", settings.locationAvoidRadiusMeters)
        o.put("amapApiKey", settings.amapApiKey)
        o.put("avoidanceLocationsJson", settings.avoidanceLocationsJson)
        o.put("locationFallbackEnabled", settings.locationFallbackEnabled)
        o.put("locationExtremeFallbackEnabled", settings.locationExtremeFallbackEnabled)

        // —— 代理（HTTP/SOCKS5 + 订阅节点） ——
        o.put("proxyEnabled", settings.proxyEnabled)
        o.put("proxyType", settings.proxyType.name)
        o.put("proxyHost", settings.proxyHost)
        o.put("proxyPort", settings.proxyPort)
        o.put("proxyUser", settings.proxyUser)
        o.put("proxyPassword", settings.proxyPassword)
        o.put("proxySubUrl", settings.proxySubUrl)
        o.put("proxyNodesJson", settings.proxyNodesJson)
        o.put("proxySelectedNodeId", settings.proxySelectedNodeId)
        o.put("proxySelectMode", settings.proxySelectMode.name)
        o.put("proxyAutoTestIntervalMinutes", settings.proxyAutoTestIntervalMinutes)

        // —— 超级代理（路径为本机私有目录，换机需重选文件） ——
        o.put("superProxyEnabled", settings.superProxyEnabled)
        o.put("superProxyBinPath", settings.superProxyBinPath)
        o.put("superProxyConfigPath", settings.superProxyConfigPath)
        o.put("superProxySubUrl", settings.superProxySubUrl)
        o.put("superProxyArgs", settings.superProxyArgs)
        o.put("superProxyLocalPort", settings.superProxyLocalPort)

        // —— 软件背景 ——
        o.put("bgApiUrl", settings.bgApiUrl)
        o.put("bgLocalPath", settings.bgLocalPath)
        o.put("bgMode", settings.bgMode.name)

        // —— 运行时进度（可选，便于恢复后继续） ——
        o.put("lastCategory", settings.lastCategory)
        o.put("keywordIndex", settings.keywordIndex)
        o.put("changeCount", settings.changeCount)

        // 明确不写 pinHash / pinEnabled
        return o.toString(2)
    }

    /**
     * 解析备份 JSON，合并到 [base]；始终保留 base 的 PIN 字段。
     */
    fun fromJson(json: String, base: AppSettings): AppSettings {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("空配置")
        val o = JSONObject(trimmed)

        fun strList(key: String): List<String>? {
            if (!o.has(key) || o.isNull(key)) return null
            val arr = o.optJSONArray(key) ?: return null
            return buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        }

        return base.copy(
            enabled = o.optBoolean("enabled", base.enabled),
            intervalMinutes = o.optInt("intervalMinutes", base.intervalMinutes).coerceIn(5, 180),
            purity = runCatching { Purity.valueOf(o.optString("purity", base.purity.name)) }.getOrDefault(base.purity),
            categoryMode = runCatching { CategoryMode.valueOf(o.optString("categoryMode", base.categoryMode.name)) }.getOrDefault(base.categoryMode),
            target = runCatching { WallpaperTarget.valueOf(o.optString("target", base.target.name)) }.getOrDefault(base.target),
            resolutionMode = runCatching { ResolutionMode.valueOf(o.optString("resolutionMode", base.resolutionMode.name)) }.getOrDefault(base.resolutionMode),
            minWidth = o.optInt("minWidth", base.minWidth).coerceAtLeast(480),
            minHeight = o.optInt("minHeight", base.minHeight).coerceAtLeast(480),
            useForegroundService = o.optBoolean("useForegroundService", base.useForegroundService),
            skipWhenScreenOff = o.optBoolean("skipWhenScreenOff", base.skipWhenScreenOff),
            orientationFilter = runCatching { OrientationFilter.valueOf(o.optString("orientationFilter", base.orientationFilter.name)) }.getOrDefault(base.orientationFilter),
            fitMode = runCatching { WallpaperFitMode.valueOf(o.optString("fitMode", base.fitMode.name)) }.getOrDefault(base.fitMode),
            isolateHomeLock = o.optBoolean("isolateHomeLock", base.isolateHomeLock),
            powerSaveEnabled = o.optBoolean("powerSaveEnabled", base.powerSaveEnabled),
            powerSaveBatteryThreshold = o.optInt("powerSaveBatteryThreshold", base.powerSaveBatteryThreshold).coerceIn(5, 50),
            superServiceEnabled = o.optBoolean("superServiceEnabled", base.superServiceEnabled),
            dataSaverEnabled = o.optBoolean("dataSaverEnabled", base.dataSaverEnabled),
            overviewMinimalMode = o.optBoolean("overviewMinimalMode", base.overviewMinimalMode),

            translateProvider = runCatching { TranslateProvider.valueOf(o.optString("translateProvider", base.translateProvider.name)) }.getOrDefault(base.translateProvider),
            translateApiKey = o.optString("translateApiKey", base.translateApiKey),
            translateSecret = o.optString("translateSecret", base.translateSecret),
            translateRegion = o.optString("translateRegion", base.translateRegion),
            uiScrimAlpha = o.optDouble("uiScrimAlpha", base.uiScrimAlpha.toDouble()).toFloat().coerceIn(0.15f, 0.85f),
            uiCardAlpha = o.optDouble("uiCardAlpha", base.uiCardAlpha.toDouble()).toFloat().coerceIn(0f, 0.7f),
            uiTextColor = runCatching { UiTextColor.valueOf(o.optString("uiTextColor", base.uiTextColor.name)) }.getOrDefault(base.uiTextColor),
            cardStyle = runCatching { CardStyle.valueOf(o.optString("cardStyle", base.cardStyle.name)) }.getOrDefault(base.cardStyle),

            apiKeys = strList("apiKeys")?.ifEmpty { base.apiKeys } ?: base.apiKeys,
            apiKeyIndex = o.optInt("apiKeyIndex", base.apiKeyIndex).coerceAtLeast(0),
            keywords = strList("keywords") ?: base.keywords,
            keywordsRemoteUrl = o.optString("keywordsRemoteUrl", base.keywordsRemoteUrl),
            useKeywords = o.optBoolean("useKeywords", base.useKeywords),
            jumpModeEnabled = o.optBoolean("jumpModeEnabled", base.jumpModeEnabled),
            jumpKeywords = strList("jumpKeywords") ?: base.jumpKeywords,
            jumpKeywordIndex = o.optInt("jumpKeywordIndex", base.jumpKeywordIndex).coerceAtLeast(0),
            blacklistPackages = strList("blacklistPackages") ?: base.blacklistPackages,

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
            amapApiKey = o.optString("amapApiKey", base.amapApiKey),
            avoidanceLocationsJson = o.optString("avoidanceLocationsJson", base.avoidanceLocationsJson),
            locationFallbackEnabled = o.optBoolean("locationFallbackEnabled", base.locationFallbackEnabled),
            locationExtremeFallbackEnabled = o.optBoolean("locationExtremeFallbackEnabled", base.locationExtremeFallbackEnabled),

            proxyEnabled = o.optBoolean("proxyEnabled", base.proxyEnabled),
            proxyType = runCatching { ProxyType.valueOf(o.optString("proxyType", base.proxyType.name)) }.getOrDefault(base.proxyType),
            proxyHost = o.optString("proxyHost", base.proxyHost),
            proxyPort = o.optInt("proxyPort", base.proxyPort).coerceIn(0, 65535),
            proxyUser = o.optString("proxyUser", base.proxyUser),
            proxyPassword = o.optString("proxyPassword", base.proxyPassword),
            proxySubUrl = o.optString("proxySubUrl", base.proxySubUrl),
            proxyNodesJson = o.optString("proxyNodesJson", base.proxyNodesJson),
            proxySelectedNodeId = o.optString("proxySelectedNodeId", base.proxySelectedNodeId),
            proxySelectMode = runCatching { ProxySelectMode.valueOf(o.optString("proxySelectMode", base.proxySelectMode.name)) }.getOrDefault(base.proxySelectMode),
            proxyAutoTestIntervalMinutes = o.optInt("proxyAutoTestIntervalMinutes", base.proxyAutoTestIntervalMinutes).coerceIn(5, 180),

            superProxyEnabled = o.optBoolean("superProxyEnabled", base.superProxyEnabled),
            superProxyBinPath = o.optString("superProxyBinPath", base.superProxyBinPath),
            superProxyConfigPath = o.optString("superProxyConfigPath", base.superProxyConfigPath),
            superProxySubUrl = o.optString("superProxySubUrl", base.superProxySubUrl),
            superProxyArgs = o.optString("superProxyArgs", base.superProxyArgs),
            superProxyLocalPort = o.optInt("superProxyLocalPort", base.superProxyLocalPort).coerceIn(1025, 65535),

            bgApiUrl = o.optString("bgApiUrl", base.bgApiUrl),
            bgLocalPath = o.optString("bgLocalPath", base.bgLocalPath),
            bgMode = runCatching { BgMode.valueOf(o.optString("bgMode", base.bgMode.name)) }.getOrDefault(base.bgMode),

            lastCategory = o.optString("lastCategory", base.lastCategory),
            keywordIndex = o.optInt("keywordIndex", base.keywordIndex).coerceAtLeast(0),
            changeCount = o.optLong("changeCount", base.changeCount).coerceAtLeast(0L),

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

    /**
     * 从远程 URL 拉取备份 JSON 文本。
     * 支持 http/https；跟随一次重定向；超时 20s。
     */
    fun fetchRemoteJson(urlStr: String): String {
        val u = urlStr.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            throw IllegalArgumentException("远程配置地址须以 http:// 或 https:// 开头")
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(u).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "JHSY-ConfigBackup/2")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                ?: throw IllegalStateException("HTTP $code")
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${body.take(200)}")
            }
            val t = body.trim()
            if (t.isEmpty()) throw IllegalStateException("远程内容为空")
            // 粗校验：必须是 JSON 对象
            if (!t.startsWith("{")) {
                throw IllegalStateException("远程内容不是配置 JSON（应以 { 开头）")
            }
            return t
        } finally {
            conn?.disconnect()
        }
    }
}
