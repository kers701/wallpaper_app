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
import com.kers.killove.jhsy.data.remote.WallhavenApi
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
import com.kers.killove.jhsy.domain.WallpaperChanger
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import com.kers.killove.jhsy.worker.ChangeWallpaperWorker
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
                ensureForegroundAndLoop()
                scope.launch {
                    // 手动/补换：force 忽略 90s 防抖，但仍避开正在进行中的一轮
                    if (!ProcessBridgePrefs.tryBeginChange(this@WallpaperForegroundService, force = true)) {
                        return@launch
                    }
                    acquireWake()
                    try {
                        val settingsRepo = runCatching { SettingsRepository(applicationContext) }.getOrNull()
                        val dao = runCatching { WallpaperDatabase.get(applicationContext).dao() }.getOrNull()
                        if (settingsRepo != null && dao != null) {
                            val changer = WallpaperChanger(
                                applicationContext, settingsRepo, WallhavenApi(),
                                SystemWallpaperSetter(applicationContext), dao,
                                onProgress = { frac, label ->
                                    val pct = (frac * 100).toInt().coerceIn(0, 100)
                                    refreshNotification(if (label.isNotBlank()) "$label · $pct%" else "更换中 $pct%")
                                }
                            )
                            changer.changeOnce(forceIgnoreScreenOff = true)
                            refreshNotification("后台更换服务运行中（独立进程）")
                            ProcessBridgePrefs.setLastChangeAt(this@WallpaperForegroundService, System.currentTimeMillis())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        ProcessBridgePrefs.releaseChange(this@WallpaperForegroundService)
                        releaseWake()
                    }
                }
            }
            else -> ensureForegroundAndLoop()
        }
        return START_STICKY
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
                // 约每 2 分钟刷新前台通知，降低被「一键清空」后长期无通知的概率
                if (tick % 4 == 0) {
                    ensureForegroundAndLoop()
                }
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
                        refreshNotification("后台更换服务运行中（独立进程）")
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

    private fun refreshNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val n = buildNotification(text)
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

    private fun buildNotification(contentText: String = "后台更换服务运行中（独立进程）"): Notification {
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
            Intent(this, WallpaperForegroundService::class.java).setAction(ACTION_CHANGE_NOW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .addAction(0, "立即更换", changeNow)
            .addAction(0, "停止", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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
