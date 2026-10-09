package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kers.killove.jhsy.util.PastLifeAi

/**
 * 前世今生：独立 GlassCard 板块，折叠样式与 CollapsibleSection 一致。
 */
@Composable
fun PastLifeSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val state by vm.pastLifeState.collectAsState()
    val textColor = LocalUiTextColor.current
    var expanded by remember { mutableStateOf(true) }

    if (!settings.pastLifeEnabled) return
    val aiOk = PastLifeAi.canUse(settings)

    LaunchedEffect(settings.lastChangeAt, settings.pastLifeEnabled, settings.translateAiApiKey) {
        vm.refreshPastLife(forceAi = true)
    }

    GlassCard {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "前世今生",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (expanded) "收起 ▲" else "展开 ▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.65f)
                )
            }

            if (!expanded) return@Column

            if (!aiOk) {
                Text(
                    "请开启 AI 模式并配置 API Key 后生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.65f)
                )
                return@Column
            }

            PastLifeBlock(
                title = "前尘",
                kw = state.prevKw,
                zh = state.prevZh,
                body = state.past.ifBlank { "—" },
                textColor = textColor
            )
            PastLifeBlock(
                title = "今生",
                kw = state.currKw,
                zh = state.currZh,
                body = state.present.ifBlank {
                    if (state.currKw.isBlank()) "等待下次更换" else "生成中…"
                },
                textColor = textColor
            )
            PastLifeBlock(
                title = "来世",
                kw = state.nextKw,
                zh = state.nextZh,
                body = state.future.ifBlank { "—" },
                textColor = textColor
            )
        }
    }
}

@Composable
private fun PastLifeBlock(
    title: String,
    kw: String,
    zh: String,
    body: String,
    textColor: androidx.compose.ui.graphics.Color
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val label = when {
            kw.isBlank() -> title
            zh.isNotBlank() -> "$title · $kw（$zh）"
            else -> "$title · $kw"
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
        Text(body, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.85f))
    }
}
