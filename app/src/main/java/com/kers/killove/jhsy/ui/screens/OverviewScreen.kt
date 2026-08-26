package com.kers.killove.jhsy.ui.screens

import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.kers.killove.jhsy.ui.LocalCardAlpha
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val serviceState by vm.serviceStatus.collectAsState()
    val cacheBytes by vm.cacheBytes.collectAsState()
    val textColor = LocalUiTextColor.current
    val cardAlpha = LocalCardAlpha.current
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val lastStr = if (settings.lastChangeAt > 0L) {
        fmt.format(Date(settings.lastChangeAt))
    } else "尚未更换"

    val nextAt = settings.nextChangeAt()
    val nextStr = when {
        !settings.enabled -> "自动更换未开启"
        nextAt <= 0L -> "—"
        else -> fmt.format(Date(nextAt))
    }

    var homeBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var lockBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(settings.lastChangeAt, serviceState) {
        vm.refreshServiceStatus()
        vm.refreshCacheSize()
        homeBmp = loadWallpaperPreview(context, home = true)
        lockBmp = loadWallpaperPreview(context, home = false)
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
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
            Text("壁纸预览", style = MaterialTheme.typography.titleSmall, color = textColor)
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("极简模式", style = MaterialTheme.typography.titleSmall, color = textColor)
                    Text("开启后仅保留概览页", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.65f))
                }
                Switch(
                    checked = settings.overviewMinimalMode,
                    onCheckedChange = { vm.setOverviewMinimal(it) }
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(cardAlpha: Float, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.2f + cardAlpha * 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun PreviewBox(title: String, bmp: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier) {
    val textColor = LocalUiTextColor.current
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("无预览", color = textColor.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun loadWallpaperPreview(context: android.content.Context, home: Boolean): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val wm = WallpaperManager.getInstance(context)
        val drawable = if (home) {
            try {
                wm.drawable
            } catch (_: SecurityException) {
                wm.peekDrawable()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    wm.getDrawable(WallpaperManager.FLAG_LOCK)
                } catch (_: Exception) {
                    try {
                        wm.drawable
                    } catch (_: Exception) {
                        null
                    }
                }
            } else {
                try {
                    wm.drawable
                } catch (_: Exception) {
                    null
                }
            }
        } ?: return null
        val bmp = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> drawable.toBitmap(
                width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 540).coerceAtMost(720),
                height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 960).coerceAtMost(1280)
            )
        }
        // scale down for UI
        val maxEdge = 480
        val scaled = if (bmp.width > maxEdge || bmp.height > maxEdge) {
            val scale = maxEdge.toFloat() / maxOf(bmp.width, bmp.height)
            android.graphics.Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bmp
        scaled.asImageBitmap()
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
