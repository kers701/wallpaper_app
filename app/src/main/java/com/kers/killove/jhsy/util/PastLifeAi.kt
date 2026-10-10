package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 前世今生 AI：可生成 unified 或 home/lock 分轨。
 */
object PastLifeAi {
    fun canUse(settings: AppSettings): Boolean =
        settings.pastLifeEnabled &&
            settings.jumpModeEnabled &&
            settings.translateAiMode &&
            settings.translateAiApiKey.isNotBlank()

    suspend fun refresh(
        context: Context,
        settings: AppSettings,
        state: PastLifeStore.State,
        zhMap: Map<String, String>,
        force: Boolean = false
    ): PastLifeStore.State {
        if (!canUse(settings)) return state
        if (!force && !state.needsNarrative && !needsGen(state)) return state
        if (!force && !state.needsNarrative) return state

        return if (state.splitMode) {
            val home = generateTrack(settings, state.home, zhMap, state.deepExplore, "桌面")
            val lock = generateTrack(settings, state.lock, zhMap, state.deepExplore, "锁屏")
            state.copy(
                home = home,
                lock = lock,
                unified = home,
                needsNarrative = false,
                updatedAt = System.currentTimeMillis()
            ).also { PastLifeStore.write(context, it) }
        } else {
            val u = generateTrack(settings, state.unified, zhMap, state.deepExplore, null)
            state.copy(
                unified = u,
                needsNarrative = false,
                updatedAt = System.currentTimeMillis()
            ).also { PastLifeStore.write(context, it) }
        }
    }

    private fun needsGen(state: PastLifeStore.State): Boolean {
        return if (state.splitMode) {
            (state.home.currKw.isNotBlank() && state.home.present.isBlank()) ||
                (state.lock.currKw.isNotBlank() && state.lock.present.isBlank())
        } else {
            state.unified.currKw.isNotBlank() && state.unified.present.isBlank()
        }
    }

    private suspend fun generateTrack(
        settings: AppSettings,
        track: PastLifeStore.Track,
        zhMap: Map<String, String>,
        deepExplore: Boolean,
        sideLabel: String?
    ): PastLifeStore.Track {
        if (track.currKw.isBlank()) return track
        if (track.present.isNotBlank() && track.past.isNotBlank()) return track

        fun label(kw: String, zhHint: String): String {
            if (kw.isBlank()) return "（无）"
            val zh = zhMap[kw].orEmpty().ifBlank { zhHint }.trim()
            return if (zh.isNotBlank()) "$kw（$zh）" else kw
        }

        fun auxLine(tags: List<String>): String {
            if (tags.isEmpty()) return "（无）"
            return tags.take(10).joinToString("、") { t ->
                val zh = zhMap[t].orEmpty()
                if (zh.isNotBlank()) "$t（$zh）" else t
            }
        }

        val a = label(track.currKw, track.currZh)
        val b = label(track.prevKw, track.prevZh)
        val c = label(track.nextKw, track.nextZh)
        val deep = deepExplore || settings.pastLifeDeepExplore
        val lenHint = if (deep) "每段字数 200～300 字（汉字计）" else "每段字数 100～200 字（汉字计）"
        val side = sideLabel?.let { "【$it】" }.orEmpty()

        val deepBlock = if (deep) {
            "深入探索已开启：以主词为主，辅助词仅作补充。\n" +
                "今生主词：$a\n今生辅助词：${auxLine(track.currAux)}\n" +
                "前尘主词：$b\n前尘辅助词：${auxLine(track.prevAux)}\n" +
                "来世主词：$c\n"
        } else {
            "今生：$a\n前尘：$b\n来世：$c\n"
        }

        val userMsg =
            "${side}请根据下列带翻译的关键词写作。\n" +
                deepBlock +
                "要求：\n" +
                "1. 主词写成「英文（中文）」形式。\n" +
                "2. 前尘、今生、来世各一段，$lenHint。\n" +
                "3. 严格三行：\n前尘：...\n今生：...\n来世：..."

        val content = AiOneShot.chat(
            settings = settings,
            userContent = userMsg,
            systemContent = "你是关键词关系叙述者。只输出前尘/今生/来世三行。不要引用历史对话。",
            temperature = 0.4
        ) ?: return track

        val (past, present, future) = parseThree(content)
        return track.copy(
            prevZh = zhMap[track.prevKw].orEmpty().ifBlank { track.prevZh },
            currZh = zhMap[track.currKw].orEmpty().ifBlank { track.currZh },
            nextZh = zhMap[track.nextKw].orEmpty().ifBlank { track.nextZh },
            past = past,
            present = present,
            future = future
        )
    }

    private fun parseThree(content: String): Triple<String, String, String> {
        var past = ""
        var present = ""
        var future = ""
        for (line in content.lines()) {
            val t = line.trim()
            when {
                t.startsWith("前尘") -> past = t.removePrefix("前尘").trimStart('：', ':', ' ', '　').trim()
                t.startsWith("今生") -> present = t.removePrefix("今生").trimStart('：', ':', ' ', '　').trim()
                t.startsWith("来世") -> future = t.removePrefix("来世").trimStart('：', ':', ' ', '　').trim()
            }
        }
        if (past.isBlank() && present.isBlank() && future.isBlank() && content.isNotBlank()) {
            present = content.trim().take(320)
        }
        return Triple(past, present, future)
    }
}
