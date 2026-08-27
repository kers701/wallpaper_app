package com.kers.killove.jhsy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kers.killove.jhsy.domain.AvoidanceLocation
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import com.kers.killove.jhsy.util.LocationHelper
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 定位避让二级页：配置高德 Key 后可搜索地点加入列表。
 * 可自定义触发半径；显示当前位置。进入避让点半径内触发绿色模式 / 极限本地回退。
 */
@Composable
fun LocationAvoidScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    val context = LocalContext.current
    val list = settings.avoidanceLocations()

    var keyword by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<LocationHelper.PlaceHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var radius by remember(settings.locationAvoidRadiusMeters) {
        mutableFloatStateOf(settings.locationAvoidRadiusMeters.coerceIn(5, 500).toFloat())
    }
    var locText by remember { mutableStateOf("定位中…") }
    var nearestText by remember { mutableStateOf("") }
    var customLabel by remember { mutableStateOf("") }
    var resolvingName by remember { mutableStateOf(false) }

    fun refreshLocation() {
        if (!LocationHelper.hasLocationPermission(context)) {
            locText = "未授予定位权限"
            nearestText = ""
            return
        }
        val cur = LocationHelper.currentLocation(context)
        if (cur == null) {
            locText = "暂无定位（请打开系统定位或到室外刷新）"
            nearestText = ""
            return
        }
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(cur.time))
        locText = String.format(
            Locale.US,
            "纬度 %.6f · 经度 %.6f\n精度约 %.0f m · %s",
            cur.latitude,
            cur.longitude,
            cur.accuracy,
            timeStr
        )
        val (near, dist) = LocationHelper.nearestDistanceMeters(context, list)
        nearestText = if (near != null && dist != null) {
            val inZone = dist <= radius
            String.format(
                Locale.CHINA,
                "最近避让点：%s · %.0f 米%s",
                near.name,
                dist,
                if (inZone) "（已在触发范围内）" else ""
            )
        } else if (list.isEmpty()) {
            "尚未添加避让点"
        } else {
            "无法计算距离"
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshLocation() }

    LaunchedEffect(list, radius) {
        refreshLocation()
        while (true) {
            delay(15_000)
            refreshLocation()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("定位避让", style = MaterialTheme.typography.headlineSmall, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "配置高德开发者 Key 后可搜索位置。进入避让点触发半径内可开启绿色模式（R13 / 仅 Sketchy 随机），并可开启极限回退（仅本地换壁纸）。离开后恢复原状态。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.8f)
        )

        GlassCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前位置", style = MaterialTheme.typography.titleSmall, color = textColor)
                Text(locText, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.9f))
                if (nearestText.isNotBlank()) {
                    Text(nearestText, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.85f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val need = arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            val missing = need.any {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing) permLauncher.launch(need) else refreshLocation()
                        }
                    ) { Text("刷新定位") }
                }
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text("避让点标签（可空）") },
                    placeholder = { Text("留空则自动解析地名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            resolvingName = true
                            vm.resolveCurrentPlaceName { name ->
                                resolvingName = false
                                if (!name.isNullOrBlank()) customLabel = name
                            }
                        },
                        enabled = !resolvingName,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (resolvingName) "解析中…" else "自动获取地名") }
                    OutlinedButton(
                        onClick = { vm.addCurrentLocationAsAvoid(customLabel) },
                        modifier = Modifier.weight(1f)
                    ) { Text("设为避让点") }
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "触发范围：${radius.toInt()} 米",
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor
                )
                Text(
                    "拖动滑条自定义进入避让区的判定半径（5～500 米）",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 5f..500f,
                    steps = 98,
                    onValueChangeFinished = {
                        vm.setAvoidRadiusMeters(radius.toInt())
                        refreshLocation()
                    }
                )
            }
        }

        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("搜索地点") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedButton(
            onClick = {
                searching = true
                vm.searchAvoidPlaces(keyword) { result ->
                    hits = result
                    searching = false
                }
            },
            enabled = !searching && keyword.isNotBlank(),
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
