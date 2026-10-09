package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import com.kers.killove.jhsy.util.PastLifeAi

/**
 * 前世今生：GlassCard 独立板块；点击展开逻辑与跃迁/虚妄一致（无箭头）。
 * 仅在本周期 needsNarrative 时请求 AI，打开 App 不重复生成。
 */
@Composable
fun PastLifeSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val state by vm.pastLifeState.collectAsState()
    val textColor = LocalUiTextColor.current
    var expanded by remember { mutableStateOf(false) }

    if (!settings.pastLifeEnabled) return
    val aiOk = PastLifeAi.canUse(settings)

    // 只读缓存；若本周期需要叙述则生成一次（不因重组反复 force）
    LaunchedEffect(settings.lastChangeAt, settings.pastLifeEnabled, state.needsNarrative, state.cycleAt) {
        vm.refreshPastLife(forceAi = false)
    }

    GlassCard {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("前世今生", style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(
                when {
                    !aiOk -> "前世今生：开启 · 需配置 AI"
                    state.currKw.isBlank() -> "前世今生：开启 · 等待下次更换" +
                        if (expanded) " · 点击收起" else " · 点击展开"
                    state.needsNarrative || (state.present.isBlank() && state.currKw.isNotBlank()) ->
                        "前世今生：开启 · 生成本期叙述中…" +
                            if (expanded) " · 点击收起" else " · 点击展开"
                    else -> "前世今生：开启 · 本期已更新" +
                        if (expanded) " · 点击收起" else " · 点击展开"
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.85f)
            )

            if (!expanded) return@Column

            if (!aiOk) {
                Text(
                    "请开启 AI 模式并配置 API Key 后生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.65f)
                )
                return@Column
            }

            Spacer(Modifier.height(4.dp))
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
