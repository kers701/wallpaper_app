package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.domain.ProxySelectMode
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel

@Composable
fun ProxyNodesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val busy by vm.proxyTestBusy.collectAsState()
    val textColor = LocalUiTextColor.current
    val nodes = settings.proxyNodes()
    val selectedId = settings.proxySelectedNodeId
    var interval by remember(settings.proxyAutoTestIntervalMinutes) {
        mutableFloatStateOf(settings.proxyAutoTestIntervalMinutes.toFloat())
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("代理节点", style = MaterialTheme.typography.headlineSmall, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }

        Text(
            "共 ${nodes.size} 个节点。手动点选立即生效；自动模式按间隔测 Wallhaven 延迟并选用最快可用节点。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(8.dp))

        GlassCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("选择模式", style = MaterialTheme.typography.titleSmall, color = textColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.proxySelectMode == ProxySelectMode.Manual,
                        onClick = { vm.setProxySelectMode(ProxySelectMode.Manual) }
                    )
                    Text("手动", color = textColor, modifier = Modifier.clickable {
                        vm.setProxySelectMode(ProxySelectMode.Manual)
                    })
                    Spacer(Modifier.padding(8.dp))
                    RadioButton(
                        selected = settings.proxySelectMode == ProxySelectMode.Auto,
                        onClick = { vm.setProxySelectMode(ProxySelectMode.Auto) }
                    )
                    Text("自动优选", color = textColor, modifier = Modifier.clickable {
                        vm.setProxySelectMode(ProxySelectMode.Auto)
                    })
                }
                if (settings.proxySelectMode == ProxySelectMode.Auto) {
                    Text(
                        "测速间隔：${interval.toInt()} 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                    Slider(
                        value = interval,
                        onValueChange = { interval = it },
                        valueRange = 5f..180f,
                        steps = 34,
                        onValueChangeFinished = {
                            vm.setProxyAutoTestInterval(interval.toInt())
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.testAllProxyNodes() },
                        enabled = !busy && nodes.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (busy) "测速中…" else "立即测速")
                    }
                    if (settings.proxySelectMode == ProxySelectMode.Auto) {
                        Button(
                            onClick = { vm.autoSelectBestProxyNode() },
                            enabled = !busy && nodes.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("选用最快") }
                    }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).padding(end = 8.dp)
                        )
                        Text("正在测试 Wallhaven 延迟…", color = textColor, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (nodes.isEmpty()) {
            Text("暂无节点，请在设置中导入订阅或粘贴节点列表。", color = textColor.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodes, key = { it.id }) { node ->
                    val selected = node.id == selectedId
                    GlassCard {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    vm.selectProxyNode(node.id)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (selected) "✓ " else "") + node.name,
                                    color = textColor
                                )
                                Text(
                                    "${node.type.label} · ${node.host}:${node.port}" +
                                        if (node.user.isNotBlank()) " · 有认证" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.65f)
                                )
                            }
                            Text(
                                when {
                                    node.latencyMs < 0L -> "未测"
                                    node.latencyMs == 0L -> "<1ms"
                                    else -> "${node.latencyMs}ms"
                                },
                                color = when {
                                    node.latencyMs < 0L -> textColor.copy(alpha = 0.5f)
                                    node.latencyMs < 800L -> MaterialTheme.colorScheme.primary
                                    else -> textColor
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}
