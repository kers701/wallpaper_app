package com.kers701.wallpaperc.domain

enum class CategoryMode(val code: String, val label: String) {
    Zr("zr", "仅真人"),
    Dm("dm", "仅动漫"),
    Rotate("lh", "真人/动漫轮换");

    companion object {
        fun fromCode(code: String): CategoryMode =
            entries.find { it.code == code } ?: Rotate
    }
}

enum class WallpaperTarget(val label: String) {
    Home("仅桌面"),
    Lock("仅锁屏"),
    Both("桌面+锁屏")
}

enum class ResolutionMode(val code: String, val label: String) {
    Device("zsy", "设备自适应"),
    Min15k("1.5k", "最低 1500×1500"),
    Custom("zdy", "自定义")
}

enum class Purity(val code: String, val label: String) {
    R8("100", "R8 仅 SFW"),
    R13("110", "R13 SFW+Sketchy"),
    R18("111", "R18 全部"),
    Only13("010", "仅 Sketchy"),
    Only18("001", "仅 NSFW"),
    R18D("011", "Sketchy+NSFW");

    companion object {
        fun fromCode(code: String): Purity =
            entries.find { it.code == code } ?: R13
    }
}

enum class BgMode(val code: String, val label: String) {
    Auto("auto", "系统壁纸/莫奈"),
    Api("api", "API 链接"),
    Local("local", "本地路径"),
    Monet("monet", "莫奈取色");

    companion object {
        fun fromCode(code: String): BgMode =
            entries.find { it.code == code } ?: Auto
    }
}

/** 文字颜色预设 */
enum class UiTextColor(val code: String, val label: String, val argb: Long) {
    White("white", "白色", 0xFFFFFFFF),
    SoftWhite("soft", "柔白", 0xFFE8E8E8),
    LightGray("gray", "浅灰", 0xFFB0B0B0),
    Warm("warm", "暖米", 0xFFFFF3E0),
    Cyan("cyan", "浅青", 0xFFB2EBF2),
    Pink("pink", "浅粉", 0xFFF8BBD0);

    companion object {
        fun fromCode(code: String): UiTextColor =
            entries.find { it.code == code } ?: White
    }
}

data class AppSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 10,
    val purity: Purity = Purity.R13,
    val categoryMode: CategoryMode = CategoryMode.Rotate,
    val target: WallpaperTarget = WallpaperTarget.Both,
    val resolutionMode: ResolutionMode = ResolutionMode.Device,
    val minWidth: Int = 1080,
    val minHeight: Int = 1920,
    val useForegroundService: Boolean = false,
    val skipWhenScreenOff: Boolean = false,

    /** 开启后过滤掉横屏图，只保留竖屏 */
    val filterLandscape: Boolean = false,
    /** 开启后过滤掉竖屏图，只保留横屏 */
    val filterPortrait: Boolean = false,
    /** 设置壁纸时按屏幕尺寸中心裁切填充 */
    val cropFill: Boolean = true,
    /** 桌面/锁屏隔离：各下不同图分别设置 */
    val isolateHomeLock: Boolean = false,

    /** UI 遮罩透明度 0.15～0.85 */
    val uiScrimAlpha: Float = 0.52f,
    /** 卡片半透明程度 0～0.7（越高越不透明） */
    val uiCardAlpha: Float = 0.28f,
    val uiTextColor: UiTextColor = UiTextColor.White,

    val apiKeys: List<String> = emptyList(),
    val apiKeyIndex: Int = 0,

    val keywords: List<String> = emptyList(),
    val keywordsRemoteUrl: String = "",
    val useKeywords: Boolean = true,

    val jumpModeEnabled: Boolean = false,
    val jumpKeywords: List<String> = emptyList(),
    val jumpKeywordIndex: Int = 0,

    val networkFallbackEnabled: Boolean = true,
    val fallbackApiUrl: String = "",

    val localFallbackEnabled: Boolean = true,
    val forceLocalMode: Boolean = false,
    val localFallbackDir: String = "",

    val bgApiUrl: String = "",
    val bgLocalPath: String = "",
    val bgMode: BgMode = BgMode.Auto,

    val lastCategory: String = "zr",
    val keywordIndex: Int = 0,
    val lastChangeAt: Long = 0L,

    val pinHash: String = "",
    val pinEnabled: Boolean = false
) {
    fun nextApiKey(): String? {
        val keys = apiKeys.map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return null
        return keys[apiKeyIndex.mod(keys.size)]
    }

    fun fallbackApiUrls(): List<String> =
        fallbackApiUrl.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && (it.startsWith("http://") || it.startsWith("https://")) }

    fun activeKeywords(): List<String> {
        if (!useKeywords) return emptyList()
        if (jumpModeEnabled && jumpKeywords.isNotEmpty()) return jumpKeywords
        return keywords
    }

    fun activeKeywordIndex(): Int {
        return if (jumpModeEnabled && jumpKeywords.isNotEmpty()) jumpKeywordIndex else keywordIndex
    }

    /** 距下次更换剩余分钟；未开启或未换过返回 -1 */
    fun minutesUntilNext(): Int {
        if (!enabled || lastChangeAt <= 0L) return -1
        val intervalMs = intervalMinutes.coerceIn(5, 180) * 60_000L
        val remain = lastChangeAt + intervalMs - System.currentTimeMillis()
        if (remain <= 0) return 0
        return ((remain + 59_999) / 60_000).toInt()
    }
}

data class WallpaperItem(
    val id: String,
    val pathUrl: String,
    val thumbsUrl: String?,
    val width: Int,
    val height: Int,
    val purity: String,
    val category: String,
    val source: String = "wallhaven",
    val tags: List<String> = emptyList(),
    val fileSize: Long = 0L,
    val prefetchedBytes: ByteArray? = null
)

sealed class ChangeResult {
    data class Success(val item: WallpaperItem, val localPath: String, val detail: String = "") : ChangeResult()
    data class Failure(val message: String) : ChangeResult()
}
