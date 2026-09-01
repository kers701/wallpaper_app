package com.kers.killove.jhsy.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.BgMode
import com.kers.killove.jhsy.domain.CategoryMode
import com.kers.killove.jhsy.domain.Purity
import com.kers.killove.jhsy.domain.TranslateProvider
import com.kers.killove.jhsy.domain.WallpaperFitMode
import com.kers.killove.jhsy.domain.OrientationFilter
import com.kers.killove.jhsy.domain.ResolutionMode
import com.kers.killove.jhsy.domain.UiTextColor
import com.kers.killove.jhsy.domain.WallpaperTarget
import com.kers.killove.jhsy.domain.CloudBackupProvider
import com.kers.killove.jhsy.domain.CardStyle
import com.kers.killove.jhsy.domain.ProxyType
import com.kers.killove.jhsy.domain.ProxySelectMode
import com.kers.killove.jhsy.data.remote.ProxyHttp
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wallpaperc_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val INTERVAL = intPreferencesKey("interval_minutes")
        val PURITY = stringPreferencesKey("purity")
        val PURITY_FILTER = booleanPreferencesKey("purity_filter_enabled")
        val CATEGORY = stringPreferencesKey("category_mode")
        val TARGET = stringPreferencesKey("target")
        val RES_MODE = stringPreferencesKey("res_mode")
        val MIN_W = intPreferencesKey("min_width")
        val MIN_H = intPreferencesKey("min_height")
        val FGS = booleanPreferencesKey("use_fgs")
        val SKIP_OFF = booleanPreferencesKey("skip_screen_off")
        val ORIENT_FILTER = stringPreferencesKey("orientation_filter")
        val FIT_MODE = stringPreferencesKey("fit_mode")
        val ISOLATE_HL = booleanPreferencesKey("isolate_home_lock")
        val POWER_SAVE = booleanPreferencesKey("power_save")
        val POWER_SAVE_TH = intPreferencesKey("power_save_threshold")
        val SUPER_SERVICE = booleanPreferencesKey("super_service")
        val TRANS_PROVIDER = stringPreferencesKey("translate_provider")
        val TRANS_KEY = stringPreferencesKey("translate_api_key")
        val TRANS_SECRET = stringPreferencesKey("translate_secret")
        val TRANS_REGION = stringPreferencesKey("translate_region")
        val UI_SCRIM = floatPreferencesKey("ui_scrim_alpha")
        val UI_CARD = floatPreferencesKey("ui_card_alpha")
        val UI_TEXT = stringPreferencesKey("ui_text_color")
        val CARD_STYLE = stringPreferencesKey("card_style")
        val API_KEYS = stringPreferencesKey("api_keys")
        val API_KEY_INDEX = intPreferencesKey("api_key_index")
        val KEYWORDS = stringPreferencesKey("keywords")
        val KEYWORDS_URL = stringPreferencesKey("keywords_remote_url")
        val USE_KEYWORDS = booleanPreferencesKey("use_keywords")
        val JUMP_MODE = booleanPreferencesKey("jump_mode")
        val JUMP_KEYWORDS = stringPreferencesKey("jump_keywords")
        val JUMP_KEYWORD_INDEX = intPreferencesKey("jump_keyword_index")
        val ANNIHILATION_MODE = booleanPreferencesKey("annihilation_mode")
        val ANNIHILATION_EPOCH = intPreferencesKey("annihilation_epoch")
        val NET_FALLBACK = booleanPreferencesKey("network_fallback")
        val FALLBACK_API = stringPreferencesKey("fallback_api_url")
        val LOCAL_FALLBACK = booleanPreferencesKey("local_fallback")
        val FORCE_LOCAL = booleanPreferencesKey("force_local")
        val LOCAL_DIR = stringPreferencesKey("local_fallback_dir")
        val BG_API = stringPreferencesKey("bg_api_url")
        val BG_LOCAL = stringPreferencesKey("bg_local_path")
        val BG_MODE = stringPreferencesKey("bg_mode")
        val LAST_CAT = stringPreferencesKey("last_category")
        val KEYWORD_INDEX = intPreferencesKey("keyword_index")
        val LAST_CHANGE_AT = longPreferencesKey("last_change_at")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val ACCEL_MODE = booleanPreferencesKey("accel_mode")
        val ACCEL_PRIVACY = booleanPreferencesKey("accel_privacy")
        val ACCEL_NODES_URL = stringPreferencesKey("accel_nodes_url")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USER = stringPreferencesKey("proxy_user")
        val PROXY_PASS = stringPreferencesKey("proxy_pass")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_SUB = stringPreferencesKey("proxy_sub_url")
        val PROXY_NODES = stringPreferencesKey("proxy_nodes_json")
        val PROXY_NODE_ID = stringPreferencesKey("proxy_selected_node")
        val PROXY_SEL_MODE = stringPreferencesKey("proxy_select_mode")
        val PROXY_AUTO_IV = intPreferencesKey("proxy_auto_test_interval")
        val PROXY_LAST_TEST = longPreferencesKey("proxy_last_auto_test")
        val SUPER_PROXY = booleanPreferencesKey("super_proxy")
        val SUPER_PROXY_BIN = stringPreferencesKey("super_proxy_bin")
        val SUPER_PROXY_CFG = stringPreferencesKey("super_proxy_cfg")
        val SUPER_PROXY_SUB = stringPreferencesKey("super_proxy_sub")
        val SUPER_PROXY_ARGS = stringPreferencesKey("super_proxy_args")
        val SUPER_PROXY_PORT = intPreferencesKey("super_proxy_port")
        val BLACKLIST = stringPreferencesKey("blacklist_packages")
        val OVERVIEW_MINIMAL = booleanPreferencesKey("overview_minimal")
        val CHANGE_COUNT = longPreferencesKey("change_count")
        val LOCAL_FB_CACHE = booleanPreferencesKey("local_fb_use_cache")
        val LOCAL_FB_SKIP = intPreferencesKey("local_fb_cache_skip")
        val CLOUD_PROVIDER = stringPreferencesKey("cloud_backup_provider")
        val CLOUD_URL = stringPreferencesKey("cloud_backup_url")
        val CLOUD_USER = stringPreferencesKey("cloud_backup_user")
        val CLOUD_PASS = stringPreferencesKey("cloud_backup_pass")
        val CLOUD_PATH = stringPreferencesKey("cloud_backup_path")
        val CLOUD_ORIENT = booleanPreferencesKey("cloud_backup_orient")
        val CLOUD_WIFI = booleanPreferencesKey("cloud_backup_wifi")
        val LOC_AVOID = booleanPreferencesKey("location_avoid")
        val LOC_RADIUS = intPreferencesKey("location_avoid_radius")
        val AMAP_KEY = stringPreferencesKey("amap_api_key")
        val AVOID_LOCS = stringPreferencesKey("avoidance_locations")
        val LOC_FALLBACK = booleanPreferencesKey("location_fallback")
        val LOC_EXTREME = booleanPreferencesKey("location_extreme")
        val LOC_SAVED_PURITY = stringPreferencesKey("location_saved_purity")
        val LOC_SAVED_FORCE = booleanPreferencesKey("location_saved_force")
        val LOC_IN_ZONE = booleanPreferencesKey("location_in_zone")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val LEGACY_API_KEY = stringPreferencesKey("api_key")
        val LEGACY_FALLBACK = booleanPreferencesKey("fallback")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val keysRaw = p[Keys.API_KEYS] ?: p[Keys.LEGACY_API_KEY] ?: ""
        AppSettings(
            enabled = p[Keys.ENABLED] ?: false,
            intervalMinutes = p[Keys.INTERVAL] ?: 10,
            purity = Purity.fromCode(p[Keys.PURITY] ?: "110"),
            purityFilterEnabled = p[Keys.PURITY_FILTER] ?: false,
            categoryMode = CategoryMode.fromCode(p[Keys.CATEGORY] ?: "lh"),
            target = runCatching {
                WallpaperTarget.valueOf(p[Keys.TARGET] ?: "Both")
            }.getOrDefault(WallpaperTarget.Both),
            resolutionMode = ResolutionMode.entries
                .find { it.code == (p[Keys.RES_MODE] ?: "zsy") }
                ?: ResolutionMode.Device,
            minWidth = p[Keys.MIN_W] ?: 1080,
            minHeight = p[Keys.MIN_H] ?: 1920,
            useForegroundService = p[Keys.FGS] ?: false,
            skipWhenScreenOff = p[Keys.SKIP_OFF] ?: false,
            orientationFilter = OrientationFilter.fromCode(p[Keys.ORIENT_FILTER] ?: "none"),
            fitMode = WallpaperFitMode.fromCode(p[Keys.FIT_MODE] ?: "fill"),
            isolateHomeLock = p[Keys.ISOLATE_HL] ?: false,
            powerSaveEnabled = p[Keys.POWER_SAVE] ?: false,
            powerSaveBatteryThreshold = (p[Keys.POWER_SAVE_TH] ?: 20).coerceIn(5, 50),
            superServiceEnabled = p[Keys.SUPER_SERVICE] ?: false,
            translateProvider = TranslateProvider.fromCode(p[Keys.TRANS_PROVIDER] ?: "off"),
            translateApiKey = p[Keys.TRANS_KEY] ?: "",
            translateSecret = p[Keys.TRANS_SECRET] ?: "",
            translateRegion = p[Keys.TRANS_REGION] ?: "global",
            uiScrimAlpha = (p[Keys.UI_SCRIM] ?: 0.52f).coerceIn(0.15f, 0.85f),
            uiCardAlpha = (p[Keys.UI_CARD] ?: 0.28f).coerceIn(0f, 0.7f),
            uiTextColor = UiTextColor.fromCode(p[Keys.UI_TEXT] ?: "white"),
            cardStyle = CardStyle.fromCode(p[Keys.CARD_STYLE] ?: "none"),
            apiKeys = splitLines(keysRaw),
            apiKeyIndex = p[Keys.API_KEY_INDEX] ?: 0,
            keywords = splitLines(p[Keys.KEYWORDS] ?: ""),
            keywordsRemoteUrl = p[Keys.KEYWORDS_URL] ?: "",
            useKeywords = p[Keys.USE_KEYWORDS] ?: true,
            jumpModeEnabled = p[Keys.JUMP_MODE] ?: false,
            jumpKeywords = splitLines(p[Keys.JUMP_KEYWORDS] ?: ""),
            jumpKeywordIndex = p[Keys.JUMP_KEYWORD_INDEX] ?: 0,
            annihilationModeEnabled = p[Keys.ANNIHILATION_MODE] ?: false,
            annihilationEpoch = (p[Keys.ANNIHILATION_EPOCH] ?: 1).coerceAtLeast(1),
            networkFallbackEnabled = p[Keys.NET_FALLBACK] ?: p[Keys.LEGACY_FALLBACK] ?: true,
            fallbackApiUrl = p[Keys.FALLBACK_API] ?: "",
            localFallbackEnabled = p[Keys.LOCAL_FALLBACK] ?: true,
            forceLocalMode = p[Keys.FORCE_LOCAL] ?: false,
            localFallbackDir = p[Keys.LOCAL_DIR] ?: "",
            bgApiUrl = p[Keys.BG_API] ?: "",
            bgLocalPath = p[Keys.BG_LOCAL] ?: "",
            bgMode = BgMode.fromCode(p[Keys.BG_MODE] ?: "auto"),
            lastCategory = p[Keys.LAST_CAT] ?: "zr",
            keywordIndex = p[Keys.KEYWORD_INDEX] ?: 0,
            lastChangeAt = p[Keys.LAST_CHANGE_AT] ?: 0L,
            pinHash = p[Keys.PIN_HASH] ?: "",
            pinEnabled = p[Keys.PIN_ENABLED] ?: false,
            accelModeEnabled = p[Keys.ACCEL_MODE] ?: false,
            accelPrivacyAccepted = p[Keys.ACCEL_PRIVACY] ?: false,
            accelNodesRemoteUrl = p[Keys.ACCEL_NODES_URL] ?: "",
            proxyEnabled = p[Keys.PROXY_ENABLED] ?: false,
            proxyType = ProxyType.fromCode(p[Keys.PROXY_TYPE] ?: "http"),
            proxyHost = p[Keys.PROXY_HOST] ?: "",
            proxyPort = p[Keys.PROXY_PORT] ?: 0,
            proxyUser = p[Keys.PROXY_USER] ?: "",
            proxyPassword = p[Keys.PROXY_PASS] ?: "",
            proxySubUrl = p[Keys.PROXY_SUB] ?: "",
            proxyNodesJson = p[Keys.PROXY_NODES] ?: "[]",
            proxySelectedNodeId = p[Keys.PROXY_NODE_ID] ?: "",
            proxySelectMode = ProxySelectMode.fromCode(p[Keys.PROXY_SEL_MODE] ?: "manual"),
            proxyAutoTestIntervalMinutes = (p[Keys.PROXY_AUTO_IV] ?: 30).coerceIn(5, 180),
            proxyLastAutoTestAt = p[Keys.PROXY_LAST_TEST] ?: 0L,
            superProxyEnabled = p[Keys.SUPER_PROXY] ?: false,
            superProxyBinPath = p[Keys.SUPER_PROXY_BIN] ?: "",
            superProxyConfigPath = p[Keys.SUPER_PROXY_CFG] ?: "",
            superProxySubUrl = p[Keys.SUPER_PROXY_SUB] ?: "",
            superProxyArgs = p[Keys.SUPER_PROXY_ARGS] ?: "",
            superProxyLocalPort = (p[Keys.SUPER_PROXY_PORT] ?: 17890).coerceIn(1025, 65535),
            // 黑名单：标记文件存在则只认文件（空文件=空名单）；否则首次用 DataStore 迁移
            blacklistPackages = if (ProcessBridgePrefs.blacklistFileExists(context)) {
                ProcessBridgePrefs.readBlacklist(context)
            } else {
                splitLines(p[Keys.BLACKLIST] ?: "")
            },
            overviewMinimalMode = p[Keys.OVERVIEW_MINIMAL] ?: false,
            changeCount = p[Keys.CHANGE_COUNT] ?: 0L,
            localFallbackUseCache = p[Keys.LOCAL_FB_CACHE] ?: true,
            localFallbackCacheSkipNewest = (p[Keys.LOCAL_FB_SKIP] ?: 3).coerceIn(0, 50),
            cloudBackupProvider = CloudBackupProvider.fromCode(p[Keys.CLOUD_PROVIDER] ?: "off"),
            cloudBackupUrl = p[Keys.CLOUD_URL] ?: "",
            cloudBackupUser = p[Keys.CLOUD_USER] ?: "",
            cloudBackupPassword = p[Keys.CLOUD_PASS] ?: "",
            cloudBackupPath = p[Keys.CLOUD_PATH] ?: "/jhsy_backup/",
            cloudBackupOrientSplit = p[Keys.CLOUD_ORIENT] ?: false,
            cloudBackupWifiOnly = p[Keys.CLOUD_WIFI] ?: true,
            locationAvoidEnabled = p[Keys.LOC_AVOID] ?: false,
            locationAvoidRadiusMeters = (p[Keys.LOC_RADIUS] ?: 10).coerceIn(5, 500),
            amapApiKey = p[Keys.AMAP_KEY] ?: "",
            // 避让：文件存在则一律以文件为准（含 []），删除立刻对 :svc 生效
            avoidanceLocationsJson = ProcessBridgePrefs.effectiveAvoidLocationsJson(
                context,
                p[Keys.AVOID_LOCS] ?: "[]"
            ),
            locationFallbackEnabled = p[Keys.LOC_FALLBACK] ?: true,
            locationExtremeFallbackEnabled = p[Keys.LOC_EXTREME] ?: false,
            locationSavedPurity = p[Keys.LOC_SAVED_PURITY] ?: "",
            locationSavedForceLocal = p[Keys.LOC_SAVED_FORCE] ?: false,
            locationInAvoidZone = p[Keys.LOC_IN_ZONE] ?: false,
            dataSaverEnabled = p[Keys.DATA_SAVER] ?: false
        )
    }

    suspend fun save(settings: AppSettings) {
        // 黑名单以本次传入列表为准（用户取消勾选必须能删掉），写 DataStore 后 sync 会整文件覆盖桥接文件
        context.dataStore.edit { p ->
            p[Keys.ENABLED] = settings.enabled
            p[Keys.INTERVAL] = settings.intervalMinutes.coerceIn(5, 180)
            p[Keys.PURITY] = settings.purity.code
            p[Keys.PURITY_FILTER] = settings.purityFilterEnabled
            p[Keys.CATEGORY] = settings.categoryMode.code
            p[Keys.TARGET] = settings.target.name
            p[Keys.RES_MODE] = settings.resolutionMode.code
            p[Keys.MIN_W] = settings.minWidth
            p[Keys.MIN_H] = settings.minHeight
            p[Keys.FGS] = settings.useForegroundService
            p[Keys.SKIP_OFF] = settings.skipWhenScreenOff
            p[Keys.ORIENT_FILTER] = settings.orientationFilter.code
            p[Keys.FIT_MODE] = settings.fitMode.code
            p[Keys.ISOLATE_HL] = settings.isolateHomeLock
            p[Keys.POWER_SAVE] = settings.powerSaveEnabled
            p[Keys.POWER_SAVE_TH] = settings.powerSaveBatteryThreshold.coerceIn(5, 50)
            p[Keys.SUPER_SERVICE] = settings.superServiceEnabled
            p[Keys.TRANS_PROVIDER] = settings.translateProvider.code
            p[Keys.TRANS_KEY] = settings.translateApiKey
            p[Keys.TRANS_SECRET] = settings.translateSecret
            p[Keys.TRANS_REGION] = settings.translateRegion
            p[Keys.UI_SCRIM] = settings.uiScrimAlpha.coerceIn(0.15f, 0.85f)
            p[Keys.UI_CARD] = settings.uiCardAlpha.coerceIn(0f, 0.7f)
            p[Keys.UI_TEXT] = settings.uiTextColor.code
            p[Keys.CARD_STYLE] = settings.cardStyle.code
            p[Keys.API_KEYS] = settings.apiKeys.joinToString("\n")
            p[Keys.API_KEY_INDEX] = settings.apiKeyIndex
            p[Keys.KEYWORDS] = settings.keywords.joinToString("\n")
            p[Keys.KEYWORDS_URL] = settings.keywordsRemoteUrl
            p[Keys.USE_KEYWORDS] = settings.useKeywords
            p[Keys.JUMP_MODE] = settings.jumpModeEnabled
            p[Keys.JUMP_KEYWORDS] = settings.jumpKeywords.joinToString("\n")
            p[Keys.JUMP_KEYWORD_INDEX] = settings.jumpKeywordIndex
            p[Keys.ANNIHILATION_MODE] = settings.annihilationModeEnabled
            p[Keys.ANNIHILATION_EPOCH] = settings.annihilationEpoch.coerceAtLeast(1)
            p[Keys.NET_FALLBACK] = settings.networkFallbackEnabled
            p[Keys.FALLBACK_API] = settings.fallbackApiUrl
            p[Keys.LOCAL_FALLBACK] = settings.localFallbackEnabled
            p[Keys.FORCE_LOCAL] = settings.forceLocalMode
            p[Keys.LOCAL_DIR] = settings.localFallbackDir
            p[Keys.BG_API] = settings.bgApiUrl
            p[Keys.BG_LOCAL] = settings.bgLocalPath
            p[Keys.BG_MODE] = settings.bgMode.code
            p[Keys.LAST_CAT] = settings.lastCategory
            p[Keys.KEYWORD_INDEX] = settings.keywordIndex
            p[Keys.LAST_CHANGE_AT] = settings.lastChangeAt
            p[Keys.PIN_HASH] = settings.pinHash
            p[Keys.PIN_ENABLED] = settings.pinEnabled
            p[Keys.ACCEL_MODE] = settings.accelModeEnabled
            p[Keys.ACCEL_PRIVACY] = settings.accelPrivacyAccepted
            p[Keys.ACCEL_NODES_URL] = settings.accelNodesRemoteUrl
            p[Keys.PROXY_ENABLED] = settings.proxyEnabled
            p[Keys.PROXY_HOST] = settings.proxyHost
            p[Keys.PROXY_PORT] = settings.proxyPort
            p[Keys.PROXY_USER] = settings.proxyUser
            p[Keys.PROXY_PASS] = settings.proxyPassword
            p[Keys.PROXY_TYPE] = settings.proxyType.code
            p[Keys.PROXY_SUB] = settings.proxySubUrl
            p[Keys.PROXY_NODES] = settings.proxyNodesJson
            p[Keys.PROXY_NODE_ID] = settings.proxySelectedNodeId
            p[Keys.PROXY_SEL_MODE] = settings.proxySelectMode.code
            p[Keys.PROXY_AUTO_IV] = settings.proxyAutoTestIntervalMinutes.coerceIn(5, 180)
            p[Keys.PROXY_LAST_TEST] = settings.proxyLastAutoTestAt
            p[Keys.SUPER_PROXY] = settings.superProxyEnabled
            p[Keys.SUPER_PROXY_BIN] = settings.superProxyBinPath
            p[Keys.SUPER_PROXY_CFG] = settings.superProxyConfigPath
            p[Keys.SUPER_PROXY_SUB] = settings.superProxySubUrl
            p[Keys.SUPER_PROXY_ARGS] = settings.superProxyArgs
            p[Keys.SUPER_PROXY_PORT] = settings.superProxyLocalPort.coerceIn(1025, 65535)
            p[Keys.BLACKLIST] = settings.blacklistPackages.joinToString("\n")
            p[Keys.OVERVIEW_MINIMAL] = settings.overviewMinimalMode
            p[Keys.CHANGE_COUNT] = settings.changeCount
            p[Keys.LOCAL_FB_CACHE] = settings.localFallbackUseCache
            p[Keys.LOCAL_FB_SKIP] = settings.localFallbackCacheSkipNewest.coerceIn(0, 50)
            p[Keys.CLOUD_PROVIDER] = settings.cloudBackupProvider.code
            p[Keys.CLOUD_URL] = settings.cloudBackupUrl
            p[Keys.CLOUD_USER] = settings.cloudBackupUser
            p[Keys.CLOUD_PASS] = settings.cloudBackupPassword
            p[Keys.CLOUD_PATH] = settings.cloudBackupPath
            p[Keys.CLOUD_ORIENT] = settings.cloudBackupOrientSplit
            p[Keys.CLOUD_WIFI] = settings.cloudBackupWifiOnly
            p[Keys.LOC_AVOID] = settings.locationAvoidEnabled
            p[Keys.LOC_RADIUS] = settings.locationAvoidRadiusMeters.coerceIn(5, 500)
            p[Keys.AMAP_KEY] = settings.amapApiKey
            p[Keys.AVOID_LOCS] = settings.avoidanceLocationsJson
            p[Keys.LOC_FALLBACK] = settings.locationFallbackEnabled
            p[Keys.LOC_EXTREME] = settings.locationExtremeFallbackEnabled
            p[Keys.LOC_SAVED_PURITY] = settings.locationSavedPurity
            p[Keys.LOC_SAVED_FORCE] = settings.locationSavedForceLocal
            p[Keys.LOC_IN_ZONE] = settings.locationInAvoidZone
            p[Keys.DATA_SAVER] = settings.dataSaverEnabled
        }
        ProcessBridgePrefs.sync(context, settings)
        ProxyHttp.applySettings(context, settings)
    }

    suspend fun setLastCategory(code: String) {
        context.dataStore.edit { it[Keys.LAST_CAT] = code }
    }

    suspend fun setKeywordIndex(index: Int) {
        context.dataStore.edit { it[Keys.KEYWORD_INDEX] = index }
    }

    suspend fun setJumpKeywordIndex(index: Int) {
        context.dataStore.edit { it[Keys.JUMP_KEYWORD_INDEX] = index }
    }

    suspend fun setAnnihilationEpoch(epoch: Int) {
        context.dataStore.edit { it[Keys.ANNIHILATION_EPOCH] = epoch.coerceAtLeast(1) }
    }

    suspend fun setJumpKeywords(list: List<String>) {
        context.dataStore.edit {
            it[Keys.JUMP_KEYWORDS] = list.joinToString("\n")
            it[Keys.JUMP_KEYWORD_INDEX] = 0
        }
    }

    suspend fun setApiKeyIndex(index: Int) {
        context.dataStore.edit { it[Keys.API_KEY_INDEX] = index }
    }

    suspend fun setLastChangeAt(ts: Long) {
        context.dataStore.edit { it[Keys.LAST_CHANGE_AT] = ts }
    }

    suspend fun incrementChangeCount() {
        context.dataStore.edit { p ->
            p[Keys.CHANGE_COUNT] = (p[Keys.CHANGE_COUNT] ?: 0L) + 1L
        }
    }

    companion object {
        fun splitLines(raw: String): List<String> =
            raw.split('\n', ',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
    }
}
