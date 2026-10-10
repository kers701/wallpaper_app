package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 前世今生 AI：needsNarrative 时生成；单次新对话。
 * 普通：每段 100～200 字；深入探索：主词+辅助词，每段 200～300 字。
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
        if (state.currKw.isBlank()) return state
        if (!force && !state.needsNarrative && state.present.isNotBlank()) return state
        if (!force && !state.needsNarrative) return state

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

        val a = label(state.currKw, state.currZh)
        val b = label(state.prevKw, state.prevZh)
        val c = label(state.nextKw, state.nextZh)
        val deep = state.deepExplore || settings.pastLifeDeepExplore
        val lenHint = if (deep) "每段字数 200～300 字（汉字计）" else "每段字数 100～200 字（汉字计）"

        val deepBlock = if (deep) {
            "深入探索已开启：叙述以主词为主，辅助词仅作场景/构图补充，不要喧宾夺主。\n" +
                "今生主词：$a\n今生辅助词（本图标签）：${auxLine(state.currAux)}\n" +
                "前尘主词：$b\n前尘辅助词（上图标签）：${auxLine(state.prevAux)}\n" +
                "来世主词：$c（尚无图片，无辅助词）\n"
        } else {
            "本次关键词（今生）：$a\n上一关键词（前尘）：$b\n下一关键词（来世）：$c\n"
        }

        val userMsg =
            "请根据下列带翻译的关键词写作。\n" +
                deepBlock +
                "要求：\n" +
                "1. 文中主词必须写成「英文（中文）」形式，例如 $a。\n" +
                "2. 前尘、今生、来世各写一段，$lenHint。\n" +
                "3. 严格按三行输出：\n" +
                "前尘：...（$a 与 $b 的关系" + (if (deep) "，可参考双方辅助词" else "") + "）\n" +
                "今生：...（描述 $a 是什么" + (if (deep) "，可参考本图辅助词" else "") + "）\n" +
                "来世：...（$a 与 $c 的关系）"

        val content = AiOneShot.chat(
            settings = settings,
            userContent = userMsg,
            systemContent = "你是关键词关系叙述者。只输出前尘/今生/来世三行；遵守字数；主词用「英文（中文）」；深入探索时以主词为主、辅助词为辅。不要引用历史对话。",
            temperature = 0.4
        ) ?: return state

        val (past, present, future) = parseThree(content)
        val done = state.copy(
            prevZh = zhMap[state.prevKw].orEmpty().ifBlank { state.prevZh },
            currZh = zhMap[state.currKw].orEmpty().ifBlank { state.currZh },
            nextZh = zhMap[state.nextKw].orEmpty().ifBlank { state.nextZh },
            past = past,
            present = present,
            future = future,
            updatedAt = System.currentTimeMillis(),
            needsNarrative = false,
            deepExplore = deep
        )
        PastLifeStore.write(context, done)
        return done
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
