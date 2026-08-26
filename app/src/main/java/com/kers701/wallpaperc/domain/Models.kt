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

/** 方向过滤：无 / 过滤横屏(只要竖屏) / 过滤竖屏(只要横屏) */
enum class OrientationFilter(val code: String, val label: String) {
    None("none", "无过滤"),
    NoLandscape("no_land", "横屏过滤（仅竖屏）"),
    NoPortrait("no_port", "竖屏过滤（仅横屏）");

    companion object {
        fun fromCode(code: String): OrientationFilter =
            entries.find { it.code == code } ?: None
    }
}

/** 壁纸铺满方式（对齐 Windows 桌面选项） */
enum class WallpaperFitMode(val code: String, val label: String) {
    /** 等比放大至铺满屏幕，多出的边缘不显示（Windows「填充」） */
    Fill("fill", "填充"),
    /** 完整显示，可能留边 */
    Fit("fit", "适应"),
    /** 拉伸到屏幕尺寸（可能变形） */
    Stretch("stretch", "拉伸");

    companion object {
        fun fromCode(code: String): WallpaperFitMode =
            entries.find { it.code == code } ?: Fill
    }
}

enum class TranslateProvider(val code: String, val label: String) {
    Off("off", "关闭翻译"),
    Google("google", "谷歌翻译"),
    Microsoft("microsoft", "微软翻译"),
    Tencent("tencent", "腾讯翻译");

    companion object {
        fun fromCode(code: String): TranslateProvider =
            entries.find { it.code == code } ?: Off
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

    /** 方向过滤（三选一） */
    val orientationFilter: OrientationFilter = OrientationFilter.None,
    /** 壁纸铺满方式 */
    val fitMode: WallpaperFitMode = WallpaperFitMode.Fill,
    /** 桌面/锁屏隔离：先下一张设桌面，再下一张设锁屏 */
    val isolateHomeLock: Boolean = false,
    /** 本地动态/视频壁纸 */
    val liveWallpaperEnabled: Boolean = false,

    /** 翻译（仅展示/日志，不改搜索词） */
    val translateProvider: TranslateProvider = TranslateProvider.Off,
    val translateApiKey: String = "",
    val translateSecret: String = "",
    val translateRegion: String = "global",

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

    val filterLandscape: Boolean get() = orientationFilter == OrientationFilter.NoLandscape
    val filterPortrait: Boolean get() = orientationFilter == OrientationFilter.NoPortrait
    val cropFill: Boolean get() = fitMode == WallpaperFitMode.Fill

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
