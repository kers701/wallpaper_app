package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

object ProcessBridgePrefs {
    private const val NAME = "jhsy_bridge"
    private const val AUTO_CHANGE_DEBOUNCE_MS = 90_000L
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
        writeBlacklist(context, s.blacklistPackages)
        writeAvoidLocationsJson(context, s.avoidanceLocationsJson.ifBlank { "[]" })
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
        sp(context).edit()
            .putLong("last_change", ts)
            .putBoolean("changing", false)
            .commit()
        writeClockFile(context, ts)
    }

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

    private fun blacklistFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_blacklist.txt")

    private fun blacklistRevFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_blacklist.rev")

    fun writeBlacklist(context: Context, packages: List<String>) {
        try {
            val body = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
            atomicWrite(blacklistFile(context), body)
            atomicWrite(blacklistRevFile(context), System.currentTimeMillis().toString())
        } catch (_: Exception) {
        }
    }

    fun blacklistFileExists(context: Context): Boolean =
        try { blacklistFile(context).exists() } catch (_: Exception) { false }

    fun avoidFileExists(context: Context): Boolean =
        try { avoidFile(context).exists() } catch (_: Exception) { false }

    fun readBlacklist(context: Context): List<String> {
        return try {
            val f = blacklistFile(context)
            if (!f.exists()) emptyList()
            else f.readText(Charsets.UTF_8).split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun mergeBlacklist(context: Context, fromStore: List<String> = emptyList()): List<String> =
        readBlacklist(context)

    fun effectiveBlacklist(context: Context): List<String> = readBlacklist(context)

    private fun avoidFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_avoid_locations.json")

    private fun avoidRevFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_avoid_locations.rev")

    fun writeAvoidLocationsJson(context: Context, json: String) {
        try {
            val body = json.ifBlank { "[]" }
            atomicWrite(avoidFile(context), body)
            atomicWrite(avoidRevFile(context), System.currentTimeMillis().toString())
        } catch (_: Exception) {
        }
    }

    fun readAvoidLocationsJson(context: Context): String? {
        return try {
            val f = avoidFile(context)
            if (!f.exists()) null
            else {
                val t = f.readText(Charsets.UTF_8).trim()
                if (t.isEmpty()) "[]" else t
            }
        } catch (_: Exception) {
            null
        }
    }

    fun effectiveAvoidLocationsJson(context: Context, fromStore: String = "[]"): String {
        val fromFile = readAvoidLocationsJson(context)
        return when {
            fromFile != null -> fromFile
            fromStore.isNotBlank() -> fromStore
            else -> "[]"
        }
    }

    private fun atomicWrite(target: java.io.File, body: String) {
        val parent = target.parentFile ?: return
        parent.mkdirs()
        val tmp = java.io.File(parent, target.name + ".tmp")
        tmp.writeText(body, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(body, Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun tryBeginChange(context: Context, force: Boolean = false): Boolean {
        val prefs = sp(context)
        synchronized(LOCK) {
            val now = System.currentTimeMillis()
            val changing = prefs.getBoolean("changing", false)
            val changingAt = prefs.getLong("changing_at", 0L)
            if (changing && now - changingAt < CHANGING_STALE_MS) return false
            if (!force) {
                val last = prefs.getLong("last_change", 0L)
                if (last > 0L && now - last < AUTO_CHANGE_DEBOUNCE_MS) return false
                val lastAttempt = prefs.getLong("last_attempt", 0L)
                if (lastAttempt > 0L && now - lastAttempt < AUTO_CHANGE_DEBOUNCE_MS) return false
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
        return System.currentTimeMillis() - at < CHANGING_STALE_MS
    }

    fun releaseChange(context: Context) {
        synchronized(LOCK) {
            sp(context).edit().putBoolean("changing", false).commit()
        }
    }

    fun tryBeginChangeForce(context: Context): Boolean = tryBeginChange(context, force = true)

    fun setStatusHint(context: Context, text: String) {
        sp(context).edit().putString("status_hint", text).putLong("status_hint_at", System.currentTimeMillis()).apply()
    }

    fun statusHint(context: Context): String =
        sp(context).getString("status_hint", "") ?: ""

    fun statusHintAt(context: Context): Long =
        sp(context).getLong("status_hint_at", 0L)

    const val MODE_NORMAL = "normal"
    const val MODE_HEALTH = "health"
    const val MODE_HEARTBEAT = "heartbeat"

    private fun purityModeFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_purity_mode.txt")

    fun purityMode(context: Context): String {
        val fromFile = try {
            val f = purityModeFile(context)
            if (f.exists()) f.readText(Charsets.UTF_8).trim() else ""
        } catch (_: Exception) { "" }
        val fromSp = sp(context).getString("purity_mode", MODE_NORMAL) ?: MODE_NORMAL
        val m = when {
            fromFile in listOf(MODE_HEALTH, MODE_HEARTBEAT, MODE_NORMAL) -> fromFile
            fromSp in listOf(MODE_HEALTH, MODE_HEARTBEAT) -> fromSp
            else -> MODE_NORMAL
        }
        return when (m) {
            MODE_HEALTH, MODE_HEARTBEAT -> m
            else -> MODE_NORMAL
        }
    }

    fun setPurityMode(context: Context, mode: String) {
        val m = when (mode) {
            MODE_HEALTH, MODE_HEARTBEAT -> mode
            else -> MODE_NORMAL
        }
        sp(context).edit().putString("purity_mode", m).commit()
        try {
            val f = purityModeFile(context)
            val tmp = java.io.File(f.parentFile, "jhsy_purity_mode.txt.tmp")
            tmp.writeText(m, Charsets.UTF_8)
            if (!tmp.renameTo(f)) {
                f.writeText(m, Charsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun cyclePurityMode(context: Context): String {
        val next = when (purityMode(context)) {
            MODE_NORMAL -> MODE_HEALTH
            MODE_HEALTH -> MODE_HEARTBEAT
            else -> MODE_NORMAL
        }
        setPurityMode(context, next)
        return next
    }

    fun purityModeTitle(mode: String): String = when (mode) {
        MODE_HEALTH -> "MiroFlweat·健康模式"
        MODE_HEARTBEAT -> "MiroFlweat·心跳模式"
        else -> "MiroFlweat·普通模式"
    }

    fun purityModeNextButtonLabel(mode: String): String = when (mode) {
        MODE_NORMAL -> "健康模式"
        MODE_HEALTH -> "心跳模式"
        else -> "普通模式"
    }

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val LOCK = Any()
}
