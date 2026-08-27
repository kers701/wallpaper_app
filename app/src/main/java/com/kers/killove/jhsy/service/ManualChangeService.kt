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
import com.kers.killove.jhsy.domain.ChangeResult
import com.kers.killove.jhsy.domain.TriggerType
import com.kers.killove.jhsy.domain.WallpaperChanger
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 第三进程 `:manual`：仅负责「立即更换」。
 * - 当场下载（不使用预下载缓存）
 * - 不启动定时轮询、不调度 Worker、不预取下一张
 * - 换完 stopSelf，进程退出
 */
class ManualChangeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jhsy:manual")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) {
            return START_NOT_STICKY
        }
        running = true
        startFg("独立进程正在下载并更换…")
        scope.launch {
            if (!ProcessBridgePrefs.tryBeginChange(this@ManualChangeService, force = true)) {
                ProcessBridgePrefs.setStatusHint(this@ManualChangeService, "已有更换在进行中")
                die()
                return@launch
            }
            acquireWake()
            var detail = ""
            try {
                val settingsRepo = SettingsRepository(applicationContext)
                val dao = WallpaperDatabase.get(applicationContext).dao()
                val changer = WallpaperChanger(
                    applicationContext, settingsRepo, WallhavenApi(),
                    SystemWallpaperSetter(applicationContext), dao,
                    onProgress = { frac, label ->
                        val pct = (frac * 100).toInt().coerceIn(0, 100)
                        val text = if (label.isNotBlank()) "$label · $pct%" else "下载中 $pct%"
                        notify(text)
                        ProcessBridgePrefs.setStatusHint(this@ManualChangeService, text)
                    }
                )
                // 当场下载；不用预缓存；不预取下一张；不碰定时规则
                val r = changer.changeOnce(
                    forceIgnoreScreenOff = true,
                    triggerType = TriggerType.Manual,
                    liveDownloadOnly = true
                )
                detail = when (r) {
                    is ChangeResult.Success -> {
                        ProcessBridgePrefs.setLastChangeAt(
                            this@ManualChangeService,
                            System.currentTimeMillis()
                        )
                        "已设置 [${r.item.source}] ${r.item.id}" +
                            if (r.detail.isNotBlank()) " · ${r.detail}" else ""
                    }
                    is ChangeResult.Failure -> "失败：${r.message}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                detail = "失败：${e.message}"
            } finally {
                ProcessBridgePrefs.releaseChange(this@ManualChangeService)
                releaseWake()
                ProcessBridgePrefs.setStatusHint(this@ManualChangeService, detail)
                die()
            }
        }
        return START_NOT_STICKY
    }

    private fun die() {
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
        stopSelf()
    }

    private fun startFg(text: String) {
        createChannel()
        val n = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIFICATION_ID, n)
            } catch (_: Exception) {
            }
        }
    }

    private fun notify(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID, "手动更换", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "立即更换时短暂前台"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireWake() {
        try {
            if (wakeLock?.isHeld != true) wakeLock?.acquire(10 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWake() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jhsy_manual_change"
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            val i = Intent(context, ManualChangeService::class.java)
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
    }
}
