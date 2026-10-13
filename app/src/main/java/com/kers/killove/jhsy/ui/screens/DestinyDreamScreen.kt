package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel

@Composable
fun DestinyDreamScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    val context = LocalContext.current
    val jumpZh by vm.jumpKeywordsZh.collectAsState()
    val anniZh by vm.annihilationZh.collectAsState()
    val illuZh by vm.illusionZh.collectAsState()
    val liveJump by vm.liveJumpKeywords.collectAsState()
    val liveJumpIdx by vm.liveJumpIndex.collectAsState()
    val liveNatRet by vm.liveNaturalRetained.collectAsState()
    val liveNatRec by vm.liveNaturalRecommended.collectAsState()
    var jumpExpanded by remember { mutableStateOf(false) }
    val jumpList = if (liveJump.isNotEmpty()) liveJump else settings.jumpKeywords
    val jumpIdx = if (liveJump.isNotEmpty()) liveJumpIdx else settings.jumpKeywordIndex
    val natRet = if (liveNatRet.isNotEmpty()) liveNatRet else settings.naturalLawRetainedKeywords
    val natRec = if (liveNatRec.isNotEmpty()) liveNatRec else settings.naturalLawRecommendedKeywords

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
            Text("造化前尘&浮生一梦", style = MaterialTheme.typography.titleMedium, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }

        GlassCard {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { jumpExpanded = !jumpExpanded },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (settings.naturalLawEnabled) "跃迁关键词•自然法则" else "跃迁关键词",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
                Text(
                    when {
                        !settings.jumpModeEnabled -> "跃迁模式：关闭"
                        jumpList.isEmpty() -> "跃迁模式：开启 · 列表为空"
                        else -> (if (settings.naturalLawEnabled) {
                            val retained = natRet.ifEmpty {
                                if (natRec.isEmpty()) jumpList else emptyList()
                            }
                            "跃迁模式：开启 · 跃迁 ${retained.size} 个 · 自然 ${natRec.size} 个"
                        } else "跃迁模式：开启 · 共 ${jumpList.size} 个") +
                            if (jumpExpanded) " · 点击收起" else " · 点击展开"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f)
                )
                if (jumpList.isNotEmpty()) {
                    val list = jumpList
                    val idx = jumpIdx
                    val isolate = settings.isolateHomeLock && settings.target == com.kers.killove.jhsy.domain.WallpaperTarget.Both
                    val pastLifeState by vm.pastLifeState.collectAsState()
                    JumpKeywordsTimeline(
                        list = list,
                        index = idx,
                        isolate = isolate,
                        zhMap = jumpZh,
                        textColor = textColor,
                        pastLife = pastLifeState
                    )
                    if (jumpExpanded) {
                        if (settings.naturalLawEnabled) {
                            val retained = natRet.ifEmpty {
                                if (natRec.isEmpty()) list else emptyList()
                            }
                            val recommended = natRec
                            fun shown(words: List<String>) = words.joinToString("、") { w ->
                                val zh = jumpZh[w]
                                if (zh.isNullOrBlank()) w else "$w($zh)"
                            }
                            Text("跃迁：${shown(retained)}", color = textColor)
                            Text("自然：${shown(recommended)}", color = textColor)
                        } else {
                            val shown = list.joinToString("、") { w ->
                                val zh = jumpZh[w]
                                if (zh.isNullOrBlank()) w else "$w($zh)"
                            }
                            Text(shown, color = textColor)
                        }
                    }
                }
            }
        }

        if (settings.jumpModeEnabled && settings.annihilationModeEnabled) {
            var anniExpanded by remember { mutableStateOf(false) }
            val anniList = remember(settings.annihilationEpoch, settings.lastChangeAt) {
                com.kers.killove.jhsy.util.AnnihilationStore.lastRoundBlocked(context)
            }
            val anniAscended = remember(settings.annihilationEpoch, settings.lastChangeAt) {
                com.kers.killove.jhsy.util.AnnihilationStore.lastRoundAllAscended(context)
            }
            val anniHasRound = remember(settings.annihilationEpoch, settings.lastChangeAt) {
                com.kers.killove.jhsy.util.AnnihilationStore.hasLastRound(context)
            }
            GlassCard {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clickable(enabled = anniList.isNotEmpty()) { anniExpanded = !anniExpanded },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("湮灭关键词", style = MaterialTheme.typography.titleMedium, color = textColor)
                    Text(
                        when {
                            !anniHasRound -> "正在湮灭 · 第 ${settings.annihilationEpoch} 纪元 · 尚无本轮记录"
                            anniAscended || anniList.isEmpty() ->
                                "正在湮灭 · 第 ${settings.annihilationEpoch} 纪元 · 本次湮灭全员飞升"
                            else -> "正在湮灭 · 第 ${settings.annihilationEpoch} 纪元 · 本轮湮灭 ${anniList.size} 个" +
                                if (anniExpanded) " · 点击收起" else " · 点击展开"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.85f)
                    )
                    if (anniList.isNotEmpty() && anniExpanded) {
                        val shown = anniList.joinToString("、") { w ->
                            val zh = anniZh[w] ?: jumpZh[w]
                            if (zh.isNullOrBlank()) w else "$w($zh)"
                        }
                        Text(shown, color = textColor)
                    }
                }
            }
        }

        if (settings.jumpModeEnabled && settings.annihilationModeEnabled && settings.illusionModeEnabled) {
            var illuExpanded by remember { mutableStateOf(false) }
            val illuList = remember(settings.lastChangeAt, settings.illusionModeEnabled) {
                com.kers.killove.jhsy.util.IllusionStore.lastRoundIllusory(context)
            }
            val illuHas = remember(settings.lastChangeAt, settings.illusionModeEnabled) {
                com.kers.killove.jhsy.util.IllusionStore.hasLastRound(context)
            }
            val illuNone = remember(settings.lastChangeAt, settings.illusionModeEnabled) {
                com.kers.killove.jhsy.util.IllusionStore.lastRoundNone(context)
            }
            GlassCard {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clickable(enabled = illuHas && !illuNone && illuList.isNotEmpty()) { illuExpanded = !illuExpanded },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("虚妄关键词", style = MaterialTheme.typography.titleMedium, color = textColor)
                    Text(
                        when {
                            !illuHas -> "虚妄模式：开启 · 尚无本轮记录"
                            illuNone || illuList.isEmpty() -> "虚妄模式：开启 · 全员升华"
                            else -> "虚妄模式：开启 · 本轮虚妄 ${illuList.size} 个" +
                                if (illuExpanded) " · 点击收起" else " · 点击展开"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.85f)
                    )
                    if (illuHas && !illuNone && illuList.isNotEmpty() && illuExpanded) {
                        val shown = illuList.joinToString("、") { w ->
                            val zh = illuZh[w] ?: jumpZh[w]
                            if (zh.isNullOrBlank()) w else "$w（$zh）"
                        }
                        Text(shown, color = textColor)
                    }
                    Text(
                        "身前虚妄，身后亦是虚妄",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.65f)
                    )
                }
            }
        }
        PastLifeSection(vm)

    }
}
