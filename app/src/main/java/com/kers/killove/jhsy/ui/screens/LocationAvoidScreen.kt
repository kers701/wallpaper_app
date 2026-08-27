package com.kers.killove.jhsy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.kers.killove.jhsy.util.RootKeepAlive
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 定位避让二级页：整页 LazyColumn 保证可上下滑动（Slider 不拦截外层滚动）。
 */
@Composable
fun LocationAvoidScreen(vm: MainViewModel, onBack: () -> Unit, onOpenList: () -> Unit = {}) {
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
            locText = "未授予定位权限（请先允许定位）"
            nearestText = ""
            return
        }
        var warn = ""
        if (!LocationHelper.hasBackgroundLocationPermission(context)) {
            warn = "⚠️ 未授予「始终允许」：应用不在前台时无法判定避让区，通知会只显示运行中"
        }
        val cur = LocationHelper.currentLocation(context)
        if (cur == null) {
            locText = "暂无定位（请打开系统定位或到室外刷新）"
            nearestText = warn
            return
        }
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(java.util.Date(cur.time))
        locText = String.format(
            Locale.US,
            "纬度 %.6f · 经度 %.6f\n精度约 %.0f m · %s",
            cur.latitude,
            cur.longitude,
            cur.accuracy,
            timeStr
        )
        val (near, dist) = LocationHelper.nearestDistanceMeters(context, list)
        val nearLine = if (near != null && dist != null) {
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
            ""
        }
        nearestText = listOf(warn, nearLine).filter { it.isNotBlank() }.joinToString("\n")
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("定位避让", style = MaterialTheme.typography.headlineSmall, color = textColor)
                TextButton(onClick = onBack) { Text("返回", color = textColor) }
            }
        }
        item {
            Text(
                "配置高德开发者 Key 后可搜索位置。进入避让点触发半径内可开启绿色模式（R13 / 仅 Sketchy 随机），并可开启极限回退（仅本地换壁纸）。离开后恢复原状态。\n" +
                    "重要：后台判定避让需要定位「始终允许」。Android 11+ 和多数国产系统（三星等）的弹窗里往往没有该选项，请点下方按钮进入系统设置手动选择。",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f)
            )
        }

        item {
            GlassCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前位置", style = MaterialTheme.typography.titleSmall, color = textColor)
                    Text(locText, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.9f))
                    if (nearestText.isNotBlank()) {
                        Text(
                            nearestText,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val need = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            val missingFg = need.any {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            when {
                                missingFg -> permLauncher.launch(need.toTypedArray())
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    !LocationHelper.hasBackgroundLocationPermission(context) -> {
                                    // 11+ 系统弹窗无「始终允许」：直接进应用详情让用户手选
                                    // 部分机型仍会弹出后台定位请求，再失败则打开设置
                                    try {
                                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                                            permLauncher.launch(
                                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                            )
                                        } else {
                                            LocationHelper.openAppLocationSettings(context)
                                        }
                                    } catch (_: Exception) {
                                        LocationHelper.openAppLocationSettings(context)
                                    }
                                }
                                else -> refreshLocation()
                            }
                        }
                    ) {
                        Text(
                            when {
                                !LocationHelper.hasLocationPermission(context) -> "授予定位权限"
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    !LocationHelper.hasBackgroundLocationPermission(context) ->
                                    "去系统设置开启始终允许"
                                else -> "刷新定位"
                            }
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        LocationHelper.hasLocationPermission(context) &&
                        !LocationHelper.hasBackgroundLocationPermission(context)
                    ) {
                        Text(
                            "你的系统弹窗只有「仅运行时允许 / 询问 / 不允许」是正常的，很多机型（含三星）不会在弹窗里给「始终允许」。\n" +
                                "请先选「仅运行时允许」并打开精确位置。\n" +
                                "再点下方打开权限页：应用信息 → 权限 → 位置，看是否多出「始终允许」。\n" +
                                "三星还可试：设置 → 位置 → 应用权限 → 镜花水月。\n" +
                                "若系统始终没有该选项：保持前台服务运行时，本版会用定位类型前台服务在「仅运行时」下尽量读取位置；仍失败则只能在打开应用时判定。",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                        OutlinedButton(
                            onClick = { LocationHelper.openAppLocationSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("打开应用权限设置") }
                        if (RootKeepAlive.hasRoot()) {
                            OutlinedButton(
                                onClick = {
                                    val ok = RootKeepAlive.grantBackgroundLocation(context.packageName)
                                    refreshLocation()
                                    // 状态写在 nearest 旁通过 refresh 体现；无 Toast 时文案已足够
                                    if (ok && LocationHelper.hasBackgroundLocationPermission(context)) {
                                        nearestText = "Root 已授予后台定位"
                                    } else if (ok) {
                                        nearestText = "已执行 Root 授权命令，请点刷新定位确认（部分系统仍需重启应用）"
                                    } else {
                                        nearestText = "Root 授权失败"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Root 授予后台定位") }
                        } else {
                            Text(
                                "无障碍权限不能增强定位。若有 Root，安装本版后可点「Root 授予后台定位」。",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
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
        }

        item {
            GlassCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "触发范围：${radius.toInt()} 米",
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "左右拖动调节半径；在空白处上下滑可滚动整页（5～500 米）",
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
        }

        item {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("搜索地点") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
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
        }

        if (hits.isNotEmpty()) {
            item {
                Text("搜索结果", style = MaterialTheme.typography.titleSmall, color = textColor)
            }
            items(hits, key = { "hit_${it.id}" }) { hit ->
                GlassCard {
                    Column(Modifier.padding(10.dp)) {
                        Text(hit.name, color = textColor)
                        Text(
                            hit.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        Text(
                            "${hit.lat}, ${hit.lng}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.6f)
                        )
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

        item {
            OutlinedButton(
                onClick = onOpenList,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看避让名单（${list.size}）")
            }
        }

        // 底部留白，避免被悬浮导航挡住
        item { Spacer(Modifier.height(100.dp)) }
    }
}
