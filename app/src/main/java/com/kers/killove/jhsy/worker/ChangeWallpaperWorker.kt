package com.kers.killove.jhsy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.service.WallpaperForegroundService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 周期 / 单次调度入口。
 *
 * 不再在此进程直接 changeOnce，避免与 :svc 前台服务各跑一轮
 * （隔离模式下会变成 4 条更换记录）。
 *
 * 职责：
 * - 拉起 FGS（重新 startForeground，顺带把被划掉的通知挂回来）
 * - 若需要立刻换：发 ACTION_CHANGE_NOW，由 :svc 执行
 */
class ChangeWallpaperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settingsFlow.first()
        if (!settings.enabled) return Result.success()

        val forceNow = inputData.getBoolean(KEY_FORCE_SCREEN_ON, false)

        return try {
            if (forceNow) {
                // 亮屏补换 / 单次任务：刷新通知并让 :svc 执行更换
                WallpaperForegroundService.startChangeNow(applicationContext)
            } else {
                // 周期兜底：只保证 FGS 在跑，到期由 FGS runLoop 判断
                WallpaperForegroundService.start(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) Result.retry() else Result.success()
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
