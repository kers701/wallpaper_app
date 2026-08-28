package com.kers.killove.jhsy.domain

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
    /** 原图居中，超出屏幕的部分裁掉（Windows「居中」） */
    Center("center", "居中"),
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


/** 更换触发方式 */
enum class TriggerType(val code: String, val label: String) {
    /** App 内「立即更换」或通知「立即更换」 */
    Manual("manual", "手动"),
    /** 前台服务周期到期自动更换 */
    Auto("auto", "定时"),
    /** 亮屏 / 解锁时发现已到期补换 */
    ScreenOn("screen_on", "亮屏补换"),
    /** WorkManager 单次调度补跑 */
    Worker("worker", "调度补跑"),
    /** 开机 / 包替换后调度拉起 */
    Boot("boot", "开机"),
    /** 其它或旧数据 */
    Unknown("unknown", "其它");

    companion object {
        fun fromCode(code: String): TriggerType =
            entries.find { it.code == code }
                ?: when (code) {
                    "manual" -> Manual
                    "auto" -> Auto
                    else -> Unknown
                }
    }
}

/** 云备份提供方 */

/** 板块子主题美化（全局同步到所有页面卡片） */
enum class CardStyle(val code: String, val label: String) {
    None("none", "无"),
    LiquidGlass("liquid_glass", "液态玻璃"),
    GaussianBlur("gaussian_blur", "高斯模糊"),
    Fog("fog", "雾化");

    companion object {
        fun fromCode(code: String): CardStyle =
            entries.find { it.code == code } ?: None
    }
}

enum class CloudBackupProvider(val code: String, val label: String) {
    Off("off", "关闭"),
    WebDav("webdav", "WebDAV"),
    OneDrive("onedrive", "OneDrive"),
    GoogleDrive("gdrive", "Google 云盘");

    companion object {
        fun fromCode(code: String): CloudBackupProvider =
            entries.find { it.code == code } ?: Off
    }
}

/** 定位避让点 */
data class AvoidanceLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double
)


enum class ProxyType(val code: String, val label: String) {
    Http("http", "HTTP"),
    Socks5("socks5", "SOCKS5");
    companion object {
        fun fromCode(c: String) = entries.find { it.code.equals(c, true) } ?: Http
    }
}

enum class ProxySelectMode(val code: String, val label: String) {
    Manual("manual", "手动选择"),
    Auto("auto", "自动优选");
    companion object {
        fun fromCode(c: String) = entries.find { it.code.equals(c, true) } ?: Manual
    }
}

