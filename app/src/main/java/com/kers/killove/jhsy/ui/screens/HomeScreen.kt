package com.kers.killove.jhsy.ui.screens

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.kers.killove.jhsy.data.local.NextWallpaperStore
import com.kers.killove.jhsy.data.local.OverviewCacheStore
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.kers.killove.jhsy.data.local.WallpaperEntity
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kers.killove.jhsy.util.PastLifeStore
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
fun HomeScreen(
    vm: MainViewModel,
    onOpenHelp: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenDestinyDream: (() -> Unit)? = null
) {
    var confirmClearRunLog by remember { mutableStateOf(false) }

    val settings by vm.settings.collectAsState()
    val serviceState by vm.serviceStatus.collectAsState()
    val cacheBytes by vm.cacheBytes.collectAsState()
    val recent by vm.recent.collectAsState()
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
    val anniZh by vm.annihilationZh.collectAsState()
    val illuZh by vm.illusionZh.collectAsState()
    val liveJump by vm.liveJumpKeywords.collectAsState()
    val liveJumpIdx by vm.liveJumpIndex.collectAsState()
    val liveNatRet by vm.liveNaturalRetained.collectAsState()
    val liveNatRec by vm.liveNaturalRecommended.collectAsState()
    var jumpExpanded by remember { mutableStateOf(false) }
    val jumpList = if (liveJump.isNotEmpty()) liveJump else settings.jumpKeywords
    val jumpIdx = if (liveJump.isNotEmpty()) liveJumpIdx else settings.jumpKeywordIndex
    val natRet = if (liveNatRet.isNotEmpty()) liveNatRet else settings.naturalLawRetainedKeywords
    val natRec = if (liveNatRec.isNotEmpty()) liveNatRec else settings.naturalLawRecommendedKeywords

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
    // 核心页：对齐原概览的刷新节奏（时钟 / 服务状态 / 缓存 / 预览）
    var coreRefreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                vm.syncChangeClockFromBridge()
                vm.refreshServiceStatus()
                vm.refreshCacheSize()
                coreRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        vm.syncChangeClockFromBridge()
        vm.refreshServiceStatus()
        vm.refreshCacheSize()
        vm.promoteWeeklyHotKeywords()
        while (true) {
            kotlinx.coroutines.delay(5_000)
            vm.syncChangeClockFromBridge()
            vm.refreshServiceStatus()
            vm.refreshCacheSize()
            coreRefreshTick++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val statusColor = when (serviceState) {
            MainViewModel.ServiceStatus.Running -> Color(0xFF4CAF50)
            MainViewModel.ServiceStatus.Stopped -> Color(0xFFF44336)
            MainViewModel.ServiceStatus.Abnormal -> Color(0xFFFFC107)
        }
        val statusLabel = when (serviceState) {
            MainViewModel.ServiceStatus.Running -> "运行中"
            MainViewModel.ServiceStatus.Stopped -> "已关闭"
            MainViewModel.ServiceStatus.Abnormal -> "异常"
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "MiroFlweat · 核心",
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
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.size(6.dp))
            Text(statusLabel, color = statusColor, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onOpenHelp != null) {
                ThemeOutlinedButton(
                    onClick = onOpenHelp,
                    modifier = Modifier.weight(1f)
                ) { Text("使用说明") }
            }
            if (onOpenHistory != null) {
                ThemeOutlinedButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f)
                ) { Text("更换记录") }
            }
        }

        if (settings.jumpModeEnabled && onOpenDestinyDream != null) {
            ThemeOutlinedButton(
                onClick = { onOpenDestinyDream.invoke() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("造化前尘&浮生一梦") }
        }



        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val fmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
                val effectiveLast = maxOf(settings.lastChangeAt, bridgeLast)
                val lastStr = if (effectiveLast > 0L) fmt.format(java.util.Date(effectiveLast)) else "尚未更换"
                val intervalMs = settings.intervalMinutes.coerceIn(5, 180) * 60_000L
                val nextAt = if (effectiveLast > 0L) effectiveLast + intervalMs else 0L
                val nextStr = when {
                    !settings.enabled -> "自动更换未开启"
                    nextAt <= 0L -> "—"
                    else -> fmt.format(java.util.Date(nextAt))
                }
                Text("上次更换：$lastStr", color = textColor)
                Text("下次更换：$nextStr", color = textColor)
                ThemeOutlinedButton(
                    onClick = { vm.refreshOverview() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("刷新时间与状态") }

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
                // 预下载状态：随 lastChangeAt / 定时 tick 刷新
                val prefetchLabel = remember(settings.lastChangeAt, settings.target, settings.isolateHomeLock, remainMin, coreRefreshTick, bridgeLast) {
                    vm.prefetchStatusLabel()
                }
                val prefetchReady = remember(settings.lastChangeAt, settings.target, settings.isolateHomeLock, remainMin, coreRefreshTick, bridgeLast) {
                    vm.isPrefetchReady()
                }
                Text(
                    "壁纸预下载：$prefetchLabel",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (prefetchReady && settings.enabled) {
                    ThemeOutlinedButton(
                        onClick = { vm.zeroWaitTimer() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("等待时间归零")
                    }
                }
                Text(
                    com.kers.killove.jhsy.util.AccessMode.line(context, settings),
                    color = textColor
                )
                run {
                    val destiny = com.kers.killove.jhsy.util.DestinyHelper.resolveActive(settings)
                    val mode = com.kers.killove.jhsy.util.ProcessBridgePrefs.purityMode(context)
                    val modeLabel = when {
                        destiny != null -> "命运模式"
                        mode == com.kers.killove.jhsy.util.ProcessBridgePrefs.MODE_HEALTH -> "健康模式"
                        mode == com.kers.killove.jhsy.util.ProcessBridgePrefs.MODE_HEARTBEAT -> "心跳模式"
                        else -> "普通模式"
                    }
                    val green = settings.locationAvoidEnabled &&
                        settings.locationFallbackEnabled &&
                        settings.locationInAvoidZone &&
                        settings.avoidanceLocations().isNotEmpty()
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
                if (settings.jumpModeEnabled && settings.annihilationModeEnabled && settings.illusionModeEnabled) {
                    Text("虚妄模式：开启", color = textColor)
                }
                Text(
                    run {
                        val mode = com.kers.killove.jhsy.util.ProcessBridgePrefs.purityMode(context)
                        val green = settings.locationAvoidEnabled &&
                            settings.locationFallbackEnabled &&
                            settings.locationInAvoidZone &&
                            settings.avoidanceLocations().isNotEmpty()
                        val eff = com.kers.killove.jhsy.util.DestinyHelper.effectiveDisplayPurity(
                            settings, mode, green
                        )
                        "纯度：${eff.label} · 类别：${settings.categoryMode.label}"
                    },
                    color = textColor
                )
                Text("目标：${settings.target.label}" + if (settings.isolateHomeLock) " · 桌面锁屏隔离" else "", color = textColor)
                Text(
                    buildString {
                        append("关键词：")
                        if (!settings.useKeywords) append("未启用")
                        else if (settings.jumpModeEnabled && jumpList.isNotEmpty())
                            append("跃迁 ${jumpList.size} 个")
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

        // —— 壁纸预览：优先预下载槽位 → 概览缓存 → 更换记录 ——
        if (settings.showWallpaperPreview) {
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("壁纸预览", style = MaterialTheme.typography.titleMedium, color = textColor)
                    var homeBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    var lockBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    LaunchedEffect(
                        settings.lastChangeAt,
                        settings.changeCount,
                        settings.showWallpaperPreview,
                        settings.target,
                        settings.isolateHomeLock,
                        coreRefreshTick,
                        bridgeLast
                    ) {
                        val (hPath, lPath) = withContext(Dispatchers.IO) {
                            val next = NextWallpaperStore(context)
                            val nextPaths = if (settings.isolateHomeLock &&
                                settings.target == com.kers.killove.jhsy.domain.WallpaperTarget.Both
                            ) {
                                val home = next.get(com.kers.killove.jhsy.domain.WallpaperTarget.Home)
                                    ?.takeIf { it.isReady() }?.path
                                val lock = next.get(com.kers.killove.jhsy.domain.WallpaperTarget.Lock)
                                    ?.takeIf { it.isReady() }?.path
                                if (home != null && lock != null) home to lock else null
                            } else {
                                next.get(settings.target)?.takeIf { it.isReady() }?.path?.let { it to it }
                            }
                            val current = OverviewCacheStore.paths(context)
                            val fromRecent = corePickHomeLockPaths(recent)
                            when {
                                nextPaths != null -> nextPaths
                                !current.first.isNullOrBlank() || !current.second.isNullOrBlank() -> current
                                else -> fromRecent
                            }
                        }
                        homeBmp = withContext(Dispatchers.IO) { coreDecodeFileScaled(hPath) }
                        lockBmp = withContext(Dispatchers.IO) { coreDecodeFileScaled(lPath) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CorePreviewBox("桌面", homeBmp, Modifier.weight(1f))
                        CorePreviewBox("锁屏", lockBmp, Modifier.weight(1f))
                    }
                    Text(
                        "优先显示预下载成功的下一张；未预下载时显示当前缓存",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.55f)
                    )
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("已更换", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
                        Text("${settings.changeCount} 次", style = MaterialTheme.typography.titleLarge, color = textColor)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("缓存占用", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
                        Text(
                            when {
                                cacheBytes < 1024 -> "$cacheBytes B"
                                cacheBytes < 1024 * 1024 -> "%.1f KB".format(cacheBytes / 1024.0)
                                else -> "%.1f MB".format(cacheBytes / (1024.0 * 1024.0))
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor
                        )
                    }
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val _weekTick = coreRefreshTick // 触发重组以刷新本周统计
                val weekMs = 7L * 24 * 60 * 60 * 1000
                val now = System.currentTimeMillis()
                val weekList = recent.filter { now - it.setAt <= weekMs }
                val purityCount = weekList.groupingBy { it.purity.ifBlank { "?" } }.eachCount()
                val topKw = weekList.map { it.keyword.trim() }.filter { it.isNotEmpty() }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
                val autoCnt = weekList.count { it.triggerType.equals("auto", true) }
                val manualCnt = weekList.size - autoCnt
                Text("本周统计", style = MaterialTheme.typography.titleSmall, color = textColor)
                Text("更换 ${weekList.size} 次 · 累计 ${settings.changeCount} 次", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text("自动 $autoCnt · 手动/其他 $manualCnt", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.75f))
                if (purityCount.isNotEmpty()) {
                    Text(
                        "纯度 " + purityCount.entries.joinToString(" · ") { "${it.key} ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                }
                if (topKw.isNotEmpty()) {
                    Text(
                        "热词 " + topKw.joinToString(" · ") { "${it.key}×${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                }
                if (weekList.isEmpty()) {
                    Text("近 7 日暂无更换记录", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.55f))
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val mb = cacheBytes / (1024.0 * 1024.0)
                Text("缓存占用：${"%.1f".format(mb)} MB", color = textColor)
                RowSwitch(
                    title = "极简模式",
                    checked = settings.overviewMinimalMode,
                    onChecked = { on -> vm.setOverviewMinimal(on) }
                )
                Text(
                    if (settings.overviewMinimalMode) "开启后隐藏底部导航，仅保留核心页"
                    else "关闭 · 显示底部导航",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.65f)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("呆瓜模式", style = MaterialTheme.typography.titleSmall, color = textColor)
                        Text(
                            if (settings.brainDumpMode)
                                "已开启 · 间隔/排序/缓存/跃迁链/兜底/命运等已接管，关闭后还原"
                            else
                                "关闭 · 开启后临时接管多项设置，关闭可还原原配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.65f)
                        )
                    }
                    ThemeSwitch(
                        checked = settings.brainDumpMode,
                        onCheckedChange = { vm.setBrainDumpMode(it) }
                    )
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网络检测", style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(networkProbe, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.9f))
                if (probing) CircularProgressIndicator(color = textColor)
                ThemeOutlinedButton(
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

        ThemeButton(
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
                    ThemeOutlinedButton(onClick = { confirmClearRunLog = true }) { Text("清空日志") }
                    ThemeOutlinedButton(onClick = {
                        RunLog.setDeveloperMode(context, false)
                        runLogOn = false
                        devMode = false
                        Toast.makeText(context, "已退出开发者模式", Toast.LENGTH_SHORT).show()
                    }) { Text("退出开发者模式") }
                }
            }
        }
    }

    if (confirmClearRunLog) {
        AlertDialog(
            onDismissRequest = { confirmClearRunLog = false },
            title = { Text("清空运行日志？") },
            text = { Text("将清空 run.log，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    RunLog.clear(context)
                    Toast.makeText(context, "已清空 run.log", Toast.LENGTH_SHORT).show()
                    confirmClearRunLog = false
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearRunLog = false }) { Text("取消") }
            }
        )
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
        ThemeSwitch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
fun ThemeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val textColor = com.kers.killove.jhsy.ui.LocalUiControlTextColor.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = textColor,
            checkedTrackColor = textColor.copy(alpha = 0.45f),
            checkedBorderColor = textColor.copy(alpha = 0.55f),
            uncheckedThumbColor = textColor.copy(alpha = 0.55f),
            uncheckedTrackColor = textColor.copy(alpha = 0.12f),
            uncheckedBorderColor = textColor.copy(alpha = 0.35f),
            disabledCheckedThumbColor = textColor.copy(alpha = 0.25f),
            disabledCheckedTrackColor = textColor.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = textColor.copy(alpha = 0.2f),
            disabledUncheckedTrackColor = textColor.copy(alpha = 0.08f)
        )
    )
}

@Composable
fun ThemeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val textColor = LocalUiTextColor.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = textColor,
            activeTrackColor = textColor.copy(alpha = 0.75f),
            inactiveTrackColor = textColor.copy(alpha = 0.2f),
            activeTickColor = textColor.copy(alpha = 0.5f),
            inactiveTickColor = textColor.copy(alpha = 0.2f),
            disabledThumbColor = textColor.copy(alpha = 0.25f),
            disabledActiveTrackColor = textColor.copy(alpha = 0.2f),
            disabledInactiveTrackColor = textColor.copy(alpha = 0.1f)
        )
    )
}



/** 跃迁列表上的「上次 / 本次 / 下次」相对当前索引（索引指向下次将用）。 */
private fun jumpTimelineWords(list: List<String>, index: Int, stepsPerCycle: Int): Triple<String, String, String> {
    if (list.isEmpty()) return Triple("", "", "")
    val n = list.size
    val idx = index.mod(n)
    val next = list[idx]
    val curr = list[(idx - stepsPerCycle).mod(n)]
    val prev = list[(idx - 2 * stepsPerCycle).mod(n)]
    return Triple(prev, curr, next)
}

@Composable
private fun JumpWordLine(
    label: String,
    word: String,
    zhMap: Map<String, String>,
    textColor: androidx.compose.ui.graphics.Color
) {
    val zh = zhMap[word]
    val body = when {
        word.isBlank() -> "—"
        zh.isNullOrBlank() -> word
        else -> "$word（$zh）"
    }
    Text(
        "$label：$body",
        color = textColor,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun JumpKeywordsTimeline(
    list: List<String>,
    index: Int,
    isolate: Boolean,
    zhMap: Map<String, String>,
    textColor: androidx.compose.ui.graphics.Color,
    pastLife: PastLifeStore.State? = null
) {
    // 仅显示下次用词（桌面/锁屏隔离时分栏各显下次）
    if (isolate) {
        val split = pastLife?.takeIf { it.splitMode }
        val homeT = split?.home
        val lockT = split?.lock
        val useStore = homeT != null && (
            homeT.nextKw.isNotBlank() || homeT.currKw.isNotBlank() ||
                (lockT?.nextKw?.isNotBlank() == true) || (lockT?.currKw?.isNotBlank() == true)
        )
        val n = list.size
        val homeNext: String
        val lockNext: String
        if (useStore) {
            fun validNext(stored: String, fallback: String): String {
                val s = stored.trim()
                if (s.isNotEmpty() && (n == 0 || list.any { it.equals(s, ignoreCase = true) })) return s
                return fallback
            }
            val fbHome = if (n > 0) list[index.mod(n)] else ""
            val fbLock = if (n > 0) list[(index + 1).mod(n)] else ""
            homeNext = validNext(homeT!!.nextKw, fbHome)
            lockNext = validNext(lockT?.nextKw.orEmpty(), fbLock)
        } else if (n > 0) {
            val idx = index.mod(n)
            homeNext = list[idx]
            lockNext = list[(idx + 1).mod(n)]
        } else {
            homeNext = ""
            lockNext = ""
        }
        if (homeNext.isBlank() && lockNext.isBlank()) return
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("桌面", style = MaterialTheme.typography.titleSmall, color = textColor)
                JumpWordLine("下次用词", homeNext, zhMap, textColor)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("锁屏", style = MaterialTheme.typography.titleSmall, color = textColor)
                JumpWordLine("下次用词", lockNext, zhMap, textColor)
            }
        }
        return
    }

    val fromStore = pastLife?.takeIf {
        it.nextKw.isNotBlank() || it.currKw.isNotBlank()
    }
    val next: String = when {
        fromStore != null && fromStore.nextKw.isNotBlank() -> fromStore.nextKw
        list.isNotEmpty() -> jumpTimelineWords(list, index, stepsPerCycle = 1).third
        else -> return
    }
    if (next.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        JumpWordLine("下次用词", next, zhMap, textColor)
    }
}



@Composable
fun ThemeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val textColor = com.kers.killove.jhsy.ui.LocalUiControlTextColor.current
    val alpha = LocalCardAlpha.current
    val style = LocalCardStyle.current
    val borderAlpha = when (style) {
        CardStyle.LiquidGlass -> 0.45f
        CardStyle.GaussianBlur -> 0.35f
        CardStyle.Fog -> 0.30f
        else -> 0.40f
    }
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = textColor,
            containerColor = Color.White.copy(alpha = (alpha * 0.18f).coerceIn(0.06f, 0.28f)),
            disabledContentColor = textColor.copy(alpha = 0.38f),
            disabledContainerColor = Color.White.copy(alpha = 0.04f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            textColor.copy(alpha = borderAlpha)
        ),
        content = content
    )
}

@Composable
fun ThemeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val textColor = com.kers.killove.jhsy.ui.LocalUiControlTextColor.current
    val alpha = LocalCardAlpha.current
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = textColor.copy(alpha = (0.22f + alpha * 0.25f).coerceIn(0.18f, 0.45f)),
            contentColor = textColor,
            disabledContainerColor = textColor.copy(alpha = 0.08f),
            disabledContentColor = textColor.copy(alpha = 0.35f)
        ),
        content = content
    )
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


private fun corePickHomeLockPaths(recent: List<WallpaperEntity>): Pair<String?, String?> {
    fun match(e: WallpaperEntity, keys: List<String>): Boolean {
        val p = e.path.lowercase()
        val id = e.id.lowercase()
        return keys.any { p.contains(it) || id.endsWith(it.trim('_')) || id.contains(it) }
    }
    val home = recent.firstOrNull { match(it, listOf("_home", "_Home", "/home", "home.jpg")) }
        ?: recent.firstOrNull { match(it, listOf("_both", "both")) }
        ?: recent.firstOrNull()
    val lock = recent.firstOrNull { match(it, listOf("_lock", "_Lock", "lock.jpg")) }
        ?: recent.firstOrNull { match(it, listOf("_both", "both")) }
        ?: recent.drop(1).firstOrNull()
        ?: home
    return home?.path to lock?.path
}

@Composable
private fun CorePreviewBox(title: String, bmp: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier) {
    val textColor = LocalUiTextColor.current
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("无缓存", color = textColor.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun coreDecodeFileScaled(path: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        val f = File(path)
        if (!f.exists() || f.length() < 32) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (maxSide / sample > 1280) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
