package com.kers701.wallpaperc.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "wallpaperc_periodic_change"
        const val ONE_SHOT_NAME = "wallpaperc_oneshot_change"
        const val KEY_FORCE_SCREEN_ON = "force_screen_on"

        fun enqueue(context: Context, intervalMinutes: Int) {
            val minutes = intervalMinutes.coerceAtLeast(15).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ChangeWallpaperWorker>(
                minutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 息屏恢复 / 立即触发：单次任务，不受 Doze 延迟影响更多 */
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
