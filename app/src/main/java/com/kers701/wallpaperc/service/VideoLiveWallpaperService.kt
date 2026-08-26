package com.kers701.wallpaperc.service

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File

/**
 * 简易视频动态壁纸：播放 App 私有目录 live_wallpaper/ 下选定的视频文件。
 * 用户需在系统「动态壁纸」中选择本应用。
 */
class VideoLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var player: MediaPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startPlayer(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            try {
                if (visible) player?.start() else player?.pause()
            } catch (_: Exception) {
            }
        }

        private fun startPlayer(holder: SurfaceHolder) {
            releasePlayer()
            val dir = File(applicationContext.filesDir, "live_wallpaper")
            val video = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in setOf("mp4", "webm", "mkv", "3gp") }
                ?.maxByOrNull { it.lastModified() }
                ?: return
            try {
                player = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(video.absolutePath)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                releasePlayer()
            }
        }

        private fun releasePlayer() {
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = null
        }
    }
}
