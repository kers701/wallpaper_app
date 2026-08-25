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

/**
 * Wallhaven purity 三位：SFW / Sketchy / NSFW
 */
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

    /** 多个 Wallhaven API Key，换行或逗号分隔存储 */
    val apiKeys: List<String> = emptyList(),
    /** 轮换到第几个 key */
    val apiKeyIndex: Int = 0,

    /** 本地关键词，每行一个 */
    val keywords: List<String> = emptyList(),
    /** 远程关键词 txt 地址（可选） */
    val keywordsRemoteUrl: String = "",
    /** 是否优先使用关键词搜索（有词则 q=随机选一词） */
    val useKeywords: Boolean = true,

    /** 网络兜底：Wallhaven 失败时走备用 API */
    val networkFallbackEnabled: Boolean = true,
    /**
     * 兜底 API 模板，支持占位：
     * {width} {height}
     * 响应可为：纯图片 URL 一行、或 JSON（自动尝试常见字段）
     */
    val fallbackApiUrl: String = "",

    /** 本地兜底：无网或强制本地时从目录随机选图 */
    val localFallbackEnabled: Boolean = true,
    /** 强制只从本地目录轮换（不访问网络） */
    val forceLocalMode: Boolean = false,
    /**
     * 本地兜底目录。空则使用 App 私有目录 local_fallback/
     * 可填绝对路径（需存储权限，如 /storage/emulated/0/Pictures/Wallpapers）
     */
    val localFallbackDir: String = "",

    /** 轮换模式下当前类别 */
    val lastCategory: String = "zr",
    /** 关键词轮询下标 */
    val keywordIndex: Int = 0
) {
    fun nextApiKey(): String? {
        val keys = apiKeys.map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return null
        return keys[apiKeyIndex.mod(keys.size)]
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
    val source: String = "wallhaven"
)

sealed class ChangeResult {
    data class Success(val item: WallpaperItem, val localPath: String) : ChangeResult()
    data class Failure(val message: String) : ChangeResult()
}
