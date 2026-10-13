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
import androidx.compose.material.icons.filled.Add
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

data class FallbackUrlEntry(
    val base: String = "",
    val params: List<String> = emptyList()
)

fun parseFallbackEntries(raw: String): List<FallbackUrlEntry> {
    val lines = raw.split('\n', ',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.isEmpty()) return listOf(FallbackUrlEntry())
    return lines.map { url ->
        val q = url.indexOf('?')
        if (q < 0) FallbackUrlEntry(base = url, params = emptyList())
        else {
            val base = url.substring(0, q)
            val params = url.substring(q + 1).split('&').map { it.trim() }.filter { it.isNotEmpty() }
            FallbackUrlEntry(base = base, params = params)
        }
    }
}

fun composeFallbackApiUrl(entries: List<FallbackUrlEntry>): String {
    return entries.mapNotNull { e ->
        val b = e.base.trim()
        if (b.isEmpty()) return@mapNotNull null
        val ps = e.params.map { it.trim() }.filter { it.isNotEmpty() }
        if (ps.isEmpty()) b
        else {
            val joined = ps.joinToString("&")
            if (b.contains('?')) "$b&$joined" else "$b?$joined"
        }
    }.joinToString("\n")
}

@Composable
fun FallbackUrlsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    var entries by remember(settings.fallbackApiUrl) {
        mutableStateOf(parseFallbackEntries(settings.fallbackApiUrl))
    }

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
            Text("兜底 API URL", style = MaterialTheme.typography.titleMedium, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "每个大框一条 URL；框后小加号追加参数（如 q=200）。失败时自动试下一条。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.75f)
        )
        entries.forEachIndexed { index, entry ->
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = entry.base,
                        onValueChange = { v ->
                            entries = entries.toMutableList().also {
                                it[index] = it[index].copy(base = v)
                            }
                        },
                        label = { Text("兜底 URL ${index + 1}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("https://… 支持 {width}{height}") }
                    )
                    IconButton(onClick = {
                        entries = entries.toMutableList().also {
                            it[index] = it[index].copy(params = it[index].params + "")
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加参数", tint = textColor)
                    }
                    if (entries.size > 1) {
                        IconButton(onClick = {
                            entries = entries.toMutableList().also { it.removeAt(index) }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "删除 URL", tint = textColor)
                        }
                    }
                }
                entry.params.forEachIndexed { pIdx, param ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = param,
                            onValueChange = { v ->
                                entries = entries.toMutableList().also {
                                    val ps = it[index].params.toMutableList()
                                    ps[pIdx] = v
                                    it[index] = it[index].copy(params = ps)
                                }
                            },
                            label = { Text("参数 ${pIdx + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("q=200") }
                        )
                        IconButton(onClick = {
                            entries = entries.toMutableList().also {
                                val ps = it[index].params.toMutableList()
                                ps.removeAt(pIdx)
                                it[index] = it[index].copy(params = ps)
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "删除参数", tint = textColor)
                        }
                    }
                }
            }
        }
        ThemeOutlinedButton(
            onClick = { entries = entries + FallbackUrlEntry() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ 添加兜底 URL") }
        Text(
            "响应可为直接图片、一行图片 URL、或含 path/url/image 的 JSON",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.65f)
        )
        ThemeButton(
            onClick = {
                vm.saveSettings(settings.copy(fallbackApiUrl = composeFallbackApiUrl(entries)))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存兜底 URL") }
    }
}
