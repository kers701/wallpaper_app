package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel

@Composable
fun ApiKeysScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenWallhavenSetup: () -> Unit = {}
) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    var fields by remember(settings.apiKeys) {
        mutableStateOf(settings.apiKeys.map { it }.ifEmpty { listOf("") })
    }
    var showSetup by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Wallhaven API 密钥", style = MaterialTheme.typography.titleMedium, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "每个输入框一个密钥；下方加号追加。失败时自动轮换到下一个。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.75f)
        )
        fields.forEachIndexed { index, keyVal ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = keyVal,
                    onValueChange = { v ->
                        fields = fields.toMutableList().also { it[index] = v }
                    },
                    label = { Text("API Key ${index + 1}") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (fields.size > 1) {
                    IconButton(onClick = {
                        fields = fields.toMutableList().also { it.removeAt(index) }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "删除", tint = textColor)
                    }
                }
            }
        }
        ThemeOutlinedButton(
            onClick = { fields = fields + "" },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ 添加密钥") }
        ThemeOutlinedButton(
            onClick = { showSetup = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("自动注册并获取 Wallhaven API Key") }
        ThemeButton(
            onClick = {
                val keys = fields.map { it.trim() }.filter { it.isNotEmpty() }
                vm.saveSettings(settings.copy(apiKeys = keys))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存密钥") }
    }

    if (showSetup) {
        WallhavenApiSetupDialog(
            onDismiss = { showSetup = false },
            onSaveKey = {
                vm.addWallhavenApiKey(it)
                showSetup = false
            }
        )
    }
}
