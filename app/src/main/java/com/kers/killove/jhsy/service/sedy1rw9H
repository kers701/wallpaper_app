package com.kers.killove.jhsy.service

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WallpaperForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "wallpaperc:fgs"
        ).apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // 始终确保前台通知与循环在跑（进程被杀后系统重启服务）
                ensureForegroundAndLoop()
            }
        }
        return START_STICKY
    }

    private fun ensureForegroundAndLoop() {
        createChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 部分机型限制 FGS：仍尝试循环
        }

        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            val settingsRepo = SettingsRepository(applicationContext)
            val changer = WallpaperChanger(
                context = applicationContext,
                settingsRepo = settingsRepo,
                api = WallhavenApi(),
                setter = SystemWallpaperSetter(applicationContext),
                dao = WallpaperDatabase.get(applicationContext).dao()
            )
            while (isActive) {
                val settings = settingsRepo.settingsFlow.first()
                if (!settings.enabled) {
                    stopSelf()
                    break
                }
                try {
                    wakeLock?.acquire(3 * 60_000L)
                    runCatching { changer.changeOnce() }
                } finally {
                    if (wakeLock?.isHeld == true) {
                        runCatching { wakeLock?.release() }
                    }
                }
                val minutes = settings.intervalMinutes.coerceIn(5, 180)
                // 分段 delay，便于服务重启后更快响应
                val totalMs = minutes * 60_000L
                var waited = 0L
                val step = 30_000L
                while (isActive && waited < totalMs) {
                    delay(minOf(step, totalMs - waited))
                    waited += step
                    // 若用户关闭了自动更换，提前退出
                    val s = settingsRepo.settingsFlow.first()
                    if (!s.enabled) {
                        stopSelf()
                        return@launch
                    }
                }
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        if (wakeLock?.isHeld == true) {
            runCatching { wakeLock?.release() }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "wallpaperc_service"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.kers.killove.jhsy.STOP"

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
