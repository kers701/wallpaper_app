package com.kers.killove.jhsy.data.local

import android.content.Context
import com.kers.killove.jhsy.domain.WallpaperTarget
import org.json.JSONObject
import java.io.File

/**
 * 下一张壁纸预下载缓存（按桌面/锁屏/Both 分槽）。
 * 更换时若文件仍在则直接使用；成功更换后再预取下一张。
 */
class NextWallpaperStore(private val context: Context) {

    data class Slot(
        val target: String,
        val id: String,
        val path: String,
        val sourceUrl: String,
        val keyword: String,
        val category: String,
        val purity: String,
        val width: Int,
        val height: Int,
        val source: String,
        val readyAt: Long
    ) {
        fun file(): File = File(path)
        fun isReady(): Boolean = file().exists() && file().length() > 32L
    }

    private fun sp() =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dir(): File = File(context.filesDir, "next_queue").apply { mkdirs() }

    fun fileFor(target: WallpaperTarget): File =
        File(dir(), "next_${target.name.lowercase()}.jpg")

    fun get(target: WallpaperTarget): Slot? {
        val raw = sp().getString(keyMeta(target), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Slot(
                target = o.optString("target", target.name),
                id = o.getString("id"),
                path = o.getString("path"),
                sourceUrl = o.optString("sourceUrl", ""),
                keyword = o.optString("keyword", ""),
                category = o.optString("category", ""),
                purity = o.optString("purity", ""),
                width = o.optInt("width", 0),
                height = o.optInt("height", 0),
                source = o.optString("source", "wallhaven"),
                readyAt = o.optLong("readyAt", 0L)
            )
        }.getOrNull()
    }

    /** 取可用预缓存；不可用则清掉并返回 null */
    fun takeReady(target: WallpaperTarget): Slot? {
        val slot = get(target) ?: return null
        if (!slot.isReady()) {
            clear(target)
            return null
        }
        // 取出即清空 meta，文件由调用方在成功设置后可删或保留
        clearMeta(target)
        clearFail(target)
        return slot
    }

    fun put(slot: Slot) {
        val o = JSONObject()
            .put("target", slot.target)
            .put("id", slot.id)
            .put("path", slot.path)
            .put("sourceUrl", slot.sourceUrl)
            .put("keyword", slot.keyword)
            .put("category", slot.category)
            .put("purity", slot.purity)
            .put("width", slot.width)
            .put("height", slot.height)
            .put("source", slot.source)
            .put("readyAt", slot.readyAt)
        sp().edit().putString(keyMeta(WallpaperTarget.valueOf(slot.target)), o.toString()).apply()
        clearFail(WallpaperTarget.valueOf(slot.target))
    }

    fun clear(target: WallpaperTarget) {
        clearMeta(target)
        runCatching { fileFor(target).delete() }
        clearFail(target)
    }

    private fun clearMeta(target: WallpaperTarget) {
        sp().edit().remove(keyMeta(target)).apply()
    }

    fun hasReady(target: WallpaperTarget): Boolean = get(target)?.isReady() == true

    // —— 失败重试：最多再试 1 次，间隔 5 分钟 ——

    fun failCount(target: WallpaperTarget): Int =
        sp().getInt(keyFailCnt(target), 0)

    fun lastFailAt(target: WallpaperTarget): Long =
        sp().getLong(keyFailAt(target), 0L)

    fun markFail(target: WallpaperTarget) {
        val c = failCount(target) + 1
        sp().edit()
            .putInt(keyFailCnt(target), c)
            .putLong(keyFailAt(target), System.currentTimeMillis())
            .apply()
    }

    fun clearFail(target: WallpaperTarget) {
        sp().edit()
            .remove(keyFailCnt(target))
            .remove(keyFailAt(target))
            .apply()
    }

    /** 是否应发起 5 分钟后的唯一一次重试 */
    fun shouldRetryOnce(target: WallpaperTarget): Boolean {
        if (hasReady(target)) return false
        val c = failCount(target)
        if (c != 1) return false
        val at = lastFailAt(target)
        if (at <= 0L) return false
        return System.currentTimeMillis() - at >= RETRY_AFTER_MS
    }

    /** 已用尽重试（失败≥2）则等到下次更换再下载 */
    fun retriesExhausted(target: WallpaperTarget): Boolean = failCount(target) >= 2

    private fun keyMeta(t: WallpaperTarget) = "meta_${t.name}"
    private fun keyFailCnt(t: WallpaperTarget) = "fail_cnt_${t.name}"
    private fun keyFailAt(t: WallpaperTarget) = "fail_at_${t.name}"

    companion object {
        private const val PREFS = "jhsy_next_wallpaper"
        const val RETRY_AFTER_MS = 5 * 60 * 1000L
    }
}
