package com.kers.killove.jhsy.ui.screens

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
import com.kers.killove.jhsy.domain.CardStyle
import com.kers.killove.jhsy.ui.LocalBackdropBitmap
import com.kers.killove.jhsy.ui.LocalCardAlpha
import com.kers.killove.jhsy.ui.LocalCardStyle
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.util.RunLog
import com.kers.killove.jhsy.util.DataSaverBudget
import android.widget.Toast
import com.kers.killove.jhsy.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全局板块卡片：跟随 LocalCardStyle。
 * - 液态玻璃：半透明渐变 + 高光描边 + 流光扫过
 * - 高斯模糊：底层强模糊磨砂层 + 清晰内容（不糊字）
 * - 雾化：柔白弥散
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha = LocalCardAlpha.current
    val style = LocalCardStyle.current
    val shape = RoundedCornerShape(18.dp)
    val contentColor = LocalUiTextColor.current

    when (style) {
        CardStyle.None -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = shape,
                elevation = CardDefaults.cardElevation(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = alpha.coerceIn(0.12f, 0.75f)),
                    contentColor = contentColor
                )
            ) { content() }
        }
        CardStyle.LiquidGlass -> LiquidGlassCard(modifier, shape, alpha, contentColor, content)
        CardStyle.GaussianBlur -> GaussianBlurCard(modifier, shape, alpha, contentColor, content)
        CardStyle.Fog -> FogCard(modifier, shape, alpha, contentColor, content)
    }
}

@Composable
private fun LiquidGlassCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    alpha: Float,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "liquid")
    // 描边高光缓慢游走
    val edgePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge"
    )
    // 波纹 1
    val wave1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    // 波纹 2，相位错开
    val wave2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val bodyBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFEAF5FF).copy(alpha = (0.20f + alpha * 0.26f).coerceIn(0.16f, 0.50f)),
            Color(0xFFB8D4FF).copy(alpha = (0.11f + alpha * 0.20f).coerceIn(0.09f, 0.40f)),
            Color(0xFF6AA8E8).copy(alpha = (0.07f + alpha * 0.16f).coerceIn(0.05f, 0.32f)),
            Color(0xFF152030).copy(alpha = (0.16f + alpha * 0.24f).coerceIn(0.12f, 0.45f))
        ),
        start = Offset.Zero,
        end = Offset(720f, 880f)
    )

    // 渐变高光描边：亮点沿边缓慢移动
    val ang = edgePhase * 2f * PI.toFloat()
    val edgeBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.90f),
            Color(0xFFB8E0FF).copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.12f),
            Color(0xFF7EC8FF).copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.20f),
            Color(0xFFE8F7FF).copy(alpha = 0.75f)
        ),
        start = Offset(400f + cos(ang) * 420f, 400f + sin(ang) * 420f),
        end = Offset(400f + cos(ang + PI.toFloat()) * 420f, 400f + sin(ang + PI.toFloat()) * 420f)
    )

    // 软波纹：径向扩散，低对比，避免光柱感
    fun rippleBrush(phase: Float, cx0: Float, cy0: Float, dx: Float, dy: Float): Brush {
        val cx = cx0 + cos(phase * 2f * PI.toFloat()) * dx
        val cy = cy0 + sin(phase * 2f * PI.toFloat()) * dy
        val radius = 160f + phase * 220f
        return Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f * (1f - phase * 0.55f)),
                Color(0xFFA8D8FF).copy(alpha = 0.10f * (1f - phase * 0.4f)),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = radius.coerceAtLeast(80f)
        )
    }
    val ripple1 = rippleBrush(wave1, 180f, 120f, 120f, 80f)
    val ripple2 = rippleBrush((wave2 + 0.35f) % 1f, 420f, 260f, 100f, 110f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bodyBrush)
            .border(width = 1.6.dp, brush = edgeBrush, shape = shape)
    ) {
        // 顶缘柔和高光（非光柱）
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )
        // 波纹流光层（两层错相位）
        Box(Modifier.matchParentSize().background(ripple1))
        Box(Modifier.matchParentSize().background(ripple2))
        content()
    }
}

