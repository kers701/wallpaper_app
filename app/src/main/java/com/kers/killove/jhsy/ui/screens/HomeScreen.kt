package com.kers.killove.jhsy.ui.screens

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.kers.killove.jhsy.ui.LocalCardAlpha
import com.kers.killove.jhsy.ui.LocalCardStyle
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import kotlinx.coroutines.delay

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
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    // 流光从左上扫到右下
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.45f),
            Color.Cyan.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.40f),
            Color.Transparent
        ),
        start = Offset(sweep * 900f - 280f, sweep * 500f - 160f),
        end = Offset(sweep * 900f + 180f, sweep * 500f + 120f)
    )
    val bodyBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE8F4FF).copy(alpha = (0.22f + alpha * 0.28f).coerceIn(0.18f, 0.55f)),
            Color(0xFFB8D4FF).copy(alpha = (0.12f + alpha * 0.22f).coerceIn(0.10f, 0.42f)),
            Color(0xFF7EB6FF).copy(alpha = (0.08f + alpha * 0.18f).coerceIn(0.06f, 0.35f)),
            Color(0xFF1A2A40).copy(alpha = (0.18f + alpha * 0.25f).coerceIn(0.12f, 0.48f))
        ),
        start = Offset.Zero,
        end = Offset(800f, 900f)
    )
    val edgeBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.75f),
            Color(0xFFA8D8FF).copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.15f),
            Color(0xFF6EC8FF).copy(alpha = 0.45f),
            Color.White.copy(alpha = 0.65f)
        )
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bodyBrush)
            .border(width = 1.4.dp, brush = edgeBrush, shape = shape)
    ) {
        // 顶部高光条（玻璃折射感）
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        // 流光层
        Box(Modifier.matchParentSize().background(shimmerBrush))
        // 内容不被糊
        Box(Modifier.padding(0.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalUiTextColor provides contentColor
            ) {
                content()
            }
        }
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
    val frost = (0.28f + alpha * 0.45f).coerceIn(0.25f, 0.72f)
    Box(modifier = modifier.fillMaxWidth().clip(shape)) {
        // 底层：大半径模糊的磨砂块（只糊背景块，不糊上层文字）
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(28.dp)
                    } else Modifier
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = frost * 0.55f),
                            Color(0xFFD0D8E8).copy(alpha = frost * 0.4f),
                            Color.Black.copy(alpha = frost * 0.85f)
                        )
                    )
                )
        )
        // 中层：再叠一层半透明磨砂
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0xFF0A0E18).copy(alpha = (0.35f + alpha * 0.35f).coerceIn(0.3f, 0.7f)))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.18f)
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
fun HomeScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val networkProbe by vm.networkProbe.collectAsState()
    val probing by vm.probing.collectAsState()
    val textColor = LocalUiTextColor.current
    val context = LocalContext.current
    val deviceRes = remember {
        val (w, h) = SystemWallpaperSetter(context).screenSize()
        "${w}×${h}"
    }
    val jumpZh by vm.jumpKeywordsZh.collectAsState()

    var remainMin by remember { mutableIntStateOf(settings.minutesUntilNext()) }
    LaunchedEffect(settings.enabled, settings.lastChangeAt, settings.intervalMinutes) {
        while (true) {
            remainMin = settings.minutesUntilNext()
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("镜花水月", style = MaterialTheme.typography.headlineMedium, color = textColor)
        Text(
            "镜花水月 · 自动更换壁纸",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor.copy(alpha = 0.8f)
        )

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RowSwitch(
                    title = "自动更换",
                    checked = settings.enabled,
                    onChecked = { vm.setEnabled(it) }
                )
                Text("间隔：${settings.intervalMinutes} 分钟", color = textColor)
                Text(
                    when {
                        !settings.enabled -> "距下次更换：未开启"
                        remainMin < 0 -> "距下次更换：等待首次更换"
                        remainMin == 0 -> "距下次更换：即将更换 / 已到期"
                        else -> "距下次更换还有 $remainMin 分钟"
                    },
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall
                )
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("跃迁关键词", style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(
                    when {
                        !settings.jumpModeEnabled -> "跃迁模式：关闭"
                        settings.jumpKeywords.isEmpty() -> "跃迁模式：开启 · 列表为空"
                        else -> "跃迁模式：开启 · 共 ${settings.jumpKeywords.size} 个"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f)
                )
                if (settings.jumpKeywords.isNotEmpty()) {
                    val shown = settings.jumpKeywords.joinToString("、") { w ->
                        val zh = jumpZh[w]
                        if (zh.isNullOrBlank()) w else "$w($zh)"
                    }
                    Text(shown, color = textColor)
                    val list = settings.jumpKeywords
                    val idx = settings.jumpKeywordIndex.mod(list.size)
                    val next = list[idx]
                    val nextZh = jumpZh[next]
                    Text(
                        "下次将用：" + if (nextZh.isNullOrBlank()) next else "$next（$nextZh）",
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                if (busy) {
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
    }
}

@Composable
fun RowSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val textColor = LocalUiTextColor.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), color = textColor)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
