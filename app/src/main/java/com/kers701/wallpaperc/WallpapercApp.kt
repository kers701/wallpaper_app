package com.kers701.wallpaperc

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.work.Configuration
import com.kers701.wallpaperc.data.prefs.SettingsRepository
import com.kers701.wallpaperc.service.WallpaperForegroundService
import com.kers701.wallpaperc.worker.ChangeWallpaperWorker
import com.kers701.wallpaperc.worker.ScreenOnReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WallpapercApp : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenReceiver: ScreenOnReceiver? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        registerScreenReceiver()
        // 进程被杀后重新创建时，恢复定时任务 / 前台服务
        appScope.launch {
            runCatching {
                val settings = SettingsRepository(this@WallpapercApp).settingsFlow.first()
                if (!settings.enabled) return@runCatching
                if (settings.useForegroundService || settings.intervalMinutes < 15) {
                    WallpaperForegroundService.start(this@WallpapercApp)
                } else {
                    ChangeWallpaperWorker.enqueue(this@WallpapercApp, settings.intervalMinutes)
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = ScreenOnReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            screenReceiver = receiver
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
