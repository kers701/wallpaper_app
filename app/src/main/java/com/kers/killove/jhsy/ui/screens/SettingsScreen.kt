package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.domain.BgMode
import com.kers.killove.jhsy.domain.CategoryMode
import com.kers.killove.jhsy.domain.Purity
import com.kers.killove.jhsy.domain.ResolutionMode
import com.kers.killove.jhsy.domain.UiTextColor
import com.kers.killove.jhsy.domain.TranslateProvider
import com.kers.killove.jhsy.domain.WallpaperFitMode
import com.kers.killove.jhsy.domain.OrientationFilter
import com.kers.killove.jhsy.domain.WallpaperTarget
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.util.BatteryHelper
import com.kers.killove.jhsy.util.SuperServiceController
import androidx.compose.ui.platform.LocalContext
import com.kers.killove.jhsy.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val unlocked by vm.unlocked.collectAsState()
    val pinMessage by vm.pinMessage.collectAsState()
    val keysVisible = vm.keysVisible(settings)

    var interval by remember(settings.intervalMinutes) {
        mutableFloatStateOf(settings.intervalMinutes.toFloat())
    }
    var purity by remember(settings.purity) { mutableStateOf(settings.purity) }
    var category by remember(settings.categoryMode) { mutableStateOf(settings.categoryMode) }
    var target by remember(settings.target) { mutableStateOf(settings.target) }
    var resMode by remember(settings.resolutionMode) { mutableStateOf(settings.resolutionMode) }
    var fgs by remember(settings.useForegroundService) { mutableStateOf(settings.useForegroundService) }
    var skipOff by remember(settings.skipWhenScreenOff) { mutableStateOf(settings.skipWhenScreenOff) }
    var orientFilter by remember(settings.orientationFilter) { mutableStateOf(settings.orientationFilter) }
    var fitMode by remember(settings.fitMode) { mutableStateOf(settings.fitMode) }
    var isolate by remember(settings.isolateHomeLock) { mutableStateOf(settings.isolateHomeLock) }
    var powerSave by remember(settings.powerSaveEnabled) { mutableStateOf(settings.powerSaveEnabled) }
    var superSvc by remember(settings.superServiceEnabled) { mutableStateOf(settings.superServiceEnabled) }
    var powerTh by remember(settings.powerSaveBatteryThreshold) { mutableIntStateOf(settings.powerSaveBatteryThreshold) }
    var transProv by remember(settings.translateProvider) { mutableStateOf(settings.translateProvider) }
    var transKey by remember(settings.translateApiKey, keysVisible) {
        mutableStateOf(if (keysVisible) settings.translateApiKey else "")
    }
    var transSecret by remember(settings.translateSecret, keysVisible) {
        mutableStateOf(if (keysVisible) settings.translateSecret else "")
    }
    var transRegion by remember(settings.translateRegion) { mutableStateOf(settings.translateRegion) }
    var scrim by remember(settings.uiScrimAlpha) { mutableFloatStateOf(settings.uiScrimAlpha) }
    var cardA by remember(settings.uiCardAlpha) { mutableFloatStateOf(settings.uiCardAlpha) }
    var textColorOpt by remember(settings.uiTextColor) { mutableStateOf(settings.uiTextColor) }
    var minW by remember(settings.minWidth) { mutableStateOf(settings.minWidth.toString()) }
    var minH by remember(settings.minHeight) { mutableStateOf(settings.minHeight.toString()) }

    var apiKeysText by remember(settings.apiKeys, keysVisible) {
        mutableStateOf(if (keysVisible) settings.apiKeys.joinToString("\n") else "")
    }
    var keywordsText by remember(settings.keywords, keysVisible) {
        mutableStateOf(if (keysVisible) settings.keywords.joinToString("\n") else "")
    }
    var keywordsUrl by remember(settings.keywordsRemoteUrl, keysVisible) {
        mutableStateOf(if (keysVisible) settings.keywordsRemoteUrl else "")
    }
    var useKeywords by remember(settings.useKeywords) { mutableStateOf(settings.useKeywords) }
    var jumpMode by remember(settings.jumpModeEnabled) { mutableStateOf(settings.jumpModeEnabled) }

    var netFb by remember(settings.networkFallbackEnabled) {
        mutableStateOf(settings.networkFallbackEnabled)
    }
    var fallbackApi by remember(settings.fallbackApiUrl, keysVisible) {
        mutableStateOf(if (keysVisible) settings.fallbackApiUrl else "")
    }
    var localFb by remember(settings.localFallbackEnabled) {
        mutableStateOf(settings.localFallbackEnabled)
    }
    var forceLocal by remember(settings.forceLocalMode) {
        mutableStateOf(settings.forceLocalMode)
    }
    var localDir by remember(settings.localFallbackDir) {
        mutableStateOf(settings.localFallbackDir)
    }

    var bgApi by remember(settings.bgApiUrl) { mutableStateOf(settings.bgApiUrl) }
    var bgLocal by remember(settings.bgLocalPath) { mutableStateOf(settings.bgLocalPath) }
    var bgMode by remember(settings.bgMode) { mutableStateOf(settings.bgMode) }

    var pinInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("基础", style = MaterialTheme.typography.titleMedium)
        Text("更换间隔：${interval.toInt()} 分钟")
        Slider(
            value = interval,
            onValueChange = { interval = it },
            valueRange = 5f..60f,
            steps = 10
        )
        Text("小于 15 分钟将自动使用前台服务", style = MaterialTheme.typography.bodySmall)

        EnumDropdown("纯度", Purity.entries, purity) { purity = it }
        EnumDropdown("类别", CategoryMode.entries, category) { category = it }
        EnumDropdown("设置目标", WallpaperTarget.entries, target) { target = it }
        EnumDropdown("分辨率", ResolutionMode.entries, resMode) { resMode = it }

        if (resMode == ResolutionMode.Custom) {
            OutlinedTextField(
                value = minW,
                onValueChange = { minW = it.filter(Char::isDigit) },
                label = { Text("最低宽度") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = minH,
                onValueChange = { minH = it.filter(Char::isDigit) },
                label = { Text("最低高度") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        RowSwitch("强制前台服务（通知栏常驻，划掉可自启）", fgs) { fgs = it }
        RowSwitch("息屏时跳过", skipOff) { skipOff = it }
        val ignoring = BatteryHelper.isIgnoringBatteryOptimizations(context)
        Text(
            if (ignoring) "电池优化：已忽略（有利于后台保活）"
            else "电池优化：未忽略（划掉/息屏易被系统杀进程）",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = { BatteryHelper.requestIgnoreBatteryOptimizations(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ignoring) "打开应用详情（可再检查）" else "申请忽略电池优化（推荐）")
        }
        Text(
            "三星建议：设置 → 电池 → 本应用 → 不受限制；允许自启动",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("超级服务（独立进程保活）", style = MaterialTheme.typography.titleMedium)
        val superSt = SuperServiceController.status(context)
        Text(superSt.message, style = MaterialTheme.typography.bodySmall)
        Text(
            "Root: ${if (superSt.hasRoot) "可用" else "无"} · 无障碍: ${if (superSt.hasAccessibility) "已开" else "未开"}",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { SuperServiceController.openAccessibilitySettings(context) },
                modifier = Modifier.weight(1f)
            ) { Text("申请无障碍") }
            OutlinedButton(
                onClick = {
                    if (!superSt.canEnable) {
                        SuperServiceController.openAccessibilitySettings(context)
                        return@OutlinedButton
                    }
                    val err = SuperServiceController.enable(context)
                    if (err == null) {
                        superSvc = true
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = true
            ) { Text(if (superSvc) "已开启" else "开启超级服务") }
        }
        if (superSvc) {
            OutlinedButton(
                onClick = {
                    SuperServiceController.disable(context)
                    superSvc = false
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("关闭超级服务") }
        }
        Text(
            "说明：更换服务运行在独立进程 :svc；UI 划掉后服务进程仍可继续。无 Root/无障碍时无法开启超级服务。",
            style = MaterialTheme.typography.bodySmall
        )
        EnumDropdown("方向过滤", OrientationFilter.entries, orientFilter) { orientFilter = it }
        EnumDropdown("壁纸铺满方式", WallpaperFitMode.entries, fitMode) { fitMode = it }
        Text("填充=等比铺满（Windows 填充）；适应=完整显示；拉伸=强制铺满", style = MaterialTheme.typography.bodySmall)
        RowSwitch("桌面锁屏隔离（两次下载，可用不同关键词）", isolate) { isolate = it }
        RowSwitch("省电模式", powerSave) { powerSave = it }
        if (powerSave) {
            Text("电量低于 ${powerTh}% 时休眠，充电忽略，恢复后继续", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = powerTh.toFloat(),
                onValueChange = { powerTh = it.toInt().coerceIn(5, 50) },
                valueRange = 5f..50f,
                steps = 8
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("界面外观", style = MaterialTheme.typography.titleMedium)
        Text("主题遮罩透明度：${"%.0f".format(scrim * 100)}%", style = MaterialTheme.typography.bodySmall)
        Slider(value = scrim, onValueChange = { scrim = it }, valueRange = 0.15f..0.85f)
        Text("卡片透明度：${"%.0f".format(cardA * 100)}%（越高越实）", style = MaterialTheme.typography.bodySmall)
        Slider(value = cardA, onValueChange = { cardA = it }, valueRange = 0.05f..0.65f)
        EnumDropdown("文字颜色", UiTextColor.entries, textColorOpt) { textColorOpt = it }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("关键词翻译（仅展示/日志）", style = MaterialTheme.typography.titleMedium)
        EnumDropdown("翻译引擎", TranslateProvider.entries, transProv) { transProv = it }
        if (keysVisible) {
            OutlinedTextField(
                value = transKey,
                onValueChange = { transKey = it },
                label = { Text("API Key / SecretId") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (transProv == TranslateProvider.Tencent) {
                OutlinedTextField(
                    value = transSecret,
                    onValueChange = { transSecret = it },
                    label = { Text("腾讯 SecretKey") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            if (transProv == TranslateProvider.Microsoft) {
                OutlinedTextField(
                    value = transRegion,
                    onValueChange = { transRegion = it },
                    label = { Text("微软 Region（如 global / eastasia）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        } else {
            LockedField("翻译 API 密钥")
        }
        Text("翻译结果只显示在首页跃迁列表与状态，不改变实际搜索词", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("软件背景", style = MaterialTheme.typography.titleMedium)
        Text(
            "优先本地路径 → API 链接 → 系统壁纸；都为空则使用莫奈取色",
            style = MaterialTheme.typography.bodySmall
        )
        EnumDropdown("背景模式", BgMode.entries, bgMode) { bgMode = it }
        OutlinedTextField(
            value = bgApi,
            onValueChange = { bgApi = it },
            label = { Text("背景 API 链接（打开自动获取）") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://… 返回图片或 JSON") },
            maxLines = 2
        )
        OutlinedTextField(
            value = bgLocal,
            onValueChange = { bgLocal = it },
            label = { Text("背景本地路径") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/storage/emulated/0/Pictures/bg.jpg") },
            singleLine = true
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("PIN 锁定", style = MaterialTheme.typography.titleMedium)
        Text(
            if (settings.pinEnabled) {
                if (unlocked) "状态：已解锁（敏感项可见）" else "状态：已锁定（密钥/关键词/兜底 API 已隐藏）"
            } else {
                "状态：未启用 PIN"
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (pinMessage != null) {
            Text(pinMessage!!, color = MaterialTheme.colorScheme.primary)
        }

        if (settings.pinEnabled && !unlocked) {
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.filter(Char::isDigit).take(8) },
                label = { Text("输入 PIN 解锁") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Button(
                onClick = {
                    vm.unlock(pinInput)
                    pinInput = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("解锁") }
        } else {
            if (settings.pinEnabled) {
                OutlinedButton(
                    onClick = { vm.lockNow() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("立即锁定") }
            }
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                label = { Text(if (settings.pinEnabled) "新 PIN（4～8 位）" else "设置 PIN（4～8 位）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter(Char::isDigit).take(8) },
                label = { Text("确认 PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.setPinWithConfirm(newPin, confirmPin)
                        if (newPin.isNotEmpty() && newPin == confirmPin) {
                            newPin = ""
                            confirmPin = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (settings.pinEnabled) "修改 PIN" else "启用 PIN") }
                if (settings.pinEnabled) {
                    TextButton(
                        onClick = { vm.setPin("", enable = false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("关闭 PIN") }
                }
            }
            Text("PIN 仅存哈希，进程重启后需重新解锁", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("API 密钥（可多个，每行一个）", style = MaterialTheme.typography.titleMedium)
        if (keysVisible) {
            OutlinedTextField(
                value = apiKeysText,
                onValueChange = { apiKeysText = it },
                label = { Text("Wallhaven API Keys") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                minLines = 3,
                maxLines = 8
            )
            Text("失败时会自动轮换到下一个密钥", style = MaterialTheme.typography.bodySmall)
        } else {
            LockedField("Wallhaven API Keys")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("关键词", style = MaterialTheme.typography.titleMedium)
        RowSwitch("启用关键词搜索", useKeywords) { useKeywords = it }
        RowSwitch("跃迁模式（用上次成功标签覆盖跃迁列表）", jumpMode) { jumpMode = it }
        Text(
            "Wallhaven 成功后会用该壁纸标签覆盖跃迁列表；开启且列表非空时优先用跃迁词搜索",
            style = MaterialTheme.typography.bodySmall
        )
        if (settings.jumpKeywords.isEmpty()) {
            Text(
                "跃迁列表：空（尚未从 Wallhaven 成功写入）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "跃迁列表（${settings.jumpKeywords.size}）：",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = settings.jumpKeywords.joinToString("\n"),
                onValueChange = {},
                readOnly = true,
                label = { Text("跃迁关键词（只读，由系统覆盖写入）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                minLines = 2,
                maxLines = 10
            )
        }
        if (keysVisible) {
            OutlinedTextField(
                value = keywordsText,
                onValueChange = { keywordsText = it },
                label = { Text("本地关键词（每行一个）") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                minLines = 4,
                maxLines = 12
            )
            OutlinedTextField(
                value = keywordsUrl,
                onValueChange = { keywordsUrl = it },
                label = { Text("远程关键词 txt 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://example.com/keywords.txt") }
            )
            OutlinedButton(
                onClick = { vm.importKeywordsFromUrl(keywordsUrl, replace = true) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("从远程导入并覆盖本地列表") }
            OutlinedButton(
                onClick = { vm.importKeywordsFromUrl(keywordsUrl, replace = false) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("从远程导入并合并到本地") }
        } else {
            LockedField("本地关键词 / 远程地址")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("兜底策略", style = MaterialTheme.typography.titleMedium)
        RowSwitch("网络兜底（Wallhaven 失败 → 备用 API）", netFb) { netFb = it }
        if (keysVisible) {
            OutlinedTextField(
                value = fallbackApi,
                onValueChange = { fallbackApi = it },
                label = { Text("兜底 API（每行一个，失败自动试下一个）") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://… 支持 {width}{height}，多行多个") },
                minLines = 2,
                maxLines = 6
            )
            Text(
                "每行一个 URL；响应可为直接图片、一行图片 URL、或含 path/url/image 的 JSON",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LockedField("兜底 API URL")
        }

        RowSwitch("本地兜底（无网/失败时从目录随机）", localFb) { localFb = it }
        RowSwitch("强制本地模式（不访问网络）", forceLocal) { forceLocal = it }
        OutlinedTextField(
            value = localDir,
            onValueChange = { localDir = it },
            label = { Text("本地兜底目录（空=App 私有目录）") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/storage/emulated/0/Pictures/Wallpapers") }
        )
        Text("当前：${vm.localFallbackInfo()}", style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                val keys = if (keysVisible) SettingsRepository.splitLines(apiKeysText) else settings.apiKeys
                val kws = if (keysVisible) SettingsRepository.splitLines(keywordsText) else settings.keywords
                val kwUrl = if (keysVisible) keywordsUrl.trim() else settings.keywordsRemoteUrl
                val fbUrl = if (keysVisible) fallbackApi.trim() else settings.fallbackApiUrl
                vm.saveSettings(
                    settings.copy(
                        intervalMinutes = interval.toInt(),
                        purity = purity,
                        categoryMode = category,
                        target = target,
                        resolutionMode = resMode,
                        useForegroundService = fgs,
                        skipWhenScreenOff = skipOff,
                        orientationFilter = orientFilter,
                        fitMode = fitMode,
                        isolateHomeLock = isolate,
                        powerSaveEnabled = powerSave,
                        powerSaveBatteryThreshold = powerTh.coerceIn(5, 50),
                        superServiceEnabled = superSvc,
                        translateProvider = transProv,
                        translateApiKey = if (keysVisible) transKey.trim() else settings.translateApiKey,
                        translateSecret = if (keysVisible) transSecret.trim() else settings.translateSecret,
                        translateRegion = transRegion.trim().ifBlank { "global" },
                        uiScrimAlpha = scrim,
                        uiCardAlpha = cardA,
                        uiTextColor = textColorOpt,
                        minWidth = minW.toIntOrNull() ?: settings.minWidth,
                        minHeight = minH.toIntOrNull() ?: settings.minHeight,
                        apiKeys = keys,
                        keywords = kws,
                        keywordsRemoteUrl = kwUrl,
                        useKeywords = useKeywords,
                        jumpModeEnabled = jumpMode,
                        networkFallbackEnabled = netFb,
                        fallbackApiUrl = fbUrl,
                        localFallbackEnabled = localFb,
                        forceLocalMode = forceLocal,
                        localFallbackDir = localDir.trim(),
                        bgApiUrl = bgApi.trim(),
                        bgLocalPath = bgLocal.trim(),
                        bgMode = bgMode
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存设置")
        }
    }
}

@Composable
private fun LockedField(label: String) {
    OutlinedTextField(
        value = "••••••••\n（已锁定，不可见）",
        onValueChange = {},
        enabled = false,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        minLines = 2,
        maxLines = 4
    )
    Text(
        "已启用 PIN 且处于锁定状态，请先解锁后查看或修改",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    items: List<T>,
    selected: T,
    itemLabel: (T) -> String = {
        when (it) {
            is Purity -> it.label
            is CategoryMode -> it.label
            is WallpaperTarget -> it.label
            is ResolutionMode -> it.label
            is BgMode -> it.label
            is UiTextColor -> it.label
            is OrientationFilter -> it.label
            is WallpaperFitMode -> it.label
            is TranslateProvider -> it.label
            else -> it.toString()
        }
    },
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = itemLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
