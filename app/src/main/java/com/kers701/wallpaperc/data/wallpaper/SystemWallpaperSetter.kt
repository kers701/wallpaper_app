package com.kers701.wallpaperc.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import com.kers701.wallpaperc.domain.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SystemWallpaperSetter(private val context: Context) {

    private val wm: WallpaperManager
        get() = WallpaperManager.getInstance(context)

    suspend fun setFromFile(file: File, target: WallpaperTarget): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: return@withContext false
                when (target) {
                    WallpaperTarget.Home -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        } else {
                            @Suppress("DEPRECATION")
                            wm.setBitmap(bitmap)
                        }
                    }
                    WallpaperTarget.Lock -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        } else {
                            @Suppress("DEPRECATION")
                            wm.setBitmap(bitmap)
                        }
                    }
                    WallpaperTarget.Both -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wm.setBitmap(
                                bitmap, null, true,
                                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            wm.setBitmap(bitmap)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}
