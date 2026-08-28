package com.kers.killove.jhsy.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 开发者模式运行日志：写入 filesDir/run.log
 */
object RunLog {
    private const val PREFS = "jhsy_meta"
    private const val KEY_DEV = "developer_mode"
    private const val KEY_LOG = "run_log_enabled"
    private const val MAX_BYTES = 8L * 1024 * 1024 // 约 8MB 后截断保留尾部

    fun isDeveloperMode(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEV, false)

    fun setDeveloperMode(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEV, on).apply()
        if (!on) setLogEnabled(context, false)
        i(context, "developer_mode=${if (on) "ON" else "OFF"}")
    }

    fun isLogEnabled(context: Context): Boolean =
        isDeveloperMode(context) &&
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_LOG, false)

    fun setLogEnabled(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOG, on).apply()
        // 直接写一行，不经 isLogEnabled（刚打开时也要记）
        appendRaw(context, "run_log=${if (on) "ON" else "OFF"}")
    }

    fun logFile(context: Context): File =
        File(context.applicationContext.filesDir, "run.log")

    fun i(context: Context, message: String) {
        if (!isLogEnabled(context)) return
        appendRaw(context, message)
    }

    private fun appendRaw(context: Context, message: String) {
        try {
            val f = logFile(context)
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "$ts  $message\n"
            f.appendText(line, Charsets.UTF_8)
            if (f.length() > MAX_BYTES) {
                val text = f.readText(Charsets.UTF_8)
                val keep = text.takeLast((MAX_BYTES / 2).toInt())
                f.writeText("…(truncated)…\n" + keep, Charsets.UTF_8)
            }
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context) {
        try {
            logFile(context).writeText("", Charsets.UTF_8)
            appendRaw(context, "log cleared")
        } catch (_: Exception) {
        }
    }
}
