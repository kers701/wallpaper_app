package com.kers.killove.jhsy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.util.GitSync
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class GitSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = SettingsRepository(applicationContext)
            val s = repo.settingsFlow.first()
            if (!s.gitSyncEnabled || s.gitUploadIntervalMinutes <= 0) return Result.success()
            if (!GitSync.canSync(s)) return Result.success()
            val r = GitSync.upload(applicationContext, s)
            if (r.isSuccess) {
                repo.save(s.copy(gitLastUploadAt = System.currentTimeMillis()))
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "jhsy_git_sync_periodic"

        fun schedule(context: Context, intervalMinutes: Int) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (intervalMinutes <= 0) {
                wm.cancelUniqueWork(UNIQUE)
                return
            }
            val period = intervalMinutes.coerceIn(15, 24 * 60).toLong()
            val req = PeriodicWorkRequestBuilder<GitSyncWorker>(period, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
