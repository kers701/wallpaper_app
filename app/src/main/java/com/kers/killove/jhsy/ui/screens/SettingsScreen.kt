package com.kers.killove.jhsy.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.kers.killove.jhsy.domain.CardStyle
import com.kers.killove.jhsy.domain.ProxyType
import com.kers.killove.jhsy.domain.ProxySelectMode
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.util.BatteryHelper
import com.kers.killove.jhsy.util.SuperServiceController
import androidx.compose.ui.platform.LocalContext
import com.kers.killove.jhsy.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onOpenBlacklist: () -> Unit = {}, onOpenLocationAvoid: () -> Unit = {}, onOpenProxyNodes: () -> Unit = {}) {
    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.backupConfigToUri(uri)
    }
    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.restoreConfigFromUri(uri)
    }
    // 必须在 composable 顶层注册，不能放进 if (hasRoot) / if (proxyOn)
    val pickSuperBinLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importSuperProxyBin(uri)
    }
    val pickSuperCfgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importSuperProxyConfig(uri)
    }

    val settings by vm.settings.collectAsState()
    val superProxyStatus by vm.status.collectAsState()
    val hasRoot = remember { com.kers.killove.jhsy.util.RootKeepAlive.hasRoot() }
    val context = LocalContext.current
    val textColor = LocalUiTextColor.current
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
    var dataSaver by remember(settings.dataSaverEnabled) { mutableStateOf(settings.dataSaverEnabled) }
    var superSvc by remember(settings.superServiceEnabled) { mutableStateOf(settings.superServiceEnabled) }
    var restoreJson by remember { mutableStateOf("") }
    var remoteConfigUrl by remember { mutableStateOf("") }
    var showRemoteConfig by remember { mutableStateOf(false) }
    var showRestoreField by remember { mutableStateOf(false) }
    // 配置页各板块折叠：默认只显示标题
    // 手风琴：同时只展开一个区块
    var openSection by remember { mutableStateOf("") }
    fun toggleSection(id: String) {
        openSection = if (openSection == id) "" else id
    }
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
    var proxyOn by remember(settings.proxyEnabled) { mutableStateOf(settings.proxyEnabled) }
    var accelOn by remember(settings.accelModeEnabled) { mutableStateOf(settings.accelModeEnabled) }
    var accelPrivacy by remember(settings.accelPrivacyAccepted) { mutableStateOf(settings.accelPrivacyAccepted) }
    var accelNodesUrl by remember(settings.accelNodesRemoteUrl) { mutableStateOf(settings.accelNodesRemoteUrl) }
    var showAccelDialog by remember { mutableStateOf(false) }
    var accelCountdown by remember { mutableStateOf(10) }
    var proxyHost by remember(settings.proxyHost, keysVisible) {
        mutableStateOf(if (keysVisible) settings.proxyHost else "")
    }
    var proxyPort by remember(settings.proxyPort, keysVisible) {
        mutableStateOf(if (keysVisible) settings.proxyPort.let { if (it > 0) it.toString() else "" } else "")
    }
    var proxyUser by remember(settings.proxyUser, keysVisible) {
        mutableStateOf(if (keysVisible) settings.proxyUser else "")
    }
    var proxyPass by remember(settings.proxyPassword, keysVisible) {
        mutableStateOf(if (keysVisible) settings.proxyPassword else "")
    }
    var proxyType by remember(settings.proxyType) { mutableStateOf(settings.proxyType) }
    var proxySub by remember(settings.proxySubUrl, keysVisible) {
        mutableStateOf(if (keysVisible) settings.proxySubUrl else "")
    }
    var superProxyOn by remember(settings.superProxyEnabled) { mutableStateOf(settings.superProxyEnabled) }
    var superBin by remember(settings.superProxyBinPath, keysVisible) {
        mutableStateOf(if (keysVisible) settings.superProxyBinPath else "")
    }
    var superCfg by remember(settings.superProxyConfigPath, keysVisible) {
        mutableStateOf(if (keysVisible) settings.superProxyConfigPath else "")
    }
    var superSub by remember(settings.superProxySubUrl, keysVisible) {
        mutableStateOf(if (keysVisible) settings.superProxySubUrl else "")
    }
    var superArgs by remember(settings.superProxyArgs, keysVisible) {
        mutableStateOf(if (keysVisible) settings.superProxyArgs else "")
    }
    var superPort by remember(settings.superProxyLocalPort) {
        mutableStateOf(settings.superProxyLocalPort.toString())
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
    var localFbCache by remember(settings.localFallbackUseCache) { mutableStateOf(settings.localFallbackUseCache) }
    var localFbSkip by remember(settings.localFallbackCacheSkipNewest) { mutableIntStateOf(settings.localFallbackCacheSkipNewest) }
    var locAvoid by remember(settings.locationAvoidEnabled) { mutableStateOf(settings.locationAvoidEnabled) }
    var amapKey by remember(settings.amapApiKey, keysVisible) { mutableStateOf(if (keysVisible) settings.amapApiKey else "") }
    var locFb by remember(settings.locationFallbackEnabled) { mutableStateOf(settings.locationFallbackEnabled) }
    var locExtreme by remember(settings.locationExtremeFallbackEnabled) { mutableStateOf(settings.locationExtremeFallbackEnabled) }
    var cardStyleOpt by remember(settings.cardStyle) { mutableStateOf(settings.cardStyle) }

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

        // —— 常用：调度与来源 ——
        CollapsibleSection(
            title = "基础",
            expanded = openSection == "basic",
            onToggle = { toggleSection("basic") }
        ) {
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

        Text("加速模式（与「网络代理」互斥）", style = MaterialTheme.typography.titleSmall)
        Text(
            "从内置/配置的中转节点随机下载。开启前须阅读安全与隐私声明。",
            style = MaterialTheme.typography.bodySmall
        )
        RowSwitch("启用加速模式", accelOn) { want ->
            if (want) {
                showAccelDialog = true
                accelCountdown = 10
            } else {
                accelOn = false
            }
        }
        if (accelOn) {
            Text(
                "已启用 · 可用节点约 ${com.kers.killove.jhsy.data.remote.BuiltinAccelNodes.all(context).size} 个",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = accelNodesUrl,
                onValueChange = { accelNodesUrl = it },
                label = { Text("远程节点列表 URL（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        }

        CollapsibleSection(
            title = "API 密钥（可多个，每行一个）",
            expanded = openSection == "keys",
            onToggle = { toggleSection("keys") }
        ) {
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
        }

        CollapsibleSection(
            title = "关键词",
            expanded = openSection == "kw",
            onToggle = { toggleSection("kw") }
        ) {
        RowSwitch("启用关键词搜索", useKeywords) { useKeywords = it }
        if (useKeywords) {
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
        } // end if (useKeywords)

        }

        CollapsibleSection(
            title = "兜底策略",
            expanded = openSection == "fb",
            onToggle = { toggleSection("fb") }
        ) {
        RowSwitch("网络兜底（Wallhaven 失败 → 备用 API）", netFb) { netFb = it }
        if (netFb) {
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
        }

        RowSwitch("本地兜底（无网/失败时从目录随机）", localFb) { localFb = it }
        if (localFb) {
        RowSwitch("强制本地模式（不访问网络）", forceLocal) { forceLocal = it }
        OutlinedTextField(
            value = localDir,
            onValueChange = { localDir = it },
            label = { Text("本地兜底目录（空=App 私有目录）") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/storage/emulated/0/Pictures/Wallpapers") }
        )
        Text("当前：${vm.localFallbackInfo()}", style = MaterialTheme.typography.bodySmall)
        RowSwitch("本地兜底也使用下载缓存（跳过最新图）", localFbCache) { localFbCache = it }
        if (localFbCache) {
            Text("跳过最新 ${localFbSkip} 张缓存", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = localFbSkip.toFloat(),
                onValueChange = { localFbSkip = it.toInt().coerceIn(0, 20) },
                valueRange = 0f..20f,
                steps = 19
            )
        }
        }

        }


        // —— 网络与保活 ——
        CollapsibleSection(
            title = "网络代理",
            expanded = openSection == "proxy",
            onToggle = { toggleSection("proxy") }
        ) {
        if (keysVisible) {
            RowSwitch("启用代理（不可用自动回退系统网络）", proxyOn) {
                if (it && accelOn) {
                    // 与加速模式互斥
                    accelOn = false
                }
                proxyOn = it
                if (!it) {
                    superProxyOn = false
                    vm.stopSuperProxy()
                }
            }
            if (proxyOn) {
            Text(
                "链路：超级代理（可用）→ 普通代理 → 系统网络。须先开代理才能开超级代理。",
                style = MaterialTheme.typography.bodySmall
            )
            EnumDropdown("代理类型", ProxyType.entries, proxyType) { proxyType = it }
            Text(
                "支持 HTTP 与 SOCKS5。SOCKS5 带认证在部分机型上可能不稳定，优先无认证节点。",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = proxyHost,
                onValueChange = { proxyHost = it },
                label = { Text("服务器地址（手动单节点）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = proxyPort,
                onValueChange = { proxyPort = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("端口号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = proxyUser,
                onValueChange = { proxyUser = it },
                label = { Text("用户名（可空）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = proxyPass,
                onValueChange = { proxyPass = it },
                label = { Text("密码（可空）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = proxySub,
                onValueChange = { proxySub = it },
                label = { Text("订阅链接或节点列表（可粘贴）") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://… 或 socks5://host:port#名") },
                minLines = 2,
                maxLines = 5
            )
            Text(
                "支持：订阅 URL / Base64 列表 / 每行 socks5://、http://、host:port；不支持 ss/vmess/trojan。",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = { vm.importProxySubscription(proxySub) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导入订阅 / 解析节点") }
            val nodeCount = settings.proxyNodes().size
            if (nodeCount > 0) {
                Text(
                    "已导入 $nodeCount 个节点 · 当前模式：${settings.proxySelectMode.label}" +
                        (settings.selectedProxyNode()?.let { " · 选用 ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = onOpenProxyNodes,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("节点选择（手动 / 自动测速）…") }
            }

            // —— 超级代理：仅 Root 设备显示 ——
            if (hasRoot) {
                Text("超级代理（Root）", style = MaterialTheme.typography.titleSmall)
                Text(
                    "用 Root 启动自定义内核（推荐 mihomo / Clash Meta），只监听 127.0.0.1，仅本应用走代理。\n" +
                        "请用「选择文件」导入内核/配置到应用私有目录（无需所有文件访问权限）。\n" +
                        "配置优先级：① 已导入配置 → ② 订阅链接自动生成。",
                    style = MaterialTheme.typography.bodySmall
                )
                val spSt = com.kers.killove.jhsy.util.SuperProxyController.status(
                    context,
                    settings.copy(
                        superProxyEnabled = superProxyOn && proxyOn,
                        superProxyBinPath = superBin.ifBlank { settings.superProxyBinPath },
                        superProxyConfigPath = superCfg.ifBlank { settings.superProxyConfigPath },
                        superProxySubUrl = superSub,
                        superProxyLocalPort = superPort.toIntOrNull() ?: 17890
                    )
                )
                Text(spSt.message, style = MaterialTheme.typography.bodySmall)
                if (!proxyOn) {
                    Text(
                        "请先启用上方「代理」，超级代理才能开启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                RowSwitch(
                    "启用超级代理（仅本应用）",
                    superProxyOn && proxyOn
                ) {
                    if (proxyOn) {
                        superProxyOn = it
                        if (!it) vm.stopSuperProxy()
                    } else {
                        superProxyOn = false
                        vm.stopSuperProxy()
                    }
                }
                if (superProxyOn && proxyOn) {

                Text(
                    "内核：${if (settings.superProxyBinPath.isNotBlank()) settings.superProxyBinPath else "未导入"}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { pickSuperBinLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = (superProxyOn && proxyOn)
                ) { Text("选择内核文件并导入…") }

                OutlinedTextField(
                    value = superSub,
                    onValueChange = { superSub = it },
                    label = { Text("订阅链接（可选，无自备配置时用）") },
                    placeholder = { Text("https://…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = (superProxyOn && proxyOn)
                )
                Text(
                    "配置：${if (settings.superProxyConfigPath.isNotBlank()) settings.superProxyConfigPath else "未导入（可用订阅自动生成）"}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { pickSuperCfgLauncher.launch(arrayOf("application/*", "text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = (superProxyOn && proxyOn)
                ) { Text("选择配置文件并导入…") }

                OutlinedTextField(
                    value = superArgs,
                    onValueChange = { superArgs = it },
                    label = { Text("启动参数（可空=自动猜测）") },
                    placeholder = { Text("-f {config} -d {workdir}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = (superProxyOn && proxyOn)
                )
                OutlinedTextField(
                    value = superPort,
                    onValueChange = { superPort = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("本地 SOCKS 端口（默认 17890）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = (superProxyOn && proxyOn)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            vm.startSuperProxy(
                                settings.copy(
                                    superProxyEnabled = true,
                                    superProxyBinPath = settings.superProxyBinPath.ifBlank { superBin.trim() },
                                    superProxyConfigPath = settings.superProxyConfigPath.ifBlank { superCfg.trim() },
                                    superProxySubUrl = superSub.trim(),
                                    superProxyArgs = superArgs.trim(),
                                    superProxyLocalPort = superPort.toIntOrNull()?.coerceIn(1025, 65535)
                                        ?: 17890
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = (superProxyOn && proxyOn)
                    ) { Text("启动内核") }
                    OutlinedButton(
                        onClick = { vm.stopSuperProxy() },
                        modifier = Modifier.weight(1f)
                    ) { Text("停止内核") }
                }
                if (superProxyStatus.isNotBlank() && (
                        superProxyStatus.contains("超级代理") ||
                            superProxyStatus.contains("内核") ||
                            superProxyStatus.contains("导入") ||
                            superProxyStatus.contains("Root") ||
                            superProxyStatus.contains("配置")
                        )
                ) {
                    Text(
                        superProxyStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                } // end if (superProxyOn && proxyOn)
            }
            } // end if (proxyOn)
        } else {
            LockedField("网络代理（请先解锁 PIN）")
        }
        }

        CollapsibleSection(
            title = "超级服务（独立进程保活）",
            expanded = openSection == "super",
            onToggle = { toggleSection("super") }
        ) {
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
        Text("填充=等比铺满；适应=完整显示留边；居中=原图居中裁多余；拉伸=强制变形铺满。修改后保存会用当前壁纸重设（不重新下载）", style = MaterialTheme.typography.bodySmall)
        RowSwitch("桌面锁屏隔离（两次下载，可用不同关键词）", isolate) { isolate = it }
        RowSwitch("省电模式", powerSave) { powerSave = it }
        RowSwitch("省流量模式", dataSaver) { dataSaver = it }
        if (dataSaver) {
            Text(
                "当日写入缓存：≥1GB 间隔+5 分钟；≥10GB 再+5；≥20GB 今日停换。手动更换不受限。",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.75f)
            )
        }
        if (powerSave) {
            Text("电量低于 ${powerTh}% 时休眠，充电忽略，恢复后继续", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = powerTh.toFloat(),
                onValueChange = { powerTh = it.toInt().coerceIn(5, 50) },
                valueRange = 5f..50f,
                steps = 8
            )
        }

        }


        // —— 场景限制 ——
        CollapsibleSection(
            title = "应用黑名单",
            expanded = openSection == "bl",
            onToggle = { toggleSection("bl") }
        ) {
        Text(
            "已选 ${settings.blacklistPackages.size} 个 · 前台休眠不换壁纸",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = onOpenBlacklist,
            modifier = Modifier.fillMaxWidth()
        ) { Text("管理黑名单（名单在次级页）…") }
        }

        CollapsibleSection(
            title = "定位避让",
            expanded = openSection == "loc",
            onToggle = { toggleSection("loc") }
        ) {
        RowSwitch("启用定位避让", locAvoid) { locAvoid = it }
        if (locAvoid) {
            if (keysVisible) {
                OutlinedTextField(value = amapKey, onValueChange = { amapKey = it }, label = { Text("高德 Web 服务 Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } else {
                LockedField("高德 Key")
            }
            RowSwitch("绿色模式（区内 R13 / 仅 Sketchy 随机）", locFb) { locFb = it }
            RowSwitch("定位极限回退（区内仅本地换壁纸）", locExtreme) { locExtreme = it }
            Text(
                "已选 ${settings.avoidanceLocations().size} 个点 · 半径 ${settings.locationAvoidRadiusMeters} 米 · 区内: ${if (settings.locationInAvoidZone) "生效中" else "未触发"} · 后台需「始终允许」定位",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onOpenLocationAvoid, modifier = Modifier.fillMaxWidth()) {
                Text("管理避让地点（名单在次级页）…")
            }
        }

        }


        // —— 外观与展示 ——
        CollapsibleSection(
            title = "界面外观",
            expanded = openSection == "ui",
            onToggle = { toggleSection("ui") }
        ) {
        Text("主题遮罩透明度：${"%.0f".format(scrim * 100)}%", style = MaterialTheme.typography.bodySmall)
        Slider(value = scrim, onValueChange = { scrim = it }, valueRange = 0.15f..0.85f)
        Text("卡片透明度：${"%.0f".format(cardA * 100)}%（越高越实）", style = MaterialTheme.typography.bodySmall)
        Slider(value = cardA, onValueChange = { cardA = it }, valueRange = 0.05f..0.65f)
        EnumDropdown("文字颜色", UiTextColor.entries, textColorOpt) { textColorOpt = it }
        EnumDropdown("板块美化（全局）", CardStyle.entries, cardStyleOpt) { cardStyleOpt = it }
        Text("液态玻璃 / 高斯模糊 / 雾化 / 无 — 所有页面板块同步", style = MaterialTheme.typography.bodySmall)

        }

        CollapsibleSection(
            title = "软件背景",
            expanded = openSection == "bg",
            onToggle = { toggleSection("bg") }
        ) {
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

        }

        CollapsibleSection(
            title = "关键词翻译（仅展示/日志）",
            expanded = openSection == "trans",
            onToggle = { toggleSection("trans") }
        ) {
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

        }


        // —— 安全与备份 ——
        CollapsibleSection(
            title = "PIN 锁定",
            expanded = openSection == "pin",
            onToggle = { toggleSection("pin") }
        ) {
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        }

        CollapsibleSection(
            title = "配置备份 / 恢复",
            expanded = openSection == "backup",
            onToggle = { toggleSection("backup") }
        ) {
        if (keysVisible) {
            Text(
                "备份除 PIN 外全部配置（含代理节点、超级代理订阅/端口、云备份、避让点等）。恢复始终保留本机 PIN。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "超级代理内核/配置路径为本机私有路径，换机后请重新选择文件导入。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "默认文件：${vm.backupFilePath()}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.backupConfig() },
                    modifier = Modifier.weight(1f)
                ) { Text("备份到应用目录") }
                OutlinedButton(
                    onClick = { vm.restoreConfigFromFile() }, // 默认路径见状态栏
                    modifier = Modifier.weight(1f)
                ) { Text("从默认文件恢复") }
            }

            OutlinedButton(
                onClick = { createDocLauncher.launch("jhsy_config_backup.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("备份到公共目录…") }
            OutlinedButton(
                onClick = { openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("从公共目录选择文件恢复…") }

            OutlinedButton(
                onClick = { showRestoreField = !showRestoreField },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (showRestoreField) "收起 JSON 粘贴" else "从剪贴板 JSON 恢复") }
            if (showRestoreField) {
                OutlinedTextField(
                    value = restoreJson,
                    onValueChange = { restoreJson = it },
                    label = { Text("粘贴备份 JSON") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    minLines = 4,
                    maxLines = 12
                )
                Button(
                    onClick = { vm.restoreConfigFromJson(restoreJson) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("确认恢复") }
            }

            OutlinedButton(
                onClick = { showRemoteConfig = !showRemoteConfig },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (showRemoteConfig) "收起远程配置" else "从远程 URL 导入配置") }
            if (showRemoteConfig) {
                OutlinedTextField(
                    value = remoteConfigUrl,
                    onValueChange = { remoteConfigUrl = it },
                    label = { Text("远程配置 JSON 地址 (http/https)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { vm.importRemoteConfig(remoteConfigUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("拉取并恢复") }
                Text(
                    "远程文件须为备份导出的 JSON 对象；不会覆盖本机 PIN。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LockedField("配置备份 / 恢复（请先解锁 PIN）")
        }

        }


        // —— 维护 ——
        CollapsibleSection(
            title = "缓存与日志",
            expanded = openSection == "cache",
            onToggle = { toggleSection("cache") }
        ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.clearWallpaperCache() }, modifier = Modifier.weight(1f)) { Text("清空缓存文件") }
            OutlinedButton(onClick = { vm.clearLogs() }, modifier = Modifier.weight(1f)) { Text("清空更换记录") }
        }

        }

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
                        dataSaverEnabled = dataSaver,
                        powerSaveBatteryThreshold = powerTh.coerceIn(5, 50),
                        superServiceEnabled = superSvc,
                        translateProvider = transProv,
                        translateApiKey = if (keysVisible) transKey.trim() else settings.translateApiKey,
                        translateSecret = if (keysVisible) transSecret.trim() else settings.translateSecret,
                        translateRegion = transRegion.trim().ifBlank { "global" },
                        uiScrimAlpha = scrim,
                        uiCardAlpha = cardA,
                        uiTextColor = textColorOpt,
                        cardStyle = cardStyleOpt,
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
                        localFallbackUseCache = localFbCache,
                        localFallbackCacheSkipNewest = localFbSkip,
                        locationAvoidEnabled = locAvoid,
                        amapApiKey = if (keysVisible) amapKey.trim() else settings.amapApiKey,
                        locationFallbackEnabled = locFb,
                        locationExtremeFallbackEnabled = locExtreme,
                        bgApiUrl = bgApi.trim(),
                        bgLocalPath = bgLocal.trim(),
                        bgMode = bgMode,
                        accelModeEnabled = accelOn,
                        accelPrivacyAccepted = accelPrivacy,
                        accelNodesRemoteUrl = accelNodesUrl.trim(),
                        proxyEnabled = proxyOn,
                        proxyType = if (keysVisible) proxyType else settings.proxyType,
                        proxyHost = if (keysVisible) proxyHost.trim() else settings.proxyHost,
                        proxyPort = if (keysVisible) (proxyPort.toIntOrNull() ?: 0) else settings.proxyPort,
                        proxyUser = if (keysVisible) proxyUser.trim() else settings.proxyUser,
                        proxyPassword = if (keysVisible) proxyPass else settings.proxyPassword,
                        proxySubUrl = if (keysVisible) proxySub.trim() else settings.proxySubUrl,
                        superProxyEnabled = superProxyOn,
                        superProxyBinPath = if (keysVisible) superBin.trim() else settings.superProxyBinPath,
                        superProxyConfigPath = if (keysVisible) superCfg.trim() else settings.superProxyConfigPath,
                        superProxySubUrl = if (keysVisible) superSub.trim() else settings.superProxySubUrl,
                        superProxyArgs = if (keysVisible) superArgs.trim() else settings.superProxyArgs,
                        superProxyLocalPort = superPort.toIntOrNull()?.coerceIn(1025, 65535)
                            ?: settings.superProxyLocalPort
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存设置")
        }
    }

    if (showAccelDialog) {
        AccelPrivacyDialog(
            countdown = accelCountdown,
            onCountdown = { accelCountdown = it },
            onAgree = {
                accelPrivacy = true
                accelOn = true
                proxyOn = false
                superProxyOn = false
                vm.stopSuperProxy()
                showAccelDialog = false
                vm.refreshAccelNodes(accelNodesUrl)
            },
            onDismiss = {
                showAccelDialog = false
                accelOn = false
            }
        )
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
            is CardStyle -> it.label
            is ProxyType -> it.label
            is ProxySelectMode -> it.label
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


@Composable
private fun AccelPrivacyDialog(
    countdown: Int,
    onCountdown: (Int) -> Unit,
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var left = 10
        onCountdown(left)
        while (left > 0) {
            kotlinx.coroutines.delay(1000)
            left--
            onCountdown(left)
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加速模式 · 安全与隐私声明") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请仔细阅读后再决定是否启用：", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. 加速模式会将本应用网络请求（含 Wallhaven 搜索与图片下载等）经内置或配置的中转节点转发。\n" +
                        "2. 中转方可能看到访问目标、时间与部分流量特征；请仅使用你信任或自建的节点。\n" +
                        "3. API Key 等若出现在请求中，中转方理论上可见，请自行评估风险。\n" +
                        "4. 与「网络代理 / 超级代理」互斥，启用后将关闭用户代理。\n" +
                        "5. 不保证可用性与速度；节点失效时回退系统直连。\n" +
                        "6. 请遵守当地法律法规与 Wallhaven 服务条款。"
                )
                Text(
                    if (countdown > 0) "请阅读满 10 秒后再同意（还剩 ${countdown} 秒）"
                    else "若你已理解风险，可点击同意启用。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onAgree, enabled = countdown <= 0) {
                Text(if (countdown > 0) "请等待 ${countdown}s" else "同意并启用")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("不同意") }
        }
    )
}

