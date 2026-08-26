package com.kers701.wallpaperc.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kers701.wallpaperc.domain.AppSettings
import com.kers701.wallpaperc.domain.BgMode
import com.kers701.wallpaperc.domain.CategoryMode
import com.kers701.wallpaperc.domain.Purity
import com.kers701.wallpaperc.domain.ResolutionMode
import com.kers701.wallpaperc.domain.WallpaperTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wallpaperc_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val INTERVAL = intPreferencesKey("interval_minutes")
        val PURITY = stringPreferencesKey("purity")
        val CATEGORY = stringPreferencesKey("category_mode")
        val TARGET = stringPreferencesKey("target")
        val RES_MODE = stringPreferencesKey("res_mode")
        val MIN_W = intPreferencesKey("min_width")
        val MIN_H = intPreferencesKey("min_height")
        val FGS = booleanPreferencesKey("use_fgs")
        val SKIP_OFF = booleanPreferencesKey("skip_screen_off")
        val API_KEYS = stringPreferencesKey("api_keys")
        val API_KEY_INDEX = intPreferencesKey("api_key_index")
        val KEYWORDS = stringPreferencesKey("keywords")
        val KEYWORDS_URL = stringPreferencesKey("keywords_remote_url")
        val USE_KEYWORDS = booleanPreferencesKey("use_keywords")
        val JUMP_MODE = booleanPreferencesKey("jump_mode")
        val JUMP_KEYWORDS = stringPreferencesKey("jump_keywords")
        val JUMP_KEYWORD_INDEX = intPreferencesKey("jump_keyword_index")
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
        val LEGACY_API_KEY = stringPreferencesKey("api_key")
        val LEGACY_FALLBACK = booleanPreferencesKey("fallback")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val keysRaw = p[Keys.API_KEYS]
            ?: p[Keys.LEGACY_API_KEY]
            ?: ""
        val keywordsRaw = p[Keys.KEYWORDS] ?: ""
        val jumpKwRaw = p[Keys.JUMP_KEYWORDS] ?: ""
        AppSettings(
            enabled = p[Keys.ENABLED] ?: false,
            intervalMinutes = p[Keys.INTERVAL] ?: 10,
            purity = Purity.fromCode(p[Keys.PURITY] ?: "110"),
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
            apiKeys = splitLines(keysRaw),
            apiKeyIndex = p[Keys.API_KEY_INDEX] ?: 0,
            keywords = splitLines(keywordsRaw),
            keywordsRemoteUrl = p[Keys.KEYWORDS_URL] ?: "",
            useKeywords = p[Keys.USE_KEYWORDS] ?: true,
            jumpModeEnabled = p[Keys.JUMP_MODE] ?: false,
            jumpKeywords = splitLines(jumpKwRaw),
            jumpKeywordIndex = p[Keys.JUMP_KEYWORD_INDEX] ?: 0,
            networkFallbackEnabled = p[Keys.NET_FALLBACK]
                ?: p[Keys.LEGACY_FALLBACK]
                ?: true,
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
            pinEnabled = p[Keys.PIN_ENABLED] ?: false
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.ENABLED] = settings.enabled
            p[Keys.INTERVAL] = settings.intervalMinutes.coerceIn(5, 180)
            p[Keys.PURITY] = settings.purity.code
            p[Keys.CATEGORY] = settings.categoryMode.code
            p[Keys.TARGET] = settings.target.name
            p[Keys.RES_MODE] = settings.resolutionMode.code
            p[Keys.MIN_W] = settings.minWidth
            p[Keys.MIN_H] = settings.minHeight
            p[Keys.FGS] = settings.useForegroundService
            p[Keys.SKIP_OFF] = settings.skipWhenScreenOff
            p[Keys.API_KEYS] = settings.apiKeys.joinToString("\n")
            p[Keys.API_KEY_INDEX] = settings.apiKeyIndex
            p[Keys.KEYWORDS] = settings.keywords.joinToString("\n")
            p[Keys.KEYWORDS_URL] = settings.keywordsRemoteUrl
            p[Keys.USE_KEYWORDS] = settings.useKeywords
            p[Keys.JUMP_MODE] = settings.jumpModeEnabled
            p[Keys.JUMP_KEYWORDS] = settings.jumpKeywords.joinToString("\n")
            p[Keys.JUMP_KEYWORD_INDEX] = settings.jumpKeywordIndex
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
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLED] = enabled }
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

    suspend fun setJumpKeywords(list: List<String>) {
        context.dataStore.edit {
            it[Keys.JUMP_KEYWORDS] = list.joinToString("\n")
            it[Keys.JUMP_KEYWORD_INDEX] = 0
        }
    }

    suspend fun setApiKeyIndex(index: Int) {
        context.dataStore.edit { it[Keys.API_KEY_INDEX] = index }
    }

    suspend fun setKeywords(list: List<String>) {
        context.dataStore.edit {
            it[Keys.KEYWORDS] = list.joinToString("\n")
        }
    }

    suspend fun setLastChangeAt(ts: Long) {
        context.dataStore.edit { it[Keys.LAST_CHANGE_AT] = ts }
    }

    companion object {
        fun splitLines(raw: String): List<String> =
            raw.split('\n', ',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
    }
}
