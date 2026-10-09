package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 前世今生 AI：每次刷新均为独立单次对话，不携带历史 messages。
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
        zhMap: Map<String, String>
    ): PastLifeStore.State {
        if (!canUse(settings)) return state
        if (state.currKw.isBlank()) return state

        fun label(kw: String): String {
            if (kw.isBlank()) return "（无）"
            val zh = zhMap[kw].orEmpty().ifBlank {
                when (kw) {
                    state.prevKw -> state.prevZh
                    state.currKw -> state.currZh
                    state.nextKw -> state.nextZh
                    else -> ""
                }
            }
            return if (zh.isNotBlank()) "$kw（$zh）" else kw
        }

        val a = label(state.currKw)
        val b = label(state.prevKw)
        val c = label(state.nextKw)
        val userMsg =
            "关键词$a 是什么？与关键词$b 的关系（前尘）与关键词$c 的关系（来世），简要描述，字数控制在200内。\n" +
                "请严格按三行输出：\n前尘：...\n今生：...\n来世：..."

        // 全新对话，不追加任何历史
        val content = AiOneShot.chat(
            settings = settings,
            userContent = userMsg,
            systemContent = "你是简练的关键词关系叙述者。只输出前尘/今生/来世三行，总字数约200以内。不要引用或依赖任何历史对话。",
            temperature = 0.4
        ) ?: return state

        val (past, present, future) = parseThree(content)
        return state.copy(
            past = past,
            present = present,
            future = future,
            updatedAt = System.currentTimeMillis()
        ).also { PastLifeStore.write(context, it) }
    }

    private fun parseThree(content: String): Triple<String, String, String> {
        var past = ""
        var present = ""
        var future = ""
        for (line in content.lines()) {
            val t = line.trim()
            when {
                t.startsWith("前尘") -> past = t.substringAfter("：").substringAfter(":").trim().ifBlank {
                    t.replace(Regex("^前尘\\s*[:：]?\\s*"), "").trim()
                }
                t.startsWith("今生") -> present = t.substringAfter("：").substringAfter(":").trim().ifBlank {
                    t.replace(Regex("^今生\\s*[:：]?\\s*"), "").trim()
                }
                t.startsWith("来世") -> future = t.substringAfter("：").substringAfter(":").trim().ifBlank {
                    t.replace(Regex("^来世\\s*[:：]?\\s*"), "").trim()
                }
            }
        }
        if (past.isBlank() && present.isBlank() && future.isBlank() && content.isNotBlank()) {
            present = content.trim().take(200)
        }
        return Triple(past, present, future)
    }
}
