package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 前世今生 AI：仅在 needsNarrative 时生成；每段 100～200 字；单次新对话。
 * 叙述中关键词必须写作「英文（中文翻译）」形式。
 */
object PastLifeAi {
    fun canUse(settings: AppSettings): Boolean =
        settings.pastLifeEnabled &&
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
        if (!force && !state.needsNarrative && state.present.isNotBlank()) {
            return state
        }
        if (!force && !state.needsNarrative) return state

        fun label(kw: String, zhHint: String): String {
            if (kw.isBlank()) return "（无）"
            val zh = zhMap[kw].orEmpty().ifBlank { zhHint }.trim()
            return if (zh.isNotBlank()) "$kw（$zh）" else kw
        }

        val a = label(state.currKw, state.currZh)
        val b = label(state.prevKw, state.prevZh)
        val c = label(state.nextKw, state.nextZh)
        val userMsg =
            "请根据下列带翻译的关键词写作。\n" +
                "本次关键词（今生）：$a\n" +
                "上一关键词（前尘对照）：$b\n" +
                "下一关键词（来世对照）：$c\n\n" +
                "要求：\n" +
                "1. 文中每次出现关键词时，必须写成「英文原词（中文翻译）」形式，例如 $a ，禁止只写英文不写括号内翻译。\n" +
                "2. 前尘、今生、来世各写一段，每段 100～200 字（汉字计）。\n" +
                "3. 严格按三行输出，不要额外说明：\n" +
                "前尘：...（写 $a 与 $b 的关系）\n" +
                "今生：...（描述 $a 是什么）\n" +
                "来世：...（写 $a 与 $c 的关系）"

        val content = AiOneShot.chat(
            settings = settings,
            userContent = userMsg,
            systemContent = "你是关键词关系叙述者。只输出前尘/今生/来世三行；每段 100～200 字；文中关键词必须保留「英文（中文）」格式。不要引用历史对话。",
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
            needsNarrative = false
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
                t.startsWith("前尘") -> {
                    past = t.removePrefix("前尘").trimStart('：', ':', ' ', '　').trim()
                }
                t.startsWith("今生") -> {
                    present = t.removePrefix("今生").trimStart('：', ':', ' ', '　').trim()
                }
                t.startsWith("来世") -> {
                    future = t.removePrefix("来世").trimStart('：', ':', ' ', '　').trim()
                }
            }
        }
        if (past.isBlank() && present.isBlank() && future.isBlank() && content.isNotBlank()) {
            present = content.trim().take(220)
        }
        return Triple(past, present, future)
    }
}
