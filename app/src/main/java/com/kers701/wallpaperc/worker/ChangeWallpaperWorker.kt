package com.kers701.wallpaperc.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kers701.wallpaperc.data.local.WallpaperDatabase
import com.kers701.wallpaperc.data.prefs.SettingsRepository
import com.kers701.wallpaperc.data.remote.WallhavenApi
import com.kers701.wallpaperc.data.wallpaper.SystemWallpaperSetter
import com.kers701.wallpaperc.domain.ChangeResult
import com.kers701.wallpaperc.domain.WallpaperChanger
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ChangeWallpaperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settingsFlow.first()
        if (!settings.enabled) return Result.success()

        val changer = WallpaperChanger(
            context = applicationContext,
            settingsRepo = settingsRepo,
            api = WallhavenApi(),
            setter = SystemWallpaperSetter(applicationContext),
            dao = WallpaperDatabase.get(applicationContext).dao()
        )

        return when (val r = changer.changeOnce()) {
            is ChangeResult.Success -> Result.success()
            is ChangeResult.Failure -> {
                // 临时失败可重试
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "wallpaperc_periodic_change"

        fun enqueue(context: Context, intervalMinutes: Int) {
            // WorkManager 周期任务最短约 15 分钟
            val minutes = intervalMinutes.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<ChangeWallpaperWorker>(
                minutes, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
