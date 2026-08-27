package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.domain.AvoidanceLocation
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import com.kers.killove.jhsy.util.LocationHelper

/**
 * 定位避让二级页：配置高德 Key 后可搜索地点加入列表。
 * 更换壁纸前若当前位置在列表任一点 10 米内，触发纯度锁定 / 极限本地回退。
 */
@Composable
fun LocationAvoidScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    var keyword by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<LocationHelper.PlaceHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val list = settings.avoidanceLocations()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
            }
            Text("定位避让", style = MaterialTheme.typography.headlineSmall, color = textColor)
        }
        Text(
            "配置高德开发者 Key 后可搜索位置。进入避让点 10 米内可锁定纯度 R13，并可开启极限回退（仅本地换壁纸）。离开后恢复原状态。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.8f)
        )
        if (settings.amapApiKey.isBlank()) {
            Text("请先在设置中填写高德 Web 服务 Key", color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("搜索地点") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = {
                searching = true
                vm.searchAvoidPlaces(keyword) {
                    hits = it
                    searching = false
                }
            },
            enabled = !searching && settings.amapApiKey.isNotBlank() && keyword.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (searching) "搜索中…" else "搜索")
        }
        if (hits.isNotEmpty()) {
            Text("搜索结果", style = MaterialTheme.typography.titleSmall, color = textColor)
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(hits, key = { it.id }) { hit ->
                    GlassCard {
                        Column(Modifier.padding(10.dp)) {
                            Text(hit.name, color = textColor)
                            Text(hit.address, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.7f))
                            Text("${hit.lat}, ${hit.lng}", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.6f))
                            OutlinedButton(
                                onClick = {
                                    vm.addAvoidanceLocation(
                                        AvoidanceLocation(hit.id, hit.name, hit.lat, hit.lng)
                                    )
                                }
                            ) { Text("加入避让列表") }
                        }
                    }
                }
            }
        }
        Text("已选避让点（${list.size}）", style = MaterialTheme.typography.titleSmall, color = textColor)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(list, key = { it.id }) { loc ->
                GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(loc.name, color = textColor)
                            Text("${loc.lat}, ${loc.lng}", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.7f))
                        }
                        OutlinedButton(onClick = { vm.removeAvoidanceLocation(loc.id) }) {
                            Text("移除")
                        }
                    }
                }
            }
        }
    }
}
