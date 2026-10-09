package com.kers.killove.jhsy.util

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 前世今生：跨进程记录上次/本次/下次关键词，以及 AI 叙述缓存。
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
        val updatedAt: Long = 0L
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
                updatedAt = o.optLong("updatedAt", 0L)
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
            file(context).writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    /** 更换成功后推进关键词三元组；清空叙述等 UI/服务侧重新问 AI。 */
    @Synchronized
    fun onWallpaperKeyword(context: Context, usedKeyword: String, nextKeyword: String?) {
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
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
