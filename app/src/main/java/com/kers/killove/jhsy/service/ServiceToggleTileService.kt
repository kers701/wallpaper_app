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
import kotlinx.coroutines.withContext

/**
 * 状态栏快捷设置磁贴：一键开关「自动更换」服务。
 * 需用户在系统「编辑图块」中手动添加。
 */
@RequiresApi(Build.VERSION_CODES.N)
class ServiceToggleTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val currentlyActive = tile.state == Tile.STATE_ACTIVE
        applyTileUi(!currentlyActive)
        val pending = goAsync()
        scope.launch {
            try {
                toggleService(!currentlyActive)
                withContext(Dispatchers.Main) {
                    refreshTile()
                }
            } catch (e: Exception) {
                RunLog.i(applicationContext, "tile toggle failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    refreshTile()
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        val on = ProcessBridgePrefs.enabled(applicationContext) ||
            ProcessBridgePrefs.superService(applicationContext)
        applyTileUi(on)
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
        val repo = SettingsRepository(ctx)
        val s = repo.settingsFlow.first()
        val next = s.copy(enabled = enable)
        repo.save(next)
        ProcessBridgePrefs.sync(ctx, next)
        if (!enable && !next.superServiceEnabled) {
            ChangeWallpaperWorker.cancel(ctx)
            WallpaperForegroundService.stop(ctx)
            RunLog.i(ctx, "tile: auto change OFF")
        } else {
            WallpaperForegroundService.start(ctx)
            ChangeWallpaperWorker.enqueue(ctx, next.intervalMinutes)
            RunLog.i(ctx, "tile: auto change ON")
        }
    }
}
