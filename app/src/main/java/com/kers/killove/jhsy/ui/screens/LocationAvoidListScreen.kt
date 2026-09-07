package com.kers.killove.jhsy.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel

/** 避让名单次级页：仅展示/移除已选点 */
@Composable
fun LocationAvoidListScreen(vm: MainViewModel, onBack: () -> Unit) {
    var pendingRemoveId by remember { mutableStateOf<String?>(null) }
    var pendingRemoveName by remember { mutableStateOf("") }

    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    val list = settings.avoidanceLocations()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("避让名单", style = MaterialTheme.typography.headlineSmall, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "共 ${list.size} 个点。进入触发半径内将按绿色模式 / 极限回退策略生效。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            Text("暂无避让点，请在上一页搜索或添加当前位置。", color = textColor.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { loc ->
                    GlassCard {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(loc.name, color = textColor)
                                Text(
                                    "${loc.lat}, ${loc.lng}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                            OutlinedButton(onClick = {
                                pendingRemoveId = loc.id
                                pendingRemoveName = loc.name.ifBlank { loc.id }
                            }) {
                                Text("移除")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (pendingRemoveId != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveId = null },
            title = { Text("移除避让点？") },
            text = { Text("确定移除「$pendingRemoveName」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoveId?.let { vm.removeAvoidanceLocation(it) }
                    pendingRemoveId = null
                }) { Text("确认移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveId = null }) { Text("取消") }
            }
        )
    }

}
