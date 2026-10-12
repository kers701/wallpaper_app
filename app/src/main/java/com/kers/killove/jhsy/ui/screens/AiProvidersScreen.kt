package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.kers.killove.jhsy.domain.AiProviderConfig
import com.kers.killove.jhsy.domain.AiRouter
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import java.util.UUID

@Composable
fun AiProvidersScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenModelGroup: () -> Unit = {}
) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    var list by remember(settings.aiProvidersJson, settings.translateAiApiKey) {
        mutableStateOf(AiRouter.providers(settings).map {
            if (it.id == "legacy") it.copy(id = UUID.randomUUID().toString(), name = it.name.ifBlank { "默认" })
            else it
        }.toMutableList())
    }
    var editing by remember { mutableStateOf<AiProviderConfig?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

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
            Text("AI 提供商配置", style = MaterialTheme.typography.titleMedium, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "可配置多个提供商。保存后点「拉取模型」写入模型组；在模型组中设置优先级与生效项目。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f)
        )
        ThemeOutlinedButton(
            onClick = {
                editing = AiProviderConfig(
                    name = "配置 ${list.size + 1}",
                    baseUrl = "https://api.openai.com/v1"
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("添加提供商配置") }

        ThemeOutlinedButton(
            onClick = onOpenModelGroup,
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开模型组…") }

        list.forEach { p ->
            GlassCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        p.name.ifBlank { "未命名" } + if (p.enabled) "" else "（已禁用）",
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "URL: ${p.baseUrl}\n模型名行数: ${p.modelNames().size} · Key: ${if (p.apiKey.isBlank()) "空" else "已填"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeOutlinedButton(onClick = { editing = p }) { Text("编辑") }
                        ThemeOutlinedButton(onClick = { vm.fetchModelsForProvider(p.id) }) { Text("拉取模型") }
                        ThemeOutlinedButton(onClick = { deleteId = p.id }) { Text("删除") }
                    }
                }
            }
        }

        ThemeButton(
            onClick = { vm.saveAiProviders(list) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存全部提供商") }
    }

    editing?.let { draft0 ->
        var name by remember(draft0.id) { mutableStateOf(draft0.name) }
        var key by remember(draft0.id) { mutableStateOf(draft0.apiKey) }
        var url by remember(draft0.id) { mutableStateOf(draft0.baseUrl) }
        var models by remember(draft0.id) { mutableStateOf(draft0.modelNamesText) }
        var enabled by remember(draft0.id) { mutableStateOf(draft0.enabled) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (list.any { it.id == draft0.id }) "编辑配置" else "新建配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("配置名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = models,
                        onValueChange = { models = it },
                        label = { Text("模型名（一行一个，可选手写）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8
                    )
                    RowSwitch(title = "启用", checked = enabled, onChecked = { enabled = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    val next = draft0.copy(
                        name = name.trim().ifBlank { "未命名" },
                        apiKey = key.trim(),
                        baseUrl = url.trim().ifBlank { "https://api.openai.com/v1" },
                        modelNamesText = models,
                        enabled = enabled,
                        updatedAt = now
                    )
                    val idx = list.indexOfFirst { it.id == draft0.id }
                    list = if (idx >= 0) {
                        list.toMutableList().also { it[idx] = next }
                    } else {
                        (list + next).toMutableList()
                    }
                    editing = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("确认删除") },
            text = { Text("删除后模型组中关联该提供商的条目仍保留，可手动清理。") },
            confirmButton = {
                TextButton(onClick = {
                    list = list.filter { it.id != id }.toMutableList()
                    deleteId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("取消") }
            }
        )
    }
}
