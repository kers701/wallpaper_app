package com.kers701.wallpaperc.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.kers701.wallpaperc.domain.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

class SystemWallpaperSetter(private val context: Context) {

    private val wm: WallpaperManager
        get() = WallpaperManager.getInstance(context)

    fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    suspend fun setFromFile(
        file: File,
        target: WallpaperTarget,
        cropFill: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext false
            val bitmap = if (cropFill) centerCropToScreen(raw) else raw
            if (bitmap !== raw) raw.recycle()
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

    /** 中心裁切填充：等比放大后裁到屏幕比例 */
    private fun centerCropToScreen(src: Bitmap): Bitmap {
        val (sw, sh) = screenSize()
        if (sw <= 0 || sh <= 0) return src
        val scale = max(sw.toFloat() / src.width, sh.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(1)
        val matrix = Matrix().apply { setScale(scale, scale) }
        val scaled = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        val x = ((scaledW - sw) / 2).coerceAtLeast(0)
        val y = ((scaledH - sh) / 2).coerceAtLeast(0)
        val w = sw.coerceAtMost(scaled.width)
        val h = sh.coerceAtMost(scaled.height)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(scaled, -x.toFloat(), -y.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        if (scaled !== src) scaled.recycle()
        return out
    }
}
