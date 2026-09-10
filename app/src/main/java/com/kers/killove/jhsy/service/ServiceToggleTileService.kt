package com.kers.killove.jhsy.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.kers.killove.jhsy.R
import com.kers.killove.jhsy.data.prefs.SettingsRepository
import com.kers.killove.jhsy.util.ProcessBridgePrefs
import com.kers.killove.jhsy.util.RunLog
import com.kers.killove.jhsy.worker.ChangeWallpaperWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 状态栏快捷设置磁贴：一键开关「自动更换」。
 * 状态仅反映 settings.enabled（跨进程文件），与「超级服务」解耦。
 */
@RequiresApi(Build.VERSION_CODES.N)
class ServiceToggleTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val toggleMutex = Mutex()

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val currentlyOn = ProcessBridgePrefs.enabled(applicationContext)
        val targetOn = !currentlyOn
        ProcessBridgePrefs.writeEnabledFile(applicationContext, targetOn)
        applyTileUi(targetOn)
        scope.launch {
            toggleMutex.withLock {
                try {
                    toggleService(targetOn)
                } catch (e: Exception) {
                    RunLog.i(applicationContext, "tile toggle failed: ${e.message}")
                } finally {
                    withContext(Dispatchers.Main) {
                        refreshTile()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        applyTileUi(ProcessBridgePrefs.enabled(applicationContext))
    }

    private fun applyTileUi(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_service_label)
        tile.contentDescription = getString(
            if (active) R.string.tile_service_desc_on else R.string.tile_service_desc_off
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (active) R.string.tile_service_subtitle_on else R.string.tile_service_subtitle_off
            )
        }
        tile.updateTile()
    }

    private suspend fun toggleService(enable: Boolean) {
        val ctx = applicationContext
        ProcessBridgePrefs.writeEnabledFile(ctx, enable)
        val repo = SettingsRepository(ctx)
        val s = repo.settingsFlow.first()
        val next = s.copy(enabled = enable)
        repo.save(next, writeEnabled = true)
        if (enable) {
            WallpaperForegroundService.start(ctx)
            ChangeWallpaperWorker.enqueue(ctx, next.intervalMinutes)
            RunLog.i(ctx, "tile: auto change ON")
        } else {
            ChangeWallpaperWorker.cancel(ctx)
            if (!next.superServiceEnabled) {
                WallpaperForegroundService.stop(ctx)
            }
            RunLog.i(ctx, "tile: auto change OFF (super=${next.superServiceEnabled})")
        }
    }
}
