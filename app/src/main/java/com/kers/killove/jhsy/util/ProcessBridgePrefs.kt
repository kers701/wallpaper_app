package com.kers.killove.jhsy.util

import android.content.Context
import com.kers.killove.jhsy.domain.AppSettings

/**
 * 主进程写、服务进程读的轻量桥接。
 * 独立进程无法安全共用 DataStore，故用 SharedPreferences + 文件同步关键开关。
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
        // 跨进程：黑名单与避让点以文件为准，主进程保存时整表覆盖
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

    // ── 黑名单 / 避让：跨进程唯一真相源 = filesDir 标记文件 ──────────
    // :svc 与主进程 DataStore 内存缓存不一致；增删一律整表覆盖写文件，识别一律读文件。

    private fun blacklistFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_blacklist.txt")

    private fun blacklistRevFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_blacklist.rev")

    /** 整表覆盖写入黑名单（每行一个包名；空列表写空文件）。同时更新 .rev 标记。 */
    fun writeBlacklist(context: Context, packages: List<String>) {
        try {
            val body = packages
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n")
            atomicWrite(blacklistFile(context), body)
            atomicWrite(blacklistRevFile(context), System.currentTimeMillis().toString())
        } catch (_: Exception) {
        }
    }

    /** 黑名单标记文件是否已建立（空列表也会建文件）。 */
    fun blacklistFileExists(context: Context): Boolean =
        try { blacklistFile(context).exists() } catch (_: Exception) { false }

    /** 避让标记文件是否已建立（空列表写 `[]` 也会建文件）。 */
    fun avoidFileExists(context: Context): Boolean =
        try { avoidFile(context).exists() } catch (_: Exception) { false }

    /** 仅从标记文件读取黑名单（文件不存在 = 空）。 */
    fun readBlacklist(context: Context): List<String> {
        return try {
            val f = blacklistFile(context)
            if (!f.exists()) emptyList()
            else f.readText(Charsets.UTF_8)
                .split('\n', ',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 生效黑名单：只认文件，忽略 DataStore 进程内缓存。
     * @param fromStore 保留参数兼容旧调用，已不再参与合并。
     */
    @Suppress("UNUSED_PARAMETER")
    fun mergeBlacklist(context: Context, fromStore: List<String> = emptyList()): List<String> =
        readBlacklist(context)

    /** 生效黑名单（推荐新调用点使用此名）。 */
    fun effectiveBlacklist(context: Context): List<String> = readBlacklist(context)

    // ── 避让点 JSON 跨进程文件桥 ────────────────────────────────────

    private fun avoidFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_avoid_locations.json")

    private fun avoidRevFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_avoid_locations.rev")

    /** 整表覆盖写避让 JSON（空列表写 `[]`）。同时更新 .rev 标记。 */
    fun writeAvoidLocationsJson(context: Context, json: String) {
        try {
            val body = json.ifBlank { "[]" }
            atomicWrite(avoidFile(context), body)
            atomicWrite(avoidRevFile(context), System.currentTimeMillis().toString())
        } catch (_: Exception) {
        }
    }

    /**
     * 读避让 JSON。文件存在则返回内容（含 `[]`）；不存在返回 null。
     * 空数组也会返回 `"[]"`，不再把「空」当成缺失而回退到 DataStore。
     */
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

    /** 生效避让 JSON：文件优先；文件不存在时才用 DataStore 兜底（首次迁移）。 */
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


    // —— 通知纯度运行模式：normal / health / heartbeat ——
    // 必须 filesDir 跨进程同步：:svc 点通知改模式，:manual 当场下载必须读到同一值。
    // SharedPreferences 在多进程下各自缓存，不可靠（与 lastChange 时钟文件同理）。
    const val MODE_NORMAL = "normal"
    const val MODE_HEALTH = "health"
    const val MODE_HEARTBEAT = "heartbeat"

    private fun purityModeFile(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, "jhsy_purity_mode.txt")

    fun purityMode(context: Context): String {
        val fromFile = try {
            val f = purityModeFile(context)
            if (f.exists()) f.readText(Charsets.UTF_8).trim() else ""
        } catch (_: Exception) {
            ""
        }
        val fromSp = sp(context).getString("purity_mode", MODE_NORMAL) ?: MODE_NORMAL
        // 文件优先；无文件时回退 SP（兼容旧安装）
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

    /** 普通 → 健康 → 心跳 → 普通 */
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
        MODE_HEALTH -> "镜花水月·健康模式"
        MODE_HEARTBEAT -> "镜花水月·心跳模式"
        else -> "镜花水月·普通模式"
    }

    /** 按钮显示「下一模式」名称 */
    fun purityModeNextButtonLabel(mode: String): String = when (mode) {
        MODE_NORMAL -> "健康模式"
        MODE_HEALTH -> "心跳模式"
        else -> "普通模式"
    }

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val LOCK = Any()
}
