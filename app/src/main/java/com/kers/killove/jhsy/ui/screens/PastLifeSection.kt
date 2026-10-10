package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import com.kers.killove.jhsy.util.PastLifeAi
import com.kers.killove.jhsy.util.PastLifeStore

/**
 * 前世今生：点击打开弹层；分栏时左右滑动切换桌面/锁屏。
 * 弹层使用 GlassCard，跟随全局板块美化。
 */
@Composable
fun PastLifeSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val state by vm.pastLifeState.collectAsState()
    val textColor = LocalUiTextColor.current
    var showDetail by remember { mutableStateOf(false) }

    if (!settings.pastLifeEnabled) return
    val aiOk = PastLifeAi.canUse(settings)
    val split = state.splitMode && settings.pastLifeIsolateSplit && settings.isolateHomeLock

    LaunchedEffect(settings.lastChangeAt, settings.pastLifeEnabled, state.needsNarrative, state.cycleAt) {
        vm.refreshPastLife(forceAi = false)
    }

    GlassCard {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { showDetail = true }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("前世今生", style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(
                when {
                    !aiOk -> "前世今生：开启 · 需配置 AI · 点击查看"
                    split -> "前世今生：分栏 · 点击查看（左右滑动切换桌面/锁屏）"
                    state.currKw.isBlank() && state.unified.currKw.isBlank() ->
                        "前世今生：开启 · 等待下次更换 · 点击查看"
                    state.needsNarrative ->
                        "前世今生：开启 · 生成本期叙述中… · 点击查看"
                    else -> "前世今生：开启 · 本期已更新 · 点击查看"
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }

    if (showDetail) {
        Dialog(
            onDismissRequest = { showDetail = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("前世今生", style = MaterialTheme.typography.titleMedium, color = textColor)
                        TextButton(onClick = { showDetail = false }) {
                            Text("关闭", color = textColor)
                        }
                    }

                    if (!aiOk) {
                        Text(
                            "请开启跃迁、AI 模式并配置 API Key 后生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.65f)
                        )
                    } else if (split) {
                        val pagerState = rememberPagerState(pageCount = { 2 })
                        Text(
                            if (pagerState.currentPage == 0) "桌面 · 左右滑动切换锁屏"
                            else "锁屏 · 左右滑动切换桌面",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.75f)
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                        ) { page ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val track = if (page == 0) state.home else state.lock
                                Text(
                                    if (page == 0) "桌面" else "锁屏",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = textColor
                                )
                                TrackBlocks(track, textColor)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                if (pagerState.currentPage == 0) "● ○" else "○ ●",
                                color = textColor.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TrackBlocks(state.unified, textColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackBlocks(track: PastLifeStore.Track, textColor: androidx.compose.ui.graphics.Color) {
    PastLifeBlock("前尘", track.prevKw, track.prevZh, track.past.ifBlank { "—" }, textColor)
    PastLifeBlock(
        "今生",
        track.currKw,
        track.currZh,
        track.present.ifBlank {
            if (track.currKw.isBlank()) "等待下次更换" else "生成中…"
        },
        textColor
    )
    PastLifeBlock("来世", track.nextKw, track.nextZh, track.future.ifBlank { "—" }, textColor)
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
