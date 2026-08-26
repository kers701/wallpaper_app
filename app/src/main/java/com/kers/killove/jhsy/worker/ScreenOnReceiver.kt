package com.kers.killove.jhsy.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.service.WallpaperForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 亮屏 / 解锁：补一次到期更换，并拉起 FGS + WorkManager。
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
                val due = settings.lastChangeAt <= 0L ||
                    System.currentTimeMillis() - settings.lastChangeAt >= intervalMs

                // 双保险始终挂上
                WallpaperForegroundService.start(context)
                ChangeWallpaperWorker.enqueue(context, settings.intervalMinutes)

                if (due) {
                    ChangeWallpaperWorker.enqueueOneShot(context, forceIgnoreScreenOff = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
