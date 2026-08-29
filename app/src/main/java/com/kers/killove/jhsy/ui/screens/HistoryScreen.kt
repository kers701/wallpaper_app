package com.kers.killove.jhsy.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val recent by vm.recent.collectAsState()
    val fmt = rememberDateFormat()
    val textColor = LocalUiTextColor.current
    var previewPath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("最近记录", style = MaterialTheme.typography.headlineSmall, color = textColor)
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            if (uri != null) vm.exportHistoryToUri(uri)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                exportLauncher.launch("jhsy_history_${System.currentTimeMillis()}.csv")
            }) {
                Text("导出记录")
            }
            OutlinedButton(onClick = { vm.clearLogs() }) {
                Text("清空记录")
            }
            OutlinedButton(onClick = { vm.clearWallpaperCache() }) {
                Text("清空缓存")
            }
        }
        if (recent.isEmpty()) {
            Text(
                "暂无记录，点击首页「立即更换」试一次",
                modifier = Modifier.padding(top = 24.dp),
                color = textColor.copy(alpha = 0.7f)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                items(recent, key = { it.id + it.setAt }) { item ->
                    val cacheExists = remember(item.path) {
                        item.path.isNotBlank() && File(item.path).isFile
                    }
                    GlassCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ID: ${item.id}", style = MaterialTheme.typography.titleSmall, color = textColor)
                            Text(
                                "来源: ${item.source.ifBlank { "—" }} · 类别: ${item.category} · 纯度: ${item.purity}",
                                color = textColor
                            )
                            if (item.source == "wallhaven" || item.keyword.isNotBlank()) {
                                Text(
                                    "关键词: ${item.keyword.ifBlank { "（未使用关键词）" }}",
                                    color = textColor
                                )
                            }
                            val res = if (item.width > 0 && item.height > 0) {
                                "${item.width}×${item.height}"
                            } else "分辨率未知"
                            Text("分辨率: $res · 大小: ${formatFileSize(item.fileSize)}", color = textColor)
                            val triggerLabel = com.kers.killove.jhsy.domain.TriggerType.fromCode(item.triggerType).label
                            Text(
                                "触发: $triggerLabel · ${fmt.format(Date(item.setAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.75f)
                            )
                            if (cacheExists) {
                                OutlinedButton(
                                    onClick = { previewPath = item.path },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Text("查看壁纸")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewPath?.let { path ->
        WallpaperPreviewDialog(
            path = path,
            onDismiss = { previewPath = null },
            onSave = { done ->
                vm.saveWallpaperToGallery(path, onDone = done)
            }
        )
    }
}

@Composable
private fun WallpaperPreviewDialog(
    path: String,
    onDismiss: () -> Unit,
    onSave: (onDone: (Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    var savedOk by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val bitmap = remember(path) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val maxSide = 2048
            val w = bounds.outWidth.coerceAtLeast(1)
            val h = bounds.outHeight.coerceAtLeast(1)
            while (w / sample > maxSide || h / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(16.dp)
        ) {
            Text(
                "壁纸预览",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "壁纸预览",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("无法加载图片\n$path", color = Color.White.copy(alpha = 0.8f))
                }
            }
            feedback?.let { msg ->
                Text(
                    msg,
                    color = if (savedOk) Color(0xFF81C784) else Color(0xFFE57373),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("返回") }
                Button(
                    onClick = {
                        if (saving || savedOk) return@Button
                        saving = true
                        feedback = "正在保存…"
                        onSave { ok, msg ->
                            saving = false
                            savedOk = ok
                            feedback = msg
                            Toast.makeText(
                                context,
                                if (ok) "已保存到相册" else msg,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = bitmap != null && !saving && !savedOk
                ) {
                    Text(
                        when {
                            saving -> "保存中…"
                            savedOk -> "已保存"
                            else -> "保存到相册"
                        }
                    )
                }
            }
            Text(
                File(path).name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "未知"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.getDefault(), "%.2f MB", mb)
        kb >= 1 -> String.format(Locale.getDefault(), "%.1f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
private fun rememberDateFormat(): SimpleDateFormat =
    androidx.compose.runtime.remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
