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
 * 状态栏快捷设置磁贴：一键开关「自动更换」。
 * 状态仅反映 settings.enabled，与「超级服务」解耦（超级服务只保活进程，不点亮磁贴）。
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
        // 以桥接文件为准，避免 UI 与 DataStore 短暂不一致
        val currentlyOn = ProcessBridgePrefs.enabled(applicationContext)
        applyTileUi(!currentlyOn)
        scope.launch {
            try {
                toggleService(!currentlyOn)
            } catch (e: Exception) {
                RunLog.i(applicationContext, "tile toggle failed: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    refreshTile()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        // 只反映「自动更换」开关，超级服务开启时磁贴仍可为灰色
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
        val repo = SettingsRepository(ctx)
        val s = repo.settingsFlow.first()
        val next = s.copy(enabled = enable)
        repo.save(next)
        ProcessBridgePrefs.sync(ctx, next)
        if (enable) {
            WallpaperForegroundService.start(ctx)
            ChangeWallpaperWorker.enqueue(ctx, next.intervalMinutes)
            RunLog.i(ctx, "tile: auto change ON")
        } else {
            // 关闭自动更换：取消定时任务；仅当超级服务也关闭时才停 FGS
            ChangeWallpaperWorker.cancel(ctx)
            if (!next.superServiceEnabled) {
                WallpaperForegroundService.stop(ctx)
            }
            // 超级服务仍开：进程可保活，但 runLoop 不再因 enabled=false 执行自动更换
            RunLog.i(ctx, "tile: auto change OFF (super=${next.superServiceEnabled})")
        }
    }
}
