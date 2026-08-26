package com.kers701.wallpaperc.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.kers701.wallpaperc.domain.WallpaperFitMode
import com.kers701.wallpaperc.domain.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

class SystemWallpaperSetter(private val context: Context) {

    private val wm: WallpaperManager
        get() = WallpaperManager.getInstance(context)

    fun screenSize(): Pair<Int, Int> {
        val window = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = window.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            window.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    /** 壁纸管理器建议尺寸（更接近系统实际铺满区域） */
    fun wallpaperCanvasSize(): Pair<Int, Int> {
        val dw = wm.desiredMinimumWidth.takeIf { it > 0 }
        val dh = wm.desiredMinimumHeight.takeIf { it > 0 }
        val (sw, sh) = screenSize()
        return (dw ?: sw) to (dh ?: sh)
    }

    suspend fun setFromFile(
        file: File,
        target: WallpaperTarget,
        fitMode: WallpaperFitMode = WallpaperFitMode.Fill
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext false
            val (cw, ch) = wallpaperCanvasSize()
            val bitmap = applyFit(raw, cw, ch, fitMode)
            if (bitmap !== raw) raw.recycle()

            // 隔离时务必只带一个 FLAG，避免部分 ROM 把「仅桌面」也写到锁屏
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
                        // 分两次设置，避免部分机型合成失败
                        wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
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

    /**
     * Windows 风格：
     * - Fill 填充：等比放大直到短边铺满，长边超出部分不显示（不先裁原图再拉伸）
     * - Fit 适应：完整可见，可能留边
     * - Stretch 拉伸：强制拉到画布大小
     */
    private fun applyFit(src: Bitmap, canvasW: Int, canvasH: Int, mode: WallpaperFitMode): Bitmap {
        if (canvasW <= 0 || canvasH <= 0) return src
        val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        when (mode) {
            WallpaperFitMode.Stretch -> {
                val dst = RectF(0f, 0f, canvasW.toFloat(), canvasH.toFloat())
                canvas.drawBitmap(src, null, dst, paint)
            }
            WallpaperFitMode.Fit -> {
                val scale = min(canvasW.toFloat() / src.width, canvasH.toFloat() / src.height)
                val w = src.width * scale
                val h = src.height * scale
                val left = (canvasW - w) / 2f
                val top = (canvasH - h) / 2f
                canvas.drawBitmap(src, null, RectF(left, top, left + w, top + h), paint)
            }
            WallpaperFitMode.Fill -> {
                // 等比放大到完全覆盖画布，再居中绘制（超出部分自然在画布外）
                val scale = max(canvasW.toFloat() / src.width, canvasH.toFloat() / src.height)
                val w = src.width * scale
                val h = src.height * scale
                val left = (canvasW - w) / 2f
                val top = (canvasH - h) / 2f
                canvas.drawBitmap(src, null, RectF(left, top, left + w, top + h), paint)
            }
        }
        return out
    }
}
