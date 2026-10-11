package com.kers.killove.jhsy.util

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 跃迁 / 湮灭 / 虚妄 关键词中文翻译缓存。
 * 周期 = 一次壁纸设置完成 → 下一次壁纸设置完成；新周期开始时清空上周期缓存。
 */
object KeywordZhCacheStore {
    private const val FILE = "keyword_zh_cache.json"

    data class Snapshot(
        /** 当前周期 ID：通常为该周期壁纸设置完成时的时间戳 */
        val cycleId: Long,
        val map: Map<String, String>
    )

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun load(ctx: Context): Snapshot {
        return try {
            val f = file(ctx)
            if (!f.isFile || f.length() == 0L) return Snapshot(0L, emptyMap())
            val o = JSONObject(f.readText())
            // 兼容旧版 epoch 字段
            val cycleId = when {
                o.has("cycleId") -> o.optLong("cycleId", 0L)
                o.has("epoch") -> o.optLong("epoch", 0L)
                else -> 0L
            }
            val mapObj = o.optJSONObject("map") ?: JSONObject()
            val map = buildMap {
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = mapObj.optString(k, "").trim()
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                }
            }
            Snapshot(cycleId, map)
        } catch (_: Exception) {
            Snapshot(0L, emptyMap())
        }
    }

    fun save(ctx: Context, cycleId: Long, map: Map<String, String>) {
        try {
            val o = JSONObject()
            o.put("cycleId", cycleId)
            val m = JSONObject()
            map.forEach { (k, v) ->
                val kk = k.trim()
                val vv = v.trim()
                if (kk.isNotEmpty() && vv.isNotEmpty()) m.put(kk, vv)
            }
            o.put("map", m)
            file(ctx).writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    /** 壁纸设置完成进入新周期：清空上周期翻译，绑定新 cycleId */
    fun beginNewCycle(ctx: Context, cycleId: Long) {
        val id = if (cycleId > 0L) cycleId else System.currentTimeMillis()
        clear(ctx)
        save(ctx, id, emptyMap())
    }

    fun clear(ctx: Context) {
        try {
            file(ctx).delete()
        } catch (_: Exception) {
        }
    }
}
