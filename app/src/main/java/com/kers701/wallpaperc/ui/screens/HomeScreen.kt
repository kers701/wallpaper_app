package com.kers701.wallpaperc.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers701.wallpaperc.ui.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val networkProbe by vm.networkProbe.collectAsState()
    val probing by vm.probing.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("化云烟", style = MaterialTheme.typography.headlineMedium)
        Text(
            "化云烟 · 自动更换壁纸",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RowSwitch(
                    title = "自动更换",
                    checked = settings.enabled,
                    onChecked = { vm.setEnabled(it) }
                )
                Text("间隔：${settings.intervalMinutes} 分钟")
                Text("纯度：${settings.purity.label} · 类别：${settings.categoryMode.label}")
                Text("目标：${settings.target.label}")
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
                    }
                )
                Text(
                    buildString {
                        append("兜底：")
                        if (settings.forceLocalMode) append("强制本地")
                        else {
                            if (settings.networkFallbackEnabled) append("网络")
                            if (settings.localFallbackEnabled) {
                                if (settings.networkFallbackEnabled) append("+")
                                append("本地")
                            }
                            if (!settings.networkFallbackEnabled && !settings.localFallbackEnabled) {
                                append("关闭")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (settings.intervalMinutes < 15 || settings.useForegroundService) {
                    Text(
                        "当前使用前台服务（短间隔）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网络检测", style = MaterialTheme.typography.titleMedium)
                Text(
                    networkProbe,
                    style = MaterialTheme.typography.bodySmall
                )
                if (probing) {
                    CircularProgressIndicator()
                }
                OutlinedButton(
                    onClick = { vm.testNetwork() },
                    enabled = !probing && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (probing) "检测中…" else "测试网络延迟")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("状态", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (busy) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                }
                Text(status)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
