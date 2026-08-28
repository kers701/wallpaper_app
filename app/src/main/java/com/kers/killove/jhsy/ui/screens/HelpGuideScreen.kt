package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.BuildConfig
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.util.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 使用说明：功能讲解与配置建议（只读滚动页）+ 检查更新。
 */
@Composable
fun HelpGuideScreen(onBack: () -> Unit) {
    val textColor = LocalUiTextColor.current
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var updateStatus by remember { mutableStateOf("当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pendingInfo by remember { mutableStateOf<AppUpdateChecker.ReleaseInfo?>(null) }

    fun checkUpdate() {
        if (checking || downloading) return
        checking = true
        updateStatus = "正在查询 GitHub Releases…"
        pendingInfo = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AppUpdateChecker.checkLatest()
            }
            checking = false
            when (result) {
                is AppUpdateChecker.CheckResult.UpToDate -> {
                    updateStatus = "已是最新：本机 ${result.current}，线上 ${result.latest}"
                }
                is AppUpdateChecker.CheckResult.UpdateAvailable -> {
                    pendingInfo = result.info
                    val sizeMb = if (result.info.apkSize > 0)
                        String.format("%.1f MB", result.info.apkSize / (1024.0 * 1024.0))
                    else "未知大小"
                    updateStatus =
                        "发现新版本 ${result.info.tag}（$sizeMb）\n${result.info.name}\n点下方「下载并安装」更新"
                }
                is AppUpdateChecker.CheckResult.Failed -> {
                    updateStatus = "检查失败：${result.message}"
                }
            }
        }
    }

    fun downloadAndInstall(info: AppUpdateChecker.ReleaseInfo) {
        if (downloading) return
        if (!AppUpdateChecker.canInstallPackages(context)) {
            updateStatus = "需要允许「安装未知应用」权限，即将打开系统设置"
            AppUpdateChecker.openInstallPermissionSettings(context)
            return
        }
        downloading = true
        progress = 0f
        updateStatus = "正在下载 ${info.apkName ?: "APK"}…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AppUpdateChecker.downloadApk(context, info) { p ->
                    progress = p
                }
            }
            downloading = false
            when (result) {
                is AppUpdateChecker.DownloadResult.Ok -> {
                    updateStatus = "下载完成，正在调起安装…"
                    val ok = AppUpdateChecker.installApk(context, result.file)
                    if (!ok) {
                        updateStatus = "无法调起安装，可打开发布页手动下载"
                        AppUpdateChecker.openReleasePage(context, info.htmlUrl)
                    }
                }
                is AppUpdateChecker.DownloadResult.Failed -> {
                    updateStatus = "下载失败：${result.message}\n可打开浏览器手动下载"
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("使用说明", style = MaterialTheme.typography.headlineSmall, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "镜花水月：从 Wallhaven 等来源自动更换桌面/锁屏壁纸。下列按模块说明含义与推荐配置。",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor.copy(alpha = 0.85f)
        )

        // —— 检查更新 ——
        GlassCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("检查更新", style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(
                    "从 GitHub（kers701/wallpaper_app）Releases 查询最新 APK，有新版本可下载安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.88f)
                )
                Text(
                    updateStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.9f)
                )
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { checkUpdate() },
                        enabled = !checking && !downloading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (checking) "查询中…" else "检查更新")
                    }
                    val info = pendingInfo
                    if (info != null) {
                        OutlinedButton(
                            onClick = { downloadAndInstall(info) },
                            enabled = !checking && !downloading && info.apkUrl != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (downloading) "下载中…" else "下载并安装")
                        }
                    }
                }
                if (pendingInfo != null) {
                    TextButton(
                        onClick = {
                            pendingInfo?.let { AppUpdateChecker.openReleasePage(context, it.htmlUrl) }
                        }
                    ) {
                        Text("在浏览器打开发布页", color = textColor.copy(alpha = 0.85f))
                    }
                }
            }
        }

        GuideSection("一、快速开始（推荐顺序）", """
1. 打开「配置」填写 Wallhaven API Key（可多行轮换）。
2. 设置关键词或开启跃迁模式；纯度、类别、分辨率按喜好选择。
3. 首页打开「自动更换」，设定间隔（建议 15～60 分钟）。
4. 建议开启前台服务或超级服务，并到系统设置里允许自启动、忽略电池优化。
5. 通知渠道保持开启，便于查看休眠原因与快捷操作。
""".trimIndent())

        GuideSection("二、首页", """
• 自动更换：总开关。关闭后服务循环会停止（超级服务开启时逻辑以设置为准）。
• 间隔：两次自动更换的最短间隔，约 5～180 分钟。
• 立即更换：马上执行一轮（仍受省电/黑名单/息屏等规则影响，手动场景下部分规则会放宽）。
• 网络探测：测试 Wallhaven / 兜底 API 连通与延迟，便于排查「一直本地兜底」。
• 跃迁关键词：折叠预览；开启跃迁且列表非空时，搜索优先用跃迁词。
• 使用说明：即本页（含检查更新）。
""".trimIndent())

        GuideSection("三、概览", """
• 上次/下次更换时间：综合 DataStore 与跨进程时钟文件，自动换完后应会更新；可点刷新。
• 桌面/锁屏预览：来自最近缓存文件。
• 服务状态：绿运行 / 红关闭 / 黄异常（独立 :svc 进程）。
• 自动更换板块：与首页类似的状态展示（无开关）；极简模式下方可单独放自动开关。
• 跃迁关键词预览：显示数量与下次使用，点开看全部。
""".trimIndent())

        GuideSection("四、壁纸来源与兜底链", """
顺序概览：
  强制本地？→ 本地目录
  否则 → Wallhaven（关键词/跃迁 + 多 Key）
       → 失败且开网络兜底 → 自定义兜底 API（可多行）
       → 仍失败且开本地兜底 → 本地目录

• Wallhaven Key：官网申请；多行则失败自动轮换。
• 关键词：普通搜索词列表，轮换使用。
• 跃迁模式：从 Wallhaven 标签写入「跃迁列表」（覆盖写）；开启且列表非空时只用跃迁词。
• 纯度 / 类别 / 分辨率：过滤结果；设备自适应会按真实分辨率请求。
• 横竖屏过滤：无过滤 / 仅竖屏 / 仅横屏。
• 裁切填充：类似 Windows「填充」，等比放大铺满（非简单裁一块再拉伸）。
• 兜底 API：支持 {width}{height}；直接图片、单行 URL 或 JSON 均可。
• 本地目录：可填绝对路径；目录空时可用莫奈取色等选项（若已开启）。
""".trimIndent())

        GuideSection("五、桌面与锁屏隔离", """
开启后一轮会触发两次下载与设置：第一张桌面、第二张锁屏，可用不同关键词。
未开启时通常一张图同时用于桌面和锁屏（视系统能力）。
""".trimIndent())

        GuideSection("六、省电与息屏", """
• 省电模式：电量低于阈值且未充电时休眠不换；充电忽略；电量恢复后继续。
• 息屏跳过：灭屏不换，亮屏后再判断是否到期。
• 应用黑名单：名单内应用在前台时休眠（需「使用情况访问」）。通知栏可一键把当前前台加入黑名单。
""".trimIndent())

        GuideSection("七、定位避让与绿色模式", """
• 用途：在指定地点附近自动改纯度策略或仅用本地壁纸。
• 权限：Android 10+ 建议「始终允许」定位，否则后台难以判定是否在区内。
• 触发半径：5～500 米可调；页面可看当前位置与距最近避让点距离。
• 绿色模式：区内纯度在 R13 与「仅 Sketchy」间随机。
• 极限回退：区内只用本地文件换壁纸；离开后恢复进入前状态。
• 通知「定位避让」：把当前位置加入列表（可逆地理命名）。
""".trimIndent())

        GuideSection("八、通知栏", """
文案与标题随状态 / 纯度运行模式变化：

【标题】普通 / 健康 / 心跳 三种模式：
• 镜花水月·普通模式 — 纯度遵循「配置」页用户设置
• 镜花水月·健康模式 — 每次更换在 R8、R13、仅 Sketchy 中随机（忽略用户纯度）
• 镜花水月·心跳模式 — 每次更换在除 R8 外的所有纯度中随机（忽略用户纯度）

通知第三按钮在三种模式间循环：健康模式 → 心跳模式 → 普通模式 → …

【正文休眠】
• 定位休眠 · 已进入（标签）范围
• 应用休眠 · 正在使用（应用名）
• 省电休眠 · 电量与阈值

【操作】
• 定位避让 / 应用黑名单：先弹出确认（确认 / 取消），不会一点就写入
• 立即更换：休眠时可用
• 已移除「停止」按钮（可在应用内关闭自动更换）

黑名单经跨进程文件与主界面同步。
""".trimIndent())

        GuideSection("九、超级服务与保活", """
• 更换服务可跑在独立进程 :svc，UI 划掉后仍可能继续。
• 超级服务：有 Root 可尝试 Root 保活；有无障碍可走无障碍保活；都没有则需先授权。
• 仍建议：忽略电池优化、自启动、后台运行（三星等在「设备维护/电池」里放行）。
• 无法做到与微信完全同级的系统级保活，本应用在权限范围内尽量粘性运行。
""".trimIndent())

        GuideSection("十、配置备份与 PIN", """
• 备份/恢复：未加密或 PIN 解锁后可导出/导入配置；PIN 本身不写入备份。
• PIN 锁定后：密钥、关键词、兜底 API、翻译密钥等敏感项隐藏。
• 云备份（若开启）：WebDAV 等，可横竖屏分离、仅 WiFi。
""".trimIndent())

        GuideSection("十一、配置建议（简表）", """
场景 → 建议
日常随机：关键词若干 + 间隔 30 分钟 + 前台服务
少打扰：间隔 60～120 分钟 + 息屏跳过 + 省电阈值 15～20%
隐私场合：定位避让 + 绿色模式或极限本地
游戏/视频：把对应 App 加入黑名单
网络不稳：开启网络兜底 API + 本地兜底目录
多样性：开启跃迁；Wallhaven 会缓存最大页数再随机页
""".trimIndent())

        GuideSection("十二、常见问题", """
• 一直本地兜底：用首页网络探测看 Wallhaven/兜底延迟；检查 Key、纯度与分辨率是否过严。
• 后台不换：查电池优化、超级服务、是否省电/黑名单/息屏休眠。
• 定位后台无效：定位改为「始终允许」。
• 通知已加黑名单但界面无勾选：已用文件桥同步，请杀进程重进或进设置触发合并。
• 数据过大：缓存超过约 10GB 会清理下载缓存；更换记录保留最近约 77 条。
• 检查更新：需联网访问 GitHub；若本机版本号已高于线上 Release，会提示已是最新。
""".trimIndent())

        Text(
            "版本说明与更新记录见 GitHub Releases / README。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun GuideSection(title: String, body: String) {
    val textColor = LocalUiTextColor.current
    GlassCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.88f)
            )
        }
    }
}