@Composable
private fun GaussianBlurCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    alpha: Float,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    val backdrop = LocalBackdropBitmap.current
    val frost = (0.22f + alpha * 0.40f).coerceIn(0.18f, 0.65f)
    Box(modifier = modifier.fillMaxWidth().clip(shape)) {
        // 真·磨砂折中：用当前软件背景图做底层，再强模糊
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(32.dp)
                        else Modifier
                    ),
                contentScale = ContentScale.Crop,
                alpha = 0.95f
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(28.dp)
                        else Modifier
                    )
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = frost * 0.5f),
                                Color.Black.copy(alpha = frost * 0.8f)
                            )
                        )
                    )
            )
        }
        // 磨砂罩：保证文字可读
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0xFF0A0E18).copy(alpha = frost * 0.55f + 0.18f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.22f)
                        )
                    ),
                    shape = shape
                )
        )
        content()
    }
}

@Composable
private fun FogCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    alpha: Float,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    val a = (0.20f + alpha * 0.42f).coerceIn(0.16f, 0.62f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF2F6FC).copy(alpha = a + 0.08f),
                        Color(0xFFC5D0E0).copy(alpha = a * 0.85f),
                        Color(0xFF8A96A8).copy(alpha = a * 0.55f)
                    )
                )
            )
            .border(0.8.dp, Color.White.copy(alpha = 0.35f), shape)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(8.dp) else Modifier
                )
                .background(Color.White.copy(alpha = 0.12f))
        )
        content()
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, onOpenHelp: (() -> Unit)? = null) {
    val settings by vm.settings.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val downloadLabel by vm.downloadLabel.collectAsState()
    val networkProbe by vm.networkProbe.collectAsState()
    val probing by vm.probing.collectAsState()
    val textColor = LocalUiTextColor.current
    val context = LocalContext.current
    var titleClicks by remember { mutableIntStateOf(0) }
    var devMode by remember { mutableStateOf(RunLog.isDeveloperMode(context)) }
    val deviceRes = remember {
        val (w, h) = SystemWallpaperSetter(context).screenSize()
        "${w}×${h}"
    }
    val jumpZh by vm.jumpKeywordsZh.collectAsState()
    var jumpExpanded by remember { mutableStateOf(false) }

    val bridgeLast by vm.bridgeLastChange.collectAsState()
    fun calcRemain(): Int {
        if (!settings.enabled) return -2
        val last = maxOf(settings.lastChangeAt, bridgeLast)
        if (last <= 0L) return -1
        val intervalMs = settings.intervalMinutes.coerceIn(5, 180) * 60_000L
        val remain = last + intervalMs - System.currentTimeMillis()
        if (remain <= 0L) return 0
        return ((remain + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }
    var remainMin by remember { mutableIntStateOf(calcRemain()) }
    LaunchedEffect(settings.enabled, settings.lastChangeAt, settings.intervalMinutes, bridgeLast) {
        while (true) {
            remainMin = calcRemain()
            delay(10_000)
        }
    }
    // 进入首页时拉一次时钟文件
    LaunchedEffect(Unit) {
        vm.syncChangeClockFromBridge()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "镜花水月",
                    style = MaterialTheme.typography.headlineMedium,
                    color = textColor,
                    modifier = Modifier.clickable {
                        titleClicks += 1
                        if (titleClicks >= 7) {
                            titleClicks = 0
                            RunLog.setDeveloperMode(context, true)
                            devMode = true
                            Toast.makeText(context, "已进入开发者模式", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Text(
                    if (devMode) "自动更换壁纸 · 开发者模式"
                    else "自动更换壁纸",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
            if (onOpenHelp != null) {
                OutlinedButton(onClick = onOpenHelp) {
                    Text("使用说明")
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RowSwitch(
                    title = "自动更换",
                    checked = settings.enabled,
                    onChecked = { vm.setEnabled(it) }
                )
                Text("间隔：${settings.intervalMinutes} 分钟", color = textColor)
                if (settings.dataSaverEnabled) {
                    Text(
                        DataSaverBudget.statusLine(context, true, settings.intervalMinutes),
                        color = textColor.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    when {
                        !settings.enabled || remainMin == -2 -> "距下次更换：未开启"
                        remainMin < 0 -> "距下次更换：等待首次更换"
                        remainMin == 0 -> "距下次更换：即将更换 / 已到期"
                        else -> "距下次更换还有 $remainMin 分钟"
                    },
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    com.kers.killove.jhsy.util.AccessMode.line(context, settings),
                    color = textColor
                )
                run {
                    val mode = com.kers.killove.jhsy.util.ProcessBridgePrefs.purityMode(context)
                    val modeLabel = when (mode) {
                        com.kers.killove.jhsy.util.ProcessBridgePrefs.MODE_HEALTH -> "健康模式"
                        com.kers.killove.jhsy.util.ProcessBridgePrefs.MODE_HEARTBEAT -> "心跳模式"
                        else -> "普通模式"
                    }
                    val green = settings.locationInAvoidZone && settings.locationFallbackEnabled
                    Text(
                        "运行模式：" + modeLabel + if (green) " · 绿色模式（定位避让）" else "",
                        color = textColor
                    )
                }

                if (settings.jumpModeEnabled && settings.annihilationModeEnabled) {
                    Text(
                        "正在湮灭 · 第 ${settings.annihilationEpoch} 纪元",
                        color = textColor
                    )
                }
                Text("纯度：${settings.purity.label} · 类别：${settings.categoryMode.label}", color = textColor)
                Text("目标：${settings.target.label}" + if (settings.isolateHomeLock) " · 桌面锁屏隔离" else "", color = textColor)
                Text(
                    buildString {
                        append("关键词：")
                        if (!settings.useKeywords) append("未启用")
                        else if (settings.jumpModeEnabled && settings.jumpKeywords.isNotEmpty())
                            append("跃迁 ${settings.jumpKeywords.size} 个")
                        else if (settings.keywords.isNotEmpty())
                            append("${settings.keywords.size} 个")
                        else append("空")
                        append(" · 密钥：${settings.apiKeys.size} 个")
                    },
                    color = textColor
                )
                val orient = settings.orientationFilter.label
                Text(
                    buildString {
                        append("分辨率：${settings.resolutionMode.label}")
                        if (settings.resolutionMode == com.kers.killove.jhsy.domain.ResolutionMode.Device) {
                            append("（设备 $deviceRes）")
                        }
                        append(" · 方向：$orient")
                    },
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("铺满：${settings.fitMode.label}" + if (settings.isolateHomeLock) " · 桌面锁屏隔离" else "", color = textColor, style = MaterialTheme.typography.bodySmall)
                if (settings.powerSaveEnabled) {
                    Text("省电：低于 ${settings.powerSaveBatteryThreshold}% 休眠（充电忽略）", color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GlassCard {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { jumpExpanded = !jumpExpanded },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("跃迁关键词", style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(
                    when {
                        !settings.jumpModeEnabled -> "跃迁模式：关闭"
                        settings.jumpKeywords.isEmpty() -> "跃迁模式：开启 · 列表为空"
                        else -> "跃迁模式：开启 · 共 ${settings.jumpKeywords.size} 个" +
                            if (jumpExpanded) " · 点击收起" else " · 点击展开"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f)
                )
                if (settings.jumpKeywords.isNotEmpty()) {
                    val list = settings.jumpKeywords
                    val idx = settings.jumpKeywordIndex.mod(list.size)
                    val next = list[idx]
                    val nextZh = jumpZh[next]
                    Text(
                        "下次将用：" + if (nextZh.isNullOrBlank()) next else "$next（$nextZh）",
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (jumpExpanded) {
                        val shown = list.joinToString("、") { w ->
                            val zh = jumpZh[w]
                            if (zh.isNullOrBlank()) w else "$w($zh)"
                        }
                        Text(shown, color = textColor)
                    }
                }
            }
        }

        if (settings.jumpModeEnabled && settings.annihilationModeEnabled) {
            var anniExpanded by remember { mutableStateOf(false) }
            val anniList = remember(settings.annihilationEpoch, settings.lastChangeAt) {
                com.kers.killove.jhsy.util.AnnihilationStore.list(context)
            }
            GlassCard {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clickable { anniExpanded = !anniExpanded },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("湮灭关键词", style = MaterialTheme.typography.titleMedium, color = textColor)
                    Text(
                        when {
                            anniList.isEmpty() -> "正在湮灭 · 第 ${settings.annihilationEpoch} 纪元 · 缓存为空"
                            else -> "正在湮灭 · 第 ${settings.annihilationEpoch} 纪元 · 共 ${anniList.size} 个" +
                                if (anniExpanded) " · 点击收起" else " · 点击展开"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.85f)
                    )
                    if (anniList.isNotEmpty() && anniExpanded) {
                        val shown = anniList.joinToString("、") { w ->
                            val zh = jumpZh[w]
                            if (zh.isNullOrBlank()) w else "$w($zh)"
                        }
                        Text(shown, color = textColor)
                    }
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网络检测", style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(networkProbe, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.9f))
                if (probing) CircularProgressIndicator(color = textColor)
                OutlinedButton(
                    onClick = { vm.testNetwork() },
                    enabled = !probing && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (probing) "检测中…" else "测试网络延迟")
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("状态", style = MaterialTheme.typography.titleMedium, color = textColor)
                Spacer(Modifier.height(8.dp))
                if (busy && downloadProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    if (downloadLabel.isNotBlank()) {
                        Text(
                            downloadLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                } else if (busy) {
                    CircularProgressIndicator(color = textColor)
                    Spacer(Modifier.height(8.dp))
                }
                Text(status, color = textColor)
            }
        }

        Button(
            onClick = { vm.changeNow() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "更换中…" else "立即更换一张")
        }

        if (devMode) {
            var runLogOn by remember { mutableStateOf(RunLog.isLogEnabled(context)) }
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("开发者选项", style = MaterialTheme.typography.titleSmall, color = textColor)
                    RowSwitch(
                        title = "运行日志",
                        checked = runLogOn,
                        onChecked = {
                            runLogOn = it
                            RunLog.setLogEnabled(context, it)
                            Toast.makeText(
                                context,
                                if (it) "日志写入 ${RunLog.logFile(context).absolutePath}" else "已关闭运行日志",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    Text(
                        "路径：${RunLog.logFile(context).absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    OutlinedButton(onClick = {
                        RunLog.clear(context)
                        Toast.makeText(context, "已清空 run.log", Toast.LENGTH_SHORT).show()
                    }) { Text("清空日志") }
                    OutlinedButton(onClick = {
                        RunLog.setDeveloperMode(context, false)
                        runLogOn = false
                        devMode = false
                        Toast.makeText(context, "已退出开发者模式", Toast.LENGTH_SHORT).show()
                    }) { Text("退出开发者模式") }
                }
            }
        }
    }
}

@Composable
fun RowSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    val textColor = LocalUiTextColor.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            color = textColor.copy(alpha = if (enabled) 1f else 0.45f)
        )
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}


@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val textColor = LocalUiTextColor.current
    GlassCard {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = textColor, modifier = Modifier.weight(1f))
                Text(
                    if (expanded) "收起 ▲" else "展开 ▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.65f)
                )
            }
            if (expanded) {
                content()
            }
        }
    }
}
