package com.kers.killove.jhsy.util

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 前世今生：跨进程记录上次/本次/下次关键词，以及 AI 叙述缓存。
 * needsNarrative=true 表示本更换周期结束、待生成叙述；生成后清零，避免反复打开 App 重复请求。
 */
object PastLifeStore {
    private const val FILE = "past_life_state.json"

    data class State(
        val prevKw: String = "",
        val currKw: String = "",
        val nextKw: String = "",
        val prevZh: String = "",
        val currZh: String = "",
        val nextZh: String = "",
        val past: String = "",
        val present: String = "",
        val future: String = "",
        val updatedAt: Long = 0L,
        /** 本更换周期是否尚需生成 AI 叙述（周期结束置 true，生成后 false） */
        val needsNarrative: Boolean = false,
        /** 对应更换周期时钟，用于去重 */
        val cycleAt: Long = 0L
    )

    private fun file(context: Context) = File(context.filesDir, FILE)

    @Synchronized
    fun read(context: Context): State {
        return try {
            val f = file(context)
            if (!f.exists()) return State()
            val o = JSONObject(f.readText())
            State(
                prevKw = o.optString("prevKw"),
                currKw = o.optString("currKw"),
                nextKw = o.optString("nextKw"),
                prevZh = o.optString("prevZh"),
                currZh = o.optString("currZh"),
                nextZh = o.optString("nextZh"),
                past = o.optString("past"),
                present = o.optString("present"),
                future = o.optString("future"),
                updatedAt = o.optLong("updatedAt", 0L),
                needsNarrative = o.optBoolean("needsNarrative", false),
                cycleAt = o.optLong("cycleAt", 0L)
            )
        } catch (_: Exception) {
            State()
        }
    }

    @Synchronized
    fun write(context: Context, state: State) {
        try {
            val o = JSONObject()
                .put("prevKw", state.prevKw)
                .put("currKw", state.currKw)
                .put("nextKw", state.nextKw)
                .put("prevZh", state.prevZh)
                .put("currZh", state.currZh)
                .put("nextZh", state.nextZh)
                .put("past", state.past)
                .put("present", state.present)
                .put("future", state.future)
                .put("updatedAt", state.updatedAt)
                .put("needsNarrative", state.needsNarrative)
                .put("cycleAt", state.cycleAt)
            file(context).writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    /**
     * 一次完整更换周期结束时调用（隔离：桌面+锁屏都完成后再调）。
     * 清空旧叙述并标记 needsNarrative，供 AI 生成一次。
     */
    @Synchronized
    fun onCycleComplete(
        context: Context,
        usedKeyword: String,
        nextKeyword: String?,
        cycleAt: Long = System.currentTimeMillis()
    ) {
        val old = read(context)
        val curr = usedKeyword.trim()
        if (curr.isEmpty()) return
        val next = nextKeyword?.trim().orEmpty()
        write(
            context,
            State(
                prevKw = old.currKw,
                currKw = curr,
                nextKw = next,
                prevZh = "",
                currZh = "",
                nextZh = "",
                past = "",
                present = "",
                future = "",
                updatedAt = cycleAt,
                needsNarrative = true,
                cycleAt = cycleAt
            )
        )
    }

    @Synchronized
    fun markNarrativeDone(context: Context, state: State) {
        write(context, state.copy(needsNarrative = false, updatedAt = System.currentTimeMillis()))
    }
}
