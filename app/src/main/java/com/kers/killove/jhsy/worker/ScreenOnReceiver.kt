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
 * 息屏休眠后亮屏时触发：若已超过间隔则立即换壁纸，并确保调度仍在。
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

                if (due) {
                    // 亮屏后补一次更换（忽略息屏跳过）
                    ChangeWallpaperWorker.enqueueOneShot(context, forceIgnoreScreenOff = true)
                }

                // 确保后台调度仍在（进程被杀后恢复）
                if (settings.useForegroundService || settings.intervalMinutes < 15) {
                    WallpaperForegroundService.start(context)
                } else {
                    ChangeWallpaperWorker.enqueue(context, settings.intervalMinutes)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
