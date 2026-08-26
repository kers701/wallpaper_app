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

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context).settingsFlow.first()
                if (!settings.enabled) return@launch
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
