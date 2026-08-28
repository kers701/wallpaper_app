package com.kers.killove.jhsy.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 省流量模式：按「当日」累计写入壁纸缓存的字节数调整间隔 / 停换。
 *
 * 阈值：
 * - ≥ 1GB  → 间隔 +5 分钟
 * - ≥ 10GB → 再 +5（合计相对基础间隔 +10）
 * - ≥ 20GB → 今日不再自动更换
 */
object DataSaverBudget {
    private const val PREFS = "jhsy_data_saver"
    private const val GB = 1024L * 1024L * 1024L

    private fun dayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun todayBytes(context: Context): Long {
        val sp = sp(context)
        val day = dayKey()
        if (sp.getString("day", "") != day) return 0L
        return sp.getLong("bytes", 0L)
    }

    fun addBytes(context: Context, bytes: Long) {
        if (bytes <= 0L) return
        val sp = sp(context)
        val day = dayKey()
        val cur = if (sp.getString("day", "") == day) sp.getLong("bytes", 0L) else 0L
        sp.edit().putString("day", day).putLong("bytes", cur + bytes).apply()
    }

    fun todayGb(context: Context): Double = todayBytes(context) / GB.toDouble()

    /** 相对用户配置的额外分钟数 */
    fun extraIntervalMinutes(context: Context, enabled: Boolean): Int {
        if (!enabled) return 0
        val b = todayBytes(context)
        return when {
            b >= 10 * GB -> 10
            b >= 1 * GB -> 5
            else -> 0
        }
    }

    /** 是否因 ≥20GB 停止今日自动更换 */
    fun shouldStopToday(context: Context, enabled: Boolean): Boolean {
        if (!enabled) return false
        return todayBytes(context) >= 20 * GB
    }

    fun effectiveIntervalMinutes(context: Context, base: Int, enabled: Boolean): Int {
        return (base + extraIntervalMinutes(context, enabled)).coerceIn(5, 240)
    }

    fun statusLine(context: Context, enabled: Boolean, baseInterval: Int): String {
        if (!enabled) return "省流量：关"
        val gb = todayGb(context)
        val extra = extraIntervalMinutes(context, true)
        val eff = effectiveIntervalMinutes(context, baseInterval, true)
        return when {
            shouldStopToday(context, true) ->
                String.format(Locale.CHINA, "省流量：今日已用 %.2f GB ≥20，今日不再自动更换", gb)
            extra > 0 ->
                String.format(Locale.CHINA, "省流量：今日 %.2f GB，间隔 %d→%d 分钟（+ %d）", gb, baseInterval, eff, extra)
            else ->
                String.format(Locale.CHINA, "省流量：今日 %.2f GB，间隔保持 %d 分钟", gb, baseInterval)
        }
    }
}
