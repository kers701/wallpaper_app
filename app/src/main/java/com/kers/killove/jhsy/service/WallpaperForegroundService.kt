package com.kers.killove.jhsy.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kers.killove.jhsy.MainActivity
import com.kers.killove.jhsy.R
import com.kers.killove.jhsy.data.local.WallpaperDatabase
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.data.remote.ProxyHttp
import com.kers.killove.jhsy.data.remote.WallhavenApi
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
import com.kers.killove.jhsy.domain.WallpaperChanger
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import com.kers.killove.jhsy.util.LocationHelper
import com.kers.killove.jhsy.util.ForegroundAppHelper
import com.kers.killove.jhsy.domain.AvoidanceLocation
import com.kers.killove.jhsy.worker.ChangeWallpaperWorker
import com.kers.killove.jhsy.service.ManualChangeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 壁纸更换服务，可运行在独立进程 `:svc`，与 UI 进程分离。
 */
class WallpaperForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastStatusText: String = "运行中 · 自动更换已开启"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jhsy:fgs")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHANGE_NOW -> {
                ManualChangeService.start(this)
                if (ProcessBridgePrefs.enabled(this) || ProcessBridgePrefs.superService(this)) {
                    ensureForegroundAndLoop()
                }
            }
            ACTION_ADD_AVOID_HERE -> {
                scope.launch {
                    handleAddAvoidHere()
                    ensureForegroundAndLoop()
                }
            }
            ACTION_ADD_FG_BLACKLIST -> {
                scope.launch {
                    handleAddFgBlacklist()
                    ensureForegroundAndLoop()
                }
            }
            else -> ensureForegroundAndLoop()
        }
        // 手动一次性：不粘性，换完 stopSelf 后系统不必强拉
        return if (intent?.action == ACTION_CHANGE_NOW &&
            !ProcessBridgePrefs.enabled(this) &&
            !ProcessBridgePrefs.superService(this)
        ) START_NOT_STICKY else START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 主进程被划掉时，独立 :svc 进程可能仍在；若同进程则自启
        if (!ProcessBridgePrefs.enabled(this) && !ProcessBridgePrefs.superService(this)) return
        scope.launch {
            ChangeWallpaperWorker.enqueue(applicationContext, ProcessBridgePrefs.intervalMinutes(this@WallpaperForegroundService))
            delay(500)
            start(applicationContext)
        }
        try {
            val restart = Intent(applicationContext, WallpaperForegroundService::class.java)
            val pi = PendingIntent.getService(
                applicationContext, 99, restart,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1200L,
                pi
            )
        } catch (_: Exception) {
        }
    }


    /** 仅挂前台通知，不启动自动轮询循环（手动一次性用） */
    private fun ensureForegroundOnly() {
        createChannel()
        val notification = buildNotification("独立进程正在更换…")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        try {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun ensureForegroundAndLoop() {
        createChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { runLoop() }
        }
    }

    private suspend fun runLoop() {
        // 优先用 DataStore；失败则用桥接（独立进程）
        val settingsRepo = runCatching { SettingsRepository(applicationContext) }.getOrNull()
        val dao = runCatching { WallpaperDatabase.get(applicationContext).dao() }.getOrNull()
        if (settingsRepo != null) {
            runCatching {
                ProxyHttp.applySettings(settingsRepo.settingsFlow.first())
            }
        }
        val changer = if (dao != null && settingsRepo != null) {
            WallpaperChanger(
                applicationContext, settingsRepo, WallhavenApi(),
                SystemWallpaperSetter(applicationContext), dao,
                onProgress = { frac, label ->
                    val pct = (frac * 100).toInt().coerceIn(0, 100)
                    refreshNotification(if (label.isNotBlank()) "$label · $pct%" else "更换中 $pct%")
                }
            )
        } else null

        var tick = 0
        while (scope.isActive) {
            try {
                tick++
                // 约每 30s～2 分钟刷新动态通知状态（休眠原因 / 运行中）
                lastStatusText = resolveNotificationText(null)
                refreshNotification(lastStatusText)
                // 预下载失败 5 分钟重试（与更换周期解耦）
                if (tick % 2 == 0 && changer != null) {
                    runCatching { changer.tickPrefetchMaintenance() }
                }
                val enabled = settingsRepo?.let {
                    runCatching { it.settingsFlow.first().enabled }.getOrNull()
                } ?: ProcessBridgePrefs.enabled(this)

                val superOn = ProcessBridgePrefs.superService(this)
                if (!enabled && !superOn) {
                    stopSelf()
                    break
                }

                val intervalMin = settingsRepo?.let {
                    runCatching { it.settingsFlow.first().intervalMinutes }.getOrNull()
                } ?: ProcessBridgePrefs.intervalMinutes(this)

                // 优先 bridge（跨进程一致），DataStore 仅作回退
                val lastBridge = ProcessBridgePrefs.lastChangeAt(this)
                val lastDs = settingsRepo?.let {
                    runCatching { it.settingsFlow.first().lastChangeAt }.getOrNull()
                } ?: 0L
                val last = maxOf(lastBridge, lastDs)

                val intervalMs = intervalMin.coerceIn(5, 180) * 60_000L
                val due = last <= 0L || System.currentTimeMillis() - last >= intervalMs
                if (due && changer != null) {
                    // 自动轮询：90s 防抖 + changing 锁，避免与 Worker/亮屏 双开
                    if (!ProcessBridgePrefs.tryBeginChange(this, force = false)) {
                        delay(30_000L)
                        continue
                    }
                    // 换之前再刷一次前台通知（用户手滑清通知后尽量挂回）
                    ensureForegroundAndLoop()
                    acquireWake()
                    try {
                        // changeOnce 内已含黑名单判断
                        changer.changeOnce(forceIgnoreScreenOff = false)
                        ProcessBridgePrefs.setLastChangeAt(this, System.currentTimeMillis())
                        lastStatusText = resolveNotificationText(null)
                        refreshNotification(lastStatusText)
                    } finally {
                        ProcessBridgePrefs.releaseChange(this)
                        releaseWake()
                    }
                }
                delay(30_000L)
            } catch (e: Exception) {
                e.printStackTrace()
                delay(30_000L)
            }
        }
    }

    private fun refreshNotification(overrideText: String? = null) {
        try {
            if (!overrideText.isNullOrBlank()) {
                lastStatusText = overrideText
            } else {
                // 异步刷新状态文案后再次 notify
                scope.launch {
                    lastStatusText = resolveNotificationText(null)
                    val nm = getSystemService(NotificationManager::class.java) ?: return@launch
                    val n = buildNotification(null)
                    nm.notify(NOTIFICATION_ID, n)
                }
            }
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val n = buildNotification(overrideText)
            nm.notify(NOTIFICATION_ID, n)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, n)
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun handleAddAvoidHere() {
        try {
            if (!LocationHelper.hasLocationPermission(this)) {
                refreshNotification("定位休眠操作失败：无定位权限")
                return
            }
            val cur = LocationHelper.currentLocation(this)
            if (cur == null) {
                refreshNotification("添加避让失败：暂无定位")
                return
            }
            val repo = SettingsRepository(applicationContext)
            val s = repo.settingsFlow.first()
            val name = LocationHelper.reverseGeocode(s.amapApiKey, cur.latitude, cur.longitude)
                ?: String.format(java.util.Locale.US, "当前位置 %.5f,%.5f", cur.latitude, cur.longitude)
            val id = "cur_${System.currentTimeMillis()}"
            val list = s.avoidanceLocations().toMutableList()
            if (list.any { LocationHelper.distanceMeters(it.lat, it.lng, cur.latitude, cur.longitude) < 15.0 }) {
                refreshNotification("附近已有避让点，未重复添加")
                return
            }
            list.add(AvoidanceLocation(id, name, cur.latitude, cur.longitude))
            val json = LocationHelper.locationsToJson(list)
            repo.save(s.copy(avoidanceLocationsJson = json, locationAvoidEnabled = true))
            refreshNotification("已添加避让点：$name")
        } catch (e: Exception) {
            refreshNotification("添加避让失败：${e.message}")
        }
    }

    private suspend fun handleAddFgBlacklist() {
        try {
            if (!ForegroundAppHelper.hasUsageAccess(this)) {
                // 尽量保持前台通知，避免点按钮后通知被收起且无反馈
                lastStatusText = "添加黑名单失败：请先授予「使用情况访问」权限"
                refreshNotification(lastStatusText)
                ensureForegroundAndLoop()
                return
            }
            // 点通知时 UsageStats 常把本应用/系统界面排在最前，取排除后的最近真实应用
            val candidates = ForegroundAppHelper.recentForegroundCandidates(this, limit = 8)
            val pkg = candidates.firstOrNull()
                ?: ForegroundAppHelper.currentForegroundPackage(this, excludeSelf = true)
            if (pkg.isNullOrBlank()) {
                lastStatusText = "添加黑名单失败：未识别到其他前台应用（请先打开目标 App 再点通知按钮）"
                refreshNotification(lastStatusText)
                ensureForegroundAndLoop()
                return
            }
            val repo = SettingsRepository(applicationContext)
            val s = repo.settingsFlow.first()
            val label = ForegroundAppHelper.appLabel(this, pkg)
            if (pkg in s.blacklistPackages) {
                lastStatusText = "「$label」已在黑名单中"
                refreshNotification(lastStatusText)
                ensureForegroundAndLoop()
                return
            }
            val next = s.copy(blacklistPackages = s.blacklistPackages + pkg)
            repo.save(next)
            lastStatusText = "已将「$label」加入黑名单"
            refreshNotification(lastStatusText)
            ensureForegroundAndLoop()
        } catch (e: Exception) {
            lastStatusText = "添加黑名单失败：${e.message}"
            refreshNotification(lastStatusText)
            ensureForegroundAndLoop()
        }
    }

    /** 根据定位/黑名单/省电动态生成通知正文；override 用于更换进度等临时文案 */
    private suspend fun resolveNotificationText(overrideText: String?): String {
        if (!overrideText.isNullOrBlank()) return overrideText
        return try {
            val repo = runCatching { SettingsRepository(applicationContext) }.getOrNull()
            val s = repo?.let { runCatching { it.settingsFlow.first() }.getOrNull() }
            if (s != null) {
                // 应用休眠优先
                if (s.blacklistPackages.isNotEmpty() &&
                    ForegroundAppHelper.isBlacklistedForeground(this, s.blacklistPackages)
                ) {
                    val pkg = ForegroundAppHelper.currentForegroundPackage(this) ?: "?"
                    val label = ForegroundAppHelper.appLabel(this, pkg)
                    return "应用休眠 · 正在使用（$label）"
                }
                // 定位休眠：启用避让且在区内
                if (s.locationAvoidEnabled && s.avoidanceLocations().isNotEmpty()) {
                    val (inZone, hit) = LocationHelper.isInAvoidZone(
                        this, s.avoidanceLocations(), s.locationAvoidRadiusMeters.toDouble()
                    )
                    if (inZone) {
                        val tag = hit?.name?.ifBlank { null } ?: "避让点"
                        return "定位休眠 · 已进入（$tag）范围"
                    }
                }
                // 省电休眠
                if (s.powerSaveEnabled) {
                    val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val charging = try {
                        val ifilter = Intent(Intent.ACTION_BATTERY_CHANGED)
                        val st = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        val status = st?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                        status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == android.os.BatteryManager.BATTERY_STATUS_FULL
                    } catch (_: Exception) { false }
                    if (!charging && pct in 0..100 && pct < s.powerSaveBatteryThreshold) {
                        return "省电休眠 · 电量 $pct%（阈值 ${s.powerSaveBatteryThreshold}%）"
                    }
                }
            }
            "运行中 · 自动更换已开启"
        } catch (_: Exception) {
            "后台更换服务运行中"
        }
    }

        private fun acquireWake() {
        try {
            if (wakeLock?.isHeld != true) wakeLock?.acquire(3 * 60_000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWake() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        // v3：IMPORTANCE_LOW，便于三星等机型将通知设为「最小化」并弱化/隐藏状态栏图标，降低烧屏风险
        // 渠道重要级别创建后不可改，故换新 id
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
        // 清理旧渠道
        for (old in listOf("jhsy_service", "jhsy_service_v2")) {
            try { nm.deleteNotificationChannel(old) } catch (_: Exception) {}
        }
    }

    private fun buildNotification(overrideText: String? = null): Notification {
        // 同步路径：尽量读即时状态；失败则用 override / 默认
        val contentText = overrideText?.takeIf { it.isNotBlank() } ?: lastStatusText
        val sleeping = contentText.contains("休眠")
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, WallpaperForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val changeNow = PendingIntent.getService(
            this, 2,
            Intent(this, ManualChangeService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val addAvoid = PendingIntent.getService(
            this, 3,
            Intent(this, WallpaperForegroundService::class.java).setAction(ACTION_ADD_AVOID_HERE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val addBlack = PendingIntent.getService(
            this, 4,
            Intent(this, WallpaperForegroundService::class.java).setAction(ACTION_ADD_FG_BLACKLIST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when {
            contentText.startsWith("定位休眠") -> "镜花水月 · 定位休眠"
            contentText.startsWith("应用休眠") -> "镜花水月 · 应用休眠"
            contentText.startsWith("省电休眠") -> "镜花水月 · 省电休眠"
            contentText.contains("更换") || contentText.contains("%") -> "镜花水月 · 更换中"
            else -> getString(R.string.notification_title)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (sleeping) {
            // 休眠中仍可停止 / 立即更换
            builder.addAction(0, "立即更换", changeNow)
            builder.addAction(0, "停止", stop)
        } else {
            // 正常运行：定位避让 + 应用黑名单
            builder.addAction(0, "定位避让", addAvoid)
            builder.addAction(0, "应用黑名单", addBlack)
            builder.addAction(0, "停止", stop)
        }
        return builder.build()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        releaseWake()
        if (ProcessBridgePrefs.enabled(this) || ProcessBridgePrefs.superService(this)) {
            try {
                ChangeWallpaperWorker.enqueue(applicationContext, ProcessBridgePrefs.intervalMinutes(this))
            } catch (_: Exception) {
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jhsy_service_v3"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.kers.killove.jhsy.STOP"
        const val ACTION_CHANGE_NOW = "com.kers.killove.jhsy.CHANGE_NOW"
        const val ACTION_ADD_AVOID_HERE = "com.kers.killove.jhsy.ADD_AVOID_HERE"
        const val ACTION_ADD_FG_BLACKLIST = "com.kers.killove.jhsy.ADD_FG_BLACKLIST"

        fun start(context: Context) {
            val i = Intent(context, WallpaperForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /** 拉起 FGS 并立即换一次（刷新通知 + :svc 执行） */
        fun startChangeNow(context: Context) {
            val i = Intent(context, WallpaperForegroundService::class.java)
                .setAction(ACTION_CHANGE_NOW)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, WallpaperForegroundService::class.java).setAction(ACTION_STOP)
                )
            } catch (_: Exception) {
                context.stopService(Intent(context, WallpaperForegroundService::class.java))
            }
        }
    }
}
