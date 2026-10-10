package com.kers.killove.jhsy.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 前世今生：主词 + 可选深入探索辅助词（本图/上图标签）。
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
        /** 上图提取的辅助标签（深入探索） */
        val prevAux: List<String> = emptyList(),
        /** 本图提取的辅助标签（深入探索） */
        val currAux: List<String> = emptyList(),
        val past: String = "",
        val present: String = "",
        val future: String = "",
        val updatedAt: Long = 0L,
        val needsNarrative: Boolean = false,
        val cycleAt: Long = 0L,
        val deepExplore: Boolean = false
    )

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun readList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotEmpty()) add(s)
            }
        }
    }

    private fun writeList(list: List<String>): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(it) }
        return a
    }

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
                prevAux = readList(o, "prevAux"),
                currAux = readList(o, "currAux"),
                past = o.optString("past"),
                present = o.optString("present"),
                future = o.optString("future"),
                updatedAt = o.optLong("updatedAt", 0L),
                needsNarrative = o.optBoolean("needsNarrative", false),
                cycleAt = o.optLong("cycleAt", 0L),
                deepExplore = o.optBoolean("deepExplore", false)
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
                .put("prevAux", writeList(state.prevAux))
                .put("currAux", writeList(state.currAux))
                .put("past", state.past)
                .put("present", state.present)
                .put("future", state.future)
                .put("updatedAt", state.updatedAt)
                .put("needsNarrative", state.needsNarrative)
                .put("cycleAt", state.cycleAt)
                .put("deepExplore", state.deepExplore)
            file(context).writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    /**
     * 周期结束：主词推进；深入探索时 currAux=本图标签，prevAux=上周期的 currAux。
     */
    @Synchronized
    fun onCycleComplete(
        context: Context,
        usedKeyword: String,
        nextKeyword: String?,
        cycleAt: Long = System.currentTimeMillis(),
        currImageTags: List<String> = emptyList(),
        deepExplore: Boolean = false
    ) {
        val old = read(context)
        val curr = usedKeyword.trim()
        if (curr.isEmpty()) return
        val next = nextKeyword?.trim().orEmpty()
        val aux = if (deepExplore) {
            currImageTags.map { it.trim() }.filter { it.isNotEmpty() }
                .filter { !it.equals(curr, ignoreCase = true) }
                .distinct()
                .take(12)
        } else emptyList()
        write(
            context,
            State(
                prevKw = old.currKw,
                currKw = curr,
                nextKw = next,
                prevZh = "",
                currZh = "",
                nextZh = "",
                prevAux = if (deepExplore) old.currAux else emptyList(),
                currAux = aux,
                past = "",
                present = "",
                future = "",
                updatedAt = cycleAt,
                needsNarrative = true,
                cycleAt = cycleAt,
                deepExplore = deepExplore
            )
        )
    }
}
