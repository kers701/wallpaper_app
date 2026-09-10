package com.kers.killove.jhsy.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 虚妄模式：本轮跃迁湮灭后剩余关键词中，与本次使用关键词存在子串包含关系的词组进入虚妄。
 */
object IllusionStore {
    private const val FILE = "illusion_last_round.json"

    data class Round(
        val usedKeyword: String,
        val illusioned: List<String>,
        val remaining: List<String>,
        val at: Long
    )

    fun save(context: Context, usedKeyword: String, illusioned: List<String>, remaining: List<String>) {
        runCatching {
            val o = JSONObject()
            o.put("usedKeyword", usedKeyword)
            o.put("at", System.currentTimeMillis())
            val a = JSONArray()
            illusioned.forEach { a.put(it) }
            o.put("illusioned", a)
            val r = JSONArray()
            remaining.forEach { r.put(it) }
            o.put("remaining", r)
            context.applicationContext.filesDir.resolve(FILE).writeText(o.toString())
        }
    }

    fun load(context: Context): Round? {
        return runCatching {
            val f = context.applicationContext.filesDir.resolve(FILE)
            if (!f.exists()) return null
            val o = JSONObject(f.readText())
            val ill = buildList {
                val a = o.optJSONArray("illusioned") ?: return@buildList
                for (i in 0 until a.length()) add(a.optString(i))
            }
            val rem = buildList {
                val a = o.optJSONArray("remaining") ?: return@buildList
                for (i in 0 until a.length()) add(a.optString(i))
            }
            Round(o.optString("usedKeyword"), ill, rem, o.optLong("at"))
        }.getOrNull()
    }

    fun filterForJump(candidates: List<String>, usedKeyword: String): Pair<List<String>, List<String>> {
        val used = usedKeyword.trim().lowercase()
        if (used.isEmpty()) return emptyList<String>() to candidates
        val illusioned = mutableListOf<String>()
        val remain = mutableListOf<String>()
        for (c in candidates) {
            val low = c.lowercase()
            if (low.contains(used) || used.contains(low)) illusioned += c else remain += c
        }
        return illusioned to remain
    }
}
