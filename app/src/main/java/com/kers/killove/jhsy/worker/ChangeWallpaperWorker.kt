package com.kers.killove.jhsy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kers.killove.jhsy.data.local.WallpaperDatabase
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.data.remote.WallhavenApi
import com.kers.killove.jhsy.data.wallpaper.SystemWallpaperSetter
import com.kers.killove.jhsy.domain.ChangeResult
import com.kers.killove.jhsy.domain.WallpaperChanger
import com.kers.killove.jhsy.service.WallpaperForegroundService
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

        // 被拉起时尽量把前台服务也拉起来，方便划掉后恢复
        runCatching { WallpaperForegroundService.start(applicationContext) }

        val force = inputData.getBoolean(KEY_FORCE_SCREEN_ON, false)
        val changer = WallpaperChanger(
            context = applicationContext,
            settingsRepo = settingsRepo,
            api = WallhavenApi(),
            setter = SystemWallpaperSetter(applicationContext),
            dao = WallpaperDatabase.get(applicationContext).dao()
        )

        return when (val r = changer.changeOnce(forceIgnoreScreenOff = force)) {
            is ChangeResult.Success -> Result.success()
            is ChangeResult.Failure -> {
                if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "jhsy_periodic_change"
        const val ONE_SHOT_NAME = "jhsy_oneshot_change"
        const val KEY_FORCE_SCREEN_ON = "force_screen_on"

        fun enqueue(context: Context, intervalMinutes: Int) {
            // WorkManager 周期最短 15 分钟；更短间隔依赖 FGS 循环
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

        fun enqueueOneShot(context: Context, forceIgnoreScreenOff: Boolean = true) {
            val data = androidx.work.Data.Builder()
                .putBoolean(KEY_FORCE_SCREEN_ON, forceIgnoreScreenOff)
                .build()
            val request = OneTimeWorkRequestBuilder<ChangeWallpaperWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_SHOT_NAME)
        }
    }
}
