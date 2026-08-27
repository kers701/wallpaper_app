package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val recent by vm.recent.collectAsState()
    val fmt = rememberDateFormat()
    val textColor = LocalUiTextColor.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("最近记录", style = MaterialTheme.typography.headlineSmall, color = textColor)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.OutlinedButton(onClick = { vm.clearLogs() }) {
                Text("清空记录")
            }
            androidx.compose.material3.OutlinedButton(onClick = { vm.clearWallpaperCache() }) {
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
                    GlassCard {
                        Column(Modifier.padding(12.dp)) {
                            Text("ID: ${item.id}", style = MaterialTheme.typography.titleSmall, color = textColor)
                            Text("来源: ${item.source.ifBlank { "—" }} · 类别: ${item.category} · 纯度: ${item.purity}", color = textColor)
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
                            val triggerLabel = when (item.triggerType) {
                                "manual" -> "手动"
                                else -> "自动"
                            }
                            Text("触发: $triggerLabel · ${fmt.format(Date(item.setAt))}", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.75f))
                        }
                    }
                }
            }
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
