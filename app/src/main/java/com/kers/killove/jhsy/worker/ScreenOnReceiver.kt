package com.kers.killove.jhsy.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.domain.TriggerType
import com.kers.killove.jhsy.service.WallpaperForegroundService
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 亮屏 / 解锁补换（带防抖）：
 *
 * 1. 亮屏后不立刻补换，延迟 [DEBOUNCE_MS]（5 分钟）
 * 2. 5 分钟内再次息屏 → 取消待执行补换（防随手看一眼）
 * 3. 延迟到期时：
 *    - 屏幕须仍亮着
 *    - 若离下次定时更换不足 5 分钟 → 不补换，交给软件定时
 *    - 若已过期（应换时间已到/已过）→ 执行亮屏补换
 *    - 若尚未到期且离下次仍大于 5 分钟 → 不补换
 *
 * 仍会刷新 WorkManager 与 FGS，与是否补换无关。
 */
class ScreenOnReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                cancelDebouncedMakeup(context)
                return
            }
            ACTION_DEBOUNCED_MAKEUP -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        evaluateAndMaybeMakeup(context.applicationContext)
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        onScreenInteractive(context.applicationContext)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> return
        }
    }

    private suspend fun onScreenInteractive(context: Context) {
        val settings = SettingsRepository(context).settingsFlow.first()
        if (!settings.enabled) {
            cancelDebouncedMakeup(context)
            return
        }

        // 周期任务与 FGS 仍立即挂上（不换壁纸）
        ChangeWallpaperWorker.enqueue(context, settings.intervalMinutes)
        WallpaperForegroundService.start(context)

        // 安排 5 分钟后评估是否补换（已有则重置计时，以最后一次亮屏为准）
        scheduleDebouncedMakeup(context)
    }

    private suspend fun evaluateAndMaybeMakeup(context: Context) {
        val settings = SettingsRepository(context).settingsFlow.first()
        if (!settings.enabled) return

        // 须仍亮屏（5 分钟内息屏已在 SCREEN_OFF 取消；此处再防一次）
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = pm?.isInteractive == true
        if (!interactive) return

        val intervalMs = settings.intervalMinutes.coerceIn(5, 180) * 60_000L
        val last = ProcessBridgePrefs.lastChangeAt(context).takeIf { it > 0L }
            ?: settings.lastChangeAt
        val now = System.currentTimeMillis()
        // 距下次定时更换的剩余时间；已过期则为负数
        val remainingMs = if (last <= 0L) {
            Long.MIN_VALUE / 4 // 从未换过：视为已到期
        } else {
            last + intervalMs - now
        }

        // 离下次更换不足 5 分钟：不补换，交给定时任务
        if (remainingMs in 1..NEAR_SCHEDULE_MS) {
            WallpaperForegroundService.start(context)
            return
        }

        // 已到期/过期（remaining <= 0）才亮屏补换
        // 尚未到期且 remaining > 5 分钟：不提前换
        if (remainingMs <= 0L) {
            WallpaperForegroundService.startChangeNow(context, TriggerType.ScreenOn)
        } else {
            WallpaperForegroundService.start(context)
        }
    }

    companion object {
        const val ACTION_DEBOUNCED_MAKEUP = "com.kers.killove.jhsy.action.SCREEN_ON_MAKEUP"
        /** 亮屏持续多久后才评估补换 */
        const val DEBOUNCE_MS = 5 * 60_000L
        /** 距下次定时不足该值则不补换 */
        const val NEAR_SCHEDULE_MS = 5 * 60_000L

        private const val REQ_CODE = 0x51C0

        private fun makeupPendingIntent(context: Context): PendingIntent {
            val i = Intent(context, ScreenOnReceiver::class.java).setAction(ACTION_DEBOUNCED_MAKEUP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            return PendingIntent.getBroadcast(context, REQ_CODE, i, flags)
        }

        fun scheduleDebouncedMakeup(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = makeupPendingIntent(context)
            val triggerAt = SystemClock.elapsedRealtime() + DEBOUNCE_MS
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            } catch (_: SecurityException) {
                // 无精确闹钟权限时退化为 inexact
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } catch (_: Exception) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }

        fun cancelDebouncedMakeup(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(makeupPendingIntent(context))
        }
    }
}
