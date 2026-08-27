package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 主进程写、服务进程读的轻量桥接。
 * 独立进程无法安全共用 DataStore，故用 SharedPreferences 同步关键开关。
 *
 * 另提供自动更换互斥：避免 FGS 循环与 WorkManager/亮屏 同时各跑一轮 changeOnce
 * （隔离模式下会变成 4 条记录）。
 */
object ProcessBridgePrefs {
    private const val NAME = "jhsy_bridge"

    /** 自动更换最短间隔防抖（毫秒）：一轮隔离桌面+锁屏约需十余秒 */
    private const val AUTO_CHANGE_DEBOUNCE_MS = 90_000L

    /** changing 标记超时，避免异常退出后永远锁死 */
    private const val CHANGING_STALE_MS = 4 * 60_000L

    fun sync(context: Context, s: AppSettings) {
        context.applicationContext
            .getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", s.enabled)
            .putBoolean("super_service", s.superServiceEnabled)
            .putBoolean("use_fgs", s.useForegroundService)
            .putBoolean("skip_off", s.skipWhenScreenOff)
            .putBoolean("power_save", s.powerSaveEnabled)
            .putInt("power_th", s.powerSaveBatteryThreshold)
            .putInt("interval", s.intervalMinutes)
            .putLong("last_change", s.lastChangeAt)
            .commit()
        if (s.lastChangeAt > 0L) writeClockFile(context, s.lastChangeAt)
    }

    fun enabled(context: Context): Boolean =
        sp(context).getBoolean("enabled", false)

    fun superService(context: Context): Boolean =
        sp(context).getBoolean("super_service", false)

    fun intervalMinutes(context: Context): Int =
        sp(context).getInt("interval", 10).coerceIn(5, 180)

    fun skipWhenScreenOff(context: Context): Boolean =
        sp(context).getBoolean("skip_off", false)

    fun powerSave(context: Context): Boolean =
        sp(context).getBoolean("power_save", false)

    fun powerTh(context: Context): Int =
        sp(context).getInt("power_th", 20).coerceIn(5, 50)

    fun lastChangeAt(context: Context): Long =
        sp(context).getLong("last_change", 0L)

    fun setLastChangeAt(context: Context, ts: Long) {
        // SharedPreferences 跨进程不可靠：同时写入 filesDir 时钟文件，主进程直接读文件
        sp(context).edit()
            .putLong("last_change", ts)
            .putBoolean("changing", false)
            .commit()
        writeClockFile(context, ts)
    }

    /** 读取「真实上次更换时间」：max(Prefs, 时钟文件)，不依赖进程内缓存 */
    fun effectiveLastChangeAt(context: Context): Long {
        val fromSp = sp(context).getLong("last_change", 0L)
        val fromFile = readClockFile(context)
        return maxOf(fromSp, fromFile)
    }

    private fun clockFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_last_change.clock")

    private fun writeClockFile(context: Context, ts: Long) {
        try {
            val f = clockFile(context)
            val tmp = java.io.File(f.parentFile, "jhsy_last_change.clock.tmp")
            tmp.writeText(ts.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(f)) {
                f.writeText(ts.toString(), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun readClockFile(context: Context): Long {
        return try {
            val f = clockFile(context)
            if (!f.exists()) 0L else f.readText(Charsets.UTF_8).trim().toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 尝试占用自动更换锁。
     * @param force 手动/通知「立即更换」时为 true，忽略防抖间隔，但仍避开正在进行中的一轮
     * @return true 表示获得执行权，调用方必须在 finally 里 [releaseChange]
     */
    fun tryBeginChange(context: Context, force: Boolean = false): Boolean {
        val prefs = sp(context)
        synchronized(LOCK) {
            val now = System.currentTimeMillis()
            val changing = prefs.getBoolean("changing", false)
            val changingAt = prefs.getLong("changing_at", 0L)
            if (changing && now - changingAt < CHANGING_STALE_MS) {
                return false
            }
            if (!force) {
                // 成功更换后的防抖
                val last = prefs.getLong("last_change", 0L)
                if (last > 0L && now - last < AUTO_CHANGE_DEBOUNCE_MS) {
                    return false
                }
                // 失败/进行中也要防抖，避免 30s 一轮死循环狂换
                val lastAttempt = prefs.getLong("last_attempt", 0L)
                if (lastAttempt > 0L && now - lastAttempt < AUTO_CHANGE_DEBOUNCE_MS) {
                    return false
                }
            }
            prefs.edit()
                .putBoolean("changing", true)
                .putLong("changing_at", now)
                .putLong("last_attempt", now)
                .commit()
            return true
        }
    }

    fun isChanging(context: Context): Boolean {
        val prefs = sp(context)
        val changing = prefs.getBoolean("changing", false)
        val at = prefs.getLong("changing_at", 0L)
        if (!changing) return false
        // 超时视为未占用，避免卡死
        return System.currentTimeMillis() - at < CHANGING_STALE_MS
    }

    fun releaseChange(context: Context) {
        synchronized(LOCK) {
            sp(context).edit()
                .putBoolean("changing", false)
                .commit()
        }
    }

    /** 手动更换等：强制占用锁（仍避开正在进行中的一轮） */
    fun tryBeginChangeForce(context: Context): Boolean = tryBeginChange(context, force = true)


    fun setStatusHint(context: Context, text: String) {
        sp(context).edit().putString("status_hint", text).putLong("status_hint_at", System.currentTimeMillis()).apply()
    }

    fun statusHint(context: Context): String =
        sp(context).getString("status_hint", "") ?: ""

    fun statusHintAt(context: Context): Long =
        sp(context).getLong("status_hint_at", 0L)

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val LOCK = Any()
}
