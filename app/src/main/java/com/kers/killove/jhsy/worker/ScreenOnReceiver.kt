package com.kers.killove.jhsy.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.service.WallpaperForegroundService
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 亮屏 / 解锁：补一次到期更换，并拉起 FGS + WorkManager。
 * 到期时只通知 :svc 执行，不在主进程 changeOnce。
 */
class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SCREEN_ON && action != Intent.ACTION_USER_PRESENT) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context).settingsFlow.first()
                if (!settings.enabled) return@launch

                val intervalMs = settings.intervalMinutes.coerceIn(5, 180) * 60_000L
                // 跨进程：优先 bridge 上的 last_change，避免 DataStore 与 :svc 不同步
                val last = ProcessBridgePrefs.lastChangeAt(context).takeIf { it > 0L }
                    ?: settings.lastChangeAt
                val due = last <= 0L ||
                    System.currentTimeMillis() - last >= intervalMs

                // 双保险：周期任务挂上，并刷新 FGS 通知
                ChangeWallpaperWorker.enqueue(context, settings.intervalMinutes)

                if (due) {
                    WallpaperForegroundService.startChangeNow(context)
                } else {
                    WallpaperForegroundService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
