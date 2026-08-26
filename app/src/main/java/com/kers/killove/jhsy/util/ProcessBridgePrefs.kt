package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 主进程写、服务进程读的轻量桥接。
 * 独立进程无法安全共用 DataStore，故用 SharedPreferences 同步关键开关。
 */
object ProcessBridgePrefs {
    private const val NAME = "jhsy_bridge"

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
            .apply()
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
        sp(context).edit().putLong("last_change", ts).apply()
    }

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
