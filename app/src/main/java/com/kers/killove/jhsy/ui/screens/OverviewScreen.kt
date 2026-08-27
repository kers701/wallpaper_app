package com.kers.killove.jhsy.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kers.killove.jhsy.data.local.WallpaperEntity
import com.kers.killove.jhsy.ui.LocalCardAlpha
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val serviceState by vm.serviceStatus.collectAsState()
    val cacheBytes by vm.cacheBytes.collectAsState()
    val recent by vm.recent.collectAsState()
    val textColor = LocalUiTextColor.current
    val cardAlpha = LocalCardAlpha.current
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val lastStr = if (settings.lastChangeAt > 0L) fmt.format(Date(settings.lastChangeAt)) else "尚未更换"
    val nextAt = settings.nextChangeAt()
    val nextStr = when {
        !settings.enabled -> "自动更换未开启"
        nextAt <= 0L -> "—"
        else -> fmt.format(Date(nextAt))
    }

    var homeBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var lockBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    fun reloadMeta() {
        vm.refreshServiceStatus()
        vm.refreshCacheSize()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reloadMeta() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(settings.lastChangeAt, settings.changeCount, recent) {
        reloadMeta()
        val (hPath, lPath) = pickHomeLockPaths(recent)
        homeBmp = withContext(Dispatchers.IO) { decodeFileScaled(hPath) }
        lockBmp = withContext(Dispatchers.IO) { decodeFileScaled(lPath) }
    }

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.size(8.dp))
            Text("镜花水月 · 概览", style = MaterialTheme.typography.headlineSmall, color = textColor)
            Spacer(Modifier.weight(1f))
            Text(statusLabel, color = statusColor, style = MaterialTheme.typography.bodyMedium)
        }

        OverviewCard(cardAlpha) {
            Text("上次更换", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
            Text(lastStr, style = MaterialTheme.typography.titleMedium, color = textColor)
            Spacer(Modifier.height(8.dp))
            Text("下次更换", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
            Text(nextStr, style = MaterialTheme.typography.titleMedium, color = textColor)
        }

        OverviewCard(cardAlpha) {
            Text("壁纸预览（缓存）", style = MaterialTheme.typography.titleSmall, color = textColor)
            Text("桌面 / 锁屏分别取最近对应目标的缓存图", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewBox("桌面", homeBmp, Modifier.weight(1f))
                PreviewBox("锁屏", lockBmp, Modifier.weight(1f))
            }
        }

        OverviewCard(cardAlpha) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("已更换", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
                    Text("${settings.changeCount} 次", style = MaterialTheme.typography.titleLarge, color = textColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("缓存占用", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
                    Text(formatBytes(cacheBytes), style = MaterialTheme.typography.titleMedium, color = textColor)
                }
            }
        }

        OverviewCard(cardAlpha) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("极简模式", style = MaterialTheme.typography.titleSmall, color = textColor)
                    Text("开启后仅保留概览页", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.65f))
                }
                Switch(checked = settings.overviewMinimalMode, onCheckedChange = { vm.setOverviewMinimal(it) })
            }
        }
    }
}

/** 从记录中分别取最新桌面 / 锁屏缓存路径（文件名后缀 _home / _lock / _Home / _Lock） */
private fun pickHomeLockPaths(recent: List<WallpaperEntity>): Pair<String?, String?> {
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
private fun OverviewCard(cardAlpha: Float, content: @Composable () -> Unit) {
    // 走全局 GlassCard，使「板块美化」在概览页生效
    GlassCard {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { content() }
    }
}

@Composable
private fun PreviewBox(title: String, bmp: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier) {
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

private fun decodeFileScaled(path: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        val f = File(path)
        if (!f.exists() || f.length() < 32) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (maxSide / sample > 480) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        bmp.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