/** 代理节点（HTTP / SOCKS5） */
data class ProxyNode(
    val id: String,
    val name: String,
    val type: ProxyType = ProxyType.Http,
    val host: String,
    val port: Int,
    val user: String = "",
    val password: String = "",
    /** -1 未测；-2 失败；>=0 毫秒 */
    val latencyMs: Long = -1L
)

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
    /** 桌面/锁屏隔离：先下一张设桌面，再下一张设锁屏（可用不同关键词） */
    val isolateHomeLock: Boolean = false,

    /** 省电模式：电量低于阈值时暂停自动更换，恢复后继续；充电时忽略 */
    val powerSaveEnabled: Boolean = false,
    /** 省电阈值 5～50，默认 20 */
    val powerSaveBatteryThreshold: Int = 20,

    /** 超级服务：独立进程 + Root/无障碍保活 */
    val superServiceEnabled: Boolean = false,

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
    /** 板块美化：液态玻璃 / 高斯模糊 / 雾化 / 无 */
    val cardStyle: CardStyle = CardStyle.None,

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
    val pinEnabled: Boolean = false,

    /** 代理：HTTP/SOCKS5；开启且填了地址后走代理；失败或未开则系统直连 */
    val proxyEnabled: Boolean = false,
    val proxyType: ProxyType = ProxyType.Http,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyUser: String = "",
    val proxyPassword: String = "",
    /** 订阅链接（http/https），导入后写入 proxyNodesJson */
    val proxySubUrl: String = "",
    val proxyNodesJson: String = "[]",
    val proxySelectedNodeId: String = "",
    val proxySelectMode: ProxySelectMode = ProxySelectMode.Manual,
    /** 自动测速间隔（分钟），5～180 */
    val proxyAutoTestIntervalMinutes: Int = 30,
    val proxyLastAutoTestAt: Long = 0L,

    /**
     * 超级代理（仅本应用）：Root 启动自定义内核，监听 127.0.0.1:superProxyLocalPort。
     * 开启后 ProxyHttp 优先走本地端口，不启用 TUN/全局劫持。
     * 配置优先级：superProxyConfigPath（存在） > superProxySubUrl 自动生成 Clash 默认配置。
     */
    val superProxyEnabled: Boolean = false,
    val superProxyBinPath: String = "",
    /** 用户自定义配置路径；文件存在时优先于订阅自动配置 */
    val superProxyConfigPath: String = "",
    /** 订阅链接（http/https）；无自定义配置时自动生成 Clash Meta 默认 YAML */
    val superProxySubUrl: String = "",
    /** 启动参数，占位符 {bin} {config} {port} {workdir}；空则按内核名猜测 */
    val superProxyArgs: String = "",
    val superProxyLocalPort: Int = 17890,

    /** 前台黑名单包名：这些应用在前台时休眠不换壁纸 */
    val blacklistPackages: List<String> = emptyList(),
    /** 概览页极简模式：只显示概览，隐藏其它页 */
    val overviewMinimalMode: Boolean = false,
    /** 累计成功更换次数 */
    val changeCount: Long = 0L,

    /** 本地兜底时是否也从 wallpapers 缓存选取（排除最新若干张） */
    val localFallbackUseCache: Boolean = true,
    /** 从缓存选取时跳过最新 N 张 */
    val localFallbackCacheSkipNewest: Int = 3,

    /** 云备份 */
    val cloudBackupProvider: CloudBackupProvider = CloudBackupProvider.Off,
    val cloudBackupUrl: String = "",
    val cloudBackupUser: String = "",
    val cloudBackupPassword: String = "",
    val cloudBackupPath: String = "/jhsy_backup/",
    /** 横屏/竖屏壁纸分离备份（仅 WiFi） */
    val cloudBackupOrientSplit: Boolean = false,
    val cloudBackupWifiOnly: Boolean = true,

    /** 定位避让 */
    val locationAvoidEnabled: Boolean = false,
    /** 触发半径（米），默认 10，可调 5～500 */
    val locationAvoidRadiusMeters: Int = 10,
    val amapApiKey: String = "",
    val avoidanceLocationsJson: String = "[]",
    /** 绿色模式：进入避让区时纯度在 R13 与「仅 Sketchy」间随机 */
    val locationFallbackEnabled: Boolean = true,
    /** 定位极限回退：进入避让区仅用本地文件换壁纸 */
    val locationExtremeFallbackEnabled: Boolean = false,
    /** 进入避让区前保存的纯度 code，离开后恢复；空表示未锁定 */
    val locationSavedPurity: String = "",
    val locationSavedForceLocal: Boolean = false,
    val locationInAvoidZone: Boolean = false,

    /** 省流量：按当日缓存增量抬高间隔，≥20GB 今日停换 */
    val dataSaverEnabled: Boolean = false
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

    /** 下次更换时间戳；无效返回 0 */
    fun nextChangeAt(): Long {
        if (!enabled || lastChangeAt <= 0L) return 0L
        return lastChangeAt + intervalMinutes.coerceIn(5, 180) * 60_000L
    }


    fun proxyNodes(): List<ProxyNode> {
        if (proxyNodesJson.isBlank() || proxyNodesJson == "[]") return emptyList()
        return try {
            val arr = org.json.JSONArray(proxyNodesJson)
            val out = mutableListOf<ProxyNode>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val host = o.optString("host")
                val port = o.optInt("port", 0)
                if (host.isBlank() || port !in 1..65535) continue
                out.add(
                    ProxyNode(
                        id = o.optString("id").ifBlank { "${host}_$port" },
                        name = o.optString("name").ifBlank { "$host:$port" },
                        type = ProxyType.fromCode(o.optString("type", "http")),
                        host = host,
                        port = port,
                        user = o.optString("user"),
                        password = o.optString("password"),
                        latencyMs = o.optLong("latencyMs", -1L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun selectedProxyNode(): ProxyNode? {
        val list = proxyNodes()
        if (list.isEmpty()) return null
        val id = proxySelectedNodeId
        return list.find { it.id == id } ?: list.firstOrNull()
    }

    fun avoidanceLocations(): List<AvoidanceLocation> {
        if (avoidanceLocationsJson.isBlank() || avoidanceLocationsJson == "[]") return emptyList()
        return try {
            val arr = org.json.JSONArray(avoidanceLocationsJson)
            val out = mutableListOf<AvoidanceLocation>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                val name = o.optString("name", id)
                val lat = o.optDouble("lat", Double.NaN)
                val lng = o.optDouble("lng", Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    out.add(AvoidanceLocation(id, name, lat, lng))
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
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
