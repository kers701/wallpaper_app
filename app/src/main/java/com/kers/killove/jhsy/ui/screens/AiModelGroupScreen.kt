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
import androidx.compose.material3.FilterChip
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
import com.kers.killove.jhsy.domain.AiFeature
import com.kers.killove.jhsy.domain.AiModelEntry
import com.kers.killove.jhsy.domain.AiRouter
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel

@Composable
fun AiModelGroupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    val providers = remember(settings.aiProvidersJson, settings.translateAiApiKey) {
        AiRouter.providers(settings).associateBy { it.id }
    }
    var list by remember(settings.aiModelsJson) {
        mutableStateOf(AiRouter.models(settings).sortedWith(compareBy({ it.priority }, { -it.updatedAt })).toMutableList())
    }
    var editing by remember { mutableStateOf<AiModelEntry?>(null) }
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
            Text("模型组", style = MaterialTheme.typography.titleMedium, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "集合所有拉取/手写模型。优先级 0–100，数字越小越优先；同一功能多模型冲突时按优先级，失败自动下一档。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f)
        )

        list.forEach { m ->
            val pname = providers[m.providerId]?.name ?: m.providerId.ifBlank { "?" }
            GlassCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        m.modelName + if (m.tag.isNotBlank()) " · ${m.tag}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                    Text(
                        "提供商: $pname · 优先级 ${m.priority}" +
                            if (!m.enabled) " · 已禁用" else "" +
                            if (m.failedUntil > System.currentTimeMillis()) " · 冷却中" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                    Text(
                        "生效: " + m.features.mapNotNull { AiFeature.fromCode(it)?.label }.joinToString("、").ifBlank { "无" },
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeOutlinedButton(onClick = { editing = m }) { Text("编辑") }
                        ThemeOutlinedButton(onClick = { deleteId = m.id }) { Text("删除") }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Text("暂无模型。请先在提供商配置中拉取或手写模型名。", color = textColor.copy(alpha = 0.7f))
        }

        ThemeButton(
            onClick = { vm.saveAiModels(list) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存模型组") }
    }

    editing?.let { draft0 ->
        var priorityText by remember(draft0.id) { mutableStateOf(draft0.priority.toString()) }
        var tag by remember(draft0.id) { mutableStateOf(draft0.tag) }
        var enabled by remember(draft0.id) { mutableStateOf(draft0.enabled) }
        var feats by remember(draft0.id) { mutableStateOf(draft0.features.toMutableSet()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑模型 · ${draft0.modelName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priorityText,
                        onValueChange = { priorityText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("优先级 0–100（越小越优先）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("自定义标签") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RowSwitch(title = "启用", checked = enabled, onChecked = { enabled = it })
                    Text("生效项目", style = MaterialTheme.typography.labelMedium)
                    AiFeature.entries.forEach { f ->
                        FilterChip(
                            selected = feats.contains(f.code),
                            onClick = {
                                feats = feats.toMutableSet().also {
                                    if (it.contains(f.code)) it.remove(f.code) else it.add(f.code)
                                }
                            },
                            label = { Text(f.label) }
                        )
                    }
                    ThemeOutlinedButton(onClick = {
                        // 清除失败冷却
                        val idx = list.indexOfFirst { it.id == draft0.id }
                        if (idx >= 0) {
                            list = list.toMutableList().also {
                                it[idx] = it[idx].copy(failedUntil = 0L)
                            }
                        }
                    }) { Text("清除失败冷却") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pr = priorityText.toIntOrNull()?.coerceIn(0, 100) ?: 50
                    val next = draft0.copy(
                        priority = pr,
                        tag = tag.trim(),
                        enabled = enabled,
                        features = feats.ifEmpty { setOf(AiFeature.GENERAL.code) },
                        updatedAt = System.currentTimeMillis()
                    )
                    val idx = list.indexOfFirst { it.id == draft0.id }
                    if (idx >= 0) {
                        list = list.toMutableList().also { it[idx] = next }
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
            title = { Text("确认删除该模型？") },
            text = { Text("仅从模型组移除，不影响提供商配置。") },
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
