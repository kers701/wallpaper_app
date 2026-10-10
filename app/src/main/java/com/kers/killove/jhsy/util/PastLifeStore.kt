package com.kers.killove.jhsy.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 前世今生状态。
 * - 普通：一条 timeline（unified）
 * - 桌面锁屏隔离分栏：home / lock 各一条，左三右三共六段
 */
object PastLifeStore {
    private const val FILE = "past_life_state.json"

    data class Track(
        val prevKw: String = "",
        val currKw: String = "",
        val nextKw: String = "",
        val prevZh: String = "",
        val currZh: String = "",
        val nextZh: String = "",
        val prevAux: List<String> = emptyList(),
        val currAux: List<String> = emptyList(),
        val past: String = "",
        val present: String = "",
        val future: String = ""
    )

    data class State(
        /** 非分栏时的单一时间轴（兼容旧字段） */
        val unified: Track = Track(),
        val home: Track = Track(),
        val lock: Track = Track(),
        val splitMode: Boolean = false,
        val needsNarrative: Boolean = false,
        val cycleAt: Long = 0L,
        val deepExplore: Boolean = false,
        val updatedAt: Long = 0L
    ) {
        // 兼容旧 UI：非分栏读写 unified
        val prevKw get() = if (splitMode) home.prevKw else unified.prevKw
        val currKw get() = if (splitMode) home.currKw else unified.currKw
        val nextKw get() = if (splitMode) home.nextKw else unified.nextKw
        val prevZh get() = if (splitMode) home.prevZh else unified.prevZh
        val currZh get() = if (splitMode) home.currZh else unified.currZh
        val nextZh get() = if (splitMode) home.nextZh else unified.nextZh
        val prevAux get() = if (splitMode) home.prevAux else unified.prevAux
        val currAux get() = if (splitMode) home.currAux else unified.currAux
        val past get() = if (splitMode) home.past else unified.past
        val present get() = if (splitMode) home.present else unified.present
        val future get() = if (splitMode) home.future else unified.future
    }

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

    private fun readTrack(o: JSONObject, prefix: String): Track {
        // 旧文件无 prefix 时用空前缀字段
        fun s(key: String) = o.optString(if (prefix.isEmpty()) key else "${prefix}_$key")
        fun list(key: String) = readList(o, if (prefix.isEmpty()) key else "${prefix}_$key")
        return Track(
            prevKw = s("prevKw"),
            currKw = s("currKw"),
            nextKw = s("nextKw"),
            prevZh = s("prevZh"),
            currZh = s("currZh"),
            nextZh = s("nextZh"),
            prevAux = list("prevAux"),
            currAux = list("currAux"),
            past = s("past"),
            present = s("present"),
            future = s("future")
        )
    }

    private fun writeTrack(o: JSONObject, prefix: String, t: Track) {
        fun put(key: String, value: String) {
            o.put(if (prefix.isEmpty()) key else "${prefix}_$key", value)
        }
        fun putL(key: String, list: List<String>) {
            o.put(if (prefix.isEmpty()) key else "${prefix}_$key", writeList(list))
        }
        put("prevKw", t.prevKw)
        put("currKw", t.currKw)
        put("nextKw", t.nextKw)
        put("prevZh", t.prevZh)
        put("currZh", t.currZh)
        put("nextZh", t.nextZh)
        putL("prevAux", t.prevAux)
        putL("currAux", t.currAux)
        put("past", t.past)
        put("present", t.present)
        put("future", t.future)
    }

    @Synchronized
    fun read(context: Context): State {
        return try {
            val f = file(context)
            if (!f.exists()) return State()
            val o = JSONObject(f.readText())
            val split = o.optBoolean("splitMode", false)
            // 兼容：旧版只有顶层 prevKw/currKw
            val unified = if (o.has("currKw") || o.has("unified_currKw")) {
                if (o.has("unified_currKw")) readTrack(o, "unified") else readTrack(o, "")
            } else Track()
            State(
                unified = unified,
                home = if (o.has("home_currKw")) readTrack(o, "home") else Track(),
                lock = if (o.has("lock_currKw")) readTrack(o, "lock") else Track(),
                splitMode = split,
                needsNarrative = o.optBoolean("needsNarrative", false),
                cycleAt = o.optLong("cycleAt", 0L),
                deepExplore = o.optBoolean("deepExplore", false),
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
            writeTrack(o, "unified", state.unified)
            writeTrack(o, "home", state.home)
            writeTrack(o, "lock", state.lock)
            // 兼容旧读取器
            writeTrack(o, "", state.unified)
            o.put("splitMode", state.splitMode)
            o.put("needsNarrative", state.needsNarrative)
            o.put("cycleAt", state.cycleAt)
            o.put("deepExplore", state.deepExplore)
            o.put("updatedAt", state.updatedAt)
            file(context).writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    private fun advanceTrack(
        old: Track,
        used: String,
        next: String?,
        imageTags: List<String>,
        deep: Boolean
    ): Track {
        val curr = used.trim()
        if (curr.isEmpty()) return old
        val aux = if (deep) {
            imageTags.map { it.trim() }.filter { it.isNotEmpty() }
                .filter { !it.equals(curr, ignoreCase = true) }
                .distinct()
                .take(12)
        } else emptyList()
        return Track(
            prevKw = old.currKw,
            currKw = curr,
            nextKw = next?.trim().orEmpty(),
            prevZh = "",
            currZh = "",
            nextZh = "",
            prevAux = if (deep) old.currAux else emptyList(),
            currAux = aux,
            past = "",
            present = "",
            future = ""
        )
    }

    /** 非分栏：单时间轴 */
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
        val unified = advanceTrack(old.unified, usedKeyword, nextKeyword, currImageTags, deepExplore)
        write(
            context,
            old.copy(
                unified = unified,
                splitMode = false,
                needsNarrative = unified.currKw.isNotBlank(),
                cycleAt = cycleAt,
                deepExplore = deepExplore,
                updatedAt = cycleAt
            )
        )
    }

    /** 分栏：桌面 + 锁屏各推进一轮 */
    @Synchronized
    fun onCycleCompleteSplit(
        context: Context,
        homeUsed: String,
        homeNext: String?,
        homeTags: List<String>,
        lockUsed: String,
        lockNext: String?,
        lockTags: List<String>,
        cycleAt: Long = System.currentTimeMillis(),
        deepExplore: Boolean = false
    ) {
        val old = read(context)
        val home = advanceTrack(old.home, homeUsed, homeNext, homeTags, deepExplore)
        val lock = advanceTrack(old.lock, lockUsed, lockNext, lockTags, deepExplore)
        write(
            context,
            old.copy(
                home = home,
                lock = lock,
                // 非分栏字段同步桌面，便于旧逻辑兜底
                unified = home,
                splitMode = true,
                needsNarrative = home.currKw.isNotBlank() || lock.currKw.isNotBlank(),
                cycleAt = cycleAt,
                deepExplore = deepExplore,
                updatedAt = cycleAt
            )
        )
    }
}
