package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import android.os.Build
import com.kers.killove.jhsy.domain.CardStyle
import com.kers.killove.jhsy.ui.LocalCardStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalCardAlpha
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
import com.kers.killove.jhsy.ui.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha = LocalCardAlpha.current
    val style = LocalCardStyle.current
    val shape = RoundedCornerShape(16.dp)
    val (container, border, elev) = when (style) {
        CardStyle.None -> Triple(
            Color.Black.copy(alpha = alpha),
            null as BorderStroke?,
            0.dp
        )
        CardStyle.LiquidGlass -> Triple(
            Color(0xFFB8D4FF).copy(alpha = (0.10f + alpha * 0.35f).coerceIn(0.08f, 0.55f)),
            BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
            2.dp
        )
        CardStyle.GaussianBlur -> Triple(
            Color.Black.copy(alpha = (alpha * 0.75f + 0.12f).coerceIn(0.15f, 0.65f)),
            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            0.dp
        )
        CardStyle.Fog -> Triple(
            Color(0xFFE8EEF5).copy(alpha = (0.14f + alpha * 0.40f).coerceIn(0.12f, 0.60f)),
            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)),
            0.dp
        )
    }
    val cardMod = modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (style == CardStyle.GaussianBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(12.dp)
            } else Modifier
        )
    Card(
        modifier = if (style == CardStyle.GaussianBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // blur on whole card would blur content; apply soft look via color only on older APIs
            modifier.fillMaxWidth()
        } else {
            modifier.fillMaxWidth()
        },
        shape = shape,
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = elev),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = LocalUiTextColor.current
        )
    ) {
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
