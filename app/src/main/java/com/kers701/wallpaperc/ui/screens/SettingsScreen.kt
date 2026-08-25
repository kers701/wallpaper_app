package com.kers701.wallpaperc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers701.wallpaperc.data.prefs.SettingsRepository
import com.kers701.wallpaperc.domain.CategoryMode
import com.kers701.wallpaperc.domain.Purity
import com.kers701.wallpaperc.domain.ResolutionMode
import com.kers701.wallpaperc.domain.WallpaperTarget
import com.kers701.wallpaperc.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    var interval by remember(settings.intervalMinutes) {
        mutableFloatStateOf(settings.intervalMinutes.toFloat())
    }
    var purity by remember(settings.purity) { mutableStateOf(settings.purity) }
    var category by remember(settings.categoryMode) { mutableStateOf(settings.categoryMode) }
    var target by remember(settings.target) { mutableStateOf(settings.target) }
    var resMode by remember(settings.resolutionMode) { mutableStateOf(settings.resolutionMode) }
    var fgs by remember(settings.useForegroundService) { mutableStateOf(settings.useForegroundService) }
    var skipOff by remember(settings.skipWhenScreenOff) { mutableStateOf(settings.skipWhenScreenOff) }
    var minW by remember(settings.minWidth) { mutableStateOf(settings.minWidth.toString()) }
    var minH by remember(settings.minHeight) { mutableStateOf(settings.minHeight.toString()) }

    var apiKeysText by remember(settings.apiKeys) {
        mutableStateOf(settings.apiKeys.joinToString("\n"))
    }
    var keywordsText by remember(settings.keywords) {
        mutableStateOf(settings.keywords.joinToString("\n"))
    }
    var keywordsUrl by remember(settings.keywordsRemoteUrl) {
        mutableStateOf(settings.keywordsRemoteUrl)
    }
    var useKeywords by remember(settings.useKeywords) { mutableStateOf(settings.useKeywords) }

    var netFb by remember(settings.networkFallbackEnabled) {
        mutableStateOf(settings.networkFallbackEnabled)
    }
    var fallbackApi by remember(settings.fallbackApiUrl) {
        mutableStateOf(settings.fallbackApiUrl)
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

        RowSwitch("强制前台服务", fgs) { fgs = it }
        RowSwitch("息屏时跳过", skipOff) { skipOff = it }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("API 密钥（可多个，每行一个）", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = apiKeysText,
            onValueChange = { apiKeysText = it },
            label = { Text("Wallhaven API Keys") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            minLines = 3,
            maxLines = 8
        )
        Text("失败时会自动轮换到下一个密钥", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("关键词", style = MaterialTheme.typography.titleMedium)
        RowSwitch("启用关键词搜索", useKeywords) { useKeywords = it }
        OutlinedTextField(
            value = keywordsText,
            onValueChange = { keywordsText = it },
            label = { Text("本地关键词（每行一个）") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
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
        ) {
            Text("从远程导入并覆盖本地列表")
        }
        OutlinedButton(
            onClick = { vm.importKeywordsFromUrl(keywordsUrl, replace = false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("从远程导入并合并到本地")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("兜底策略", style = MaterialTheme.typography.titleMedium)
        RowSwitch("网络兜底（Wallhaven 失败 → 备用 API）", netFb) { netFb = it }
        OutlinedTextField(
            value = fallbackApi,
            onValueChange = { fallbackApi = it },
            label = { Text("兜底 API URL") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://… 支持 {width}{height}") },
            maxLines = 3
        )
        Text(
            "响应可以是：直接图片、一行图片 URL、或含 path/url/image 的 JSON",
            style = MaterialTheme.typography.bodySmall
        )

        RowSwitch("本地兜底（无网/失败时从目录随机）", localFb) { localFb = it }
        RowSwitch("强制本地模式（不访问网络）", forceLocal) { forceLocal = it }
        OutlinedTextField(
            value = localDir,
            onValueChange = { localDir = it },
            label = { Text("本地兜底目录（空=App 私有目录）") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("/storage/emulated/0/Pictures/Wallpapers") }
        )
        Text(
            "当前：${vm.localFallbackInfo()}",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = {
                vm.saveSettings(
                    settings.copy(
                        intervalMinutes = interval.toInt(),
                        purity = purity,
                        categoryMode = category,
                        target = target,
                        resolutionMode = resMode,
                        useForegroundService = fgs,
                        skipWhenScreenOff = skipOff,
                        minWidth = minW.toIntOrNull() ?: settings.minWidth,
                        minHeight = minH.toIntOrNull() ?: settings.minHeight,
                        apiKeys = SettingsRepository.splitLines(apiKeysText),
                        keywords = SettingsRepository.splitLines(keywordsText),
                        keywordsRemoteUrl = keywordsUrl.trim(),
                        useKeywords = useKeywords,
                        networkFallbackEnabled = netFb,
                        fallbackApiUrl = fallbackApi.trim(),
                        localFallbackEnabled = localFb,
                        forceLocalMode = forceLocal,
                        localFallbackDir = localDir.trim()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存设置")
        }
    }
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
