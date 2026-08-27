package com.kers.killove.jhsy.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.kers.killove.jhsy.domain.WallpaperFitMode
import com.kers.killove.jhsy.domain.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

class SystemWallpaperSetter(private val context: Context) {

    companion object {
        private const val TAG = "JhsyWallpaperSetter"
        /** 限制画布，避免 :svc 进程 OOM 画出“空”图 */
        private const val MAX_EDGE = 4096
    }

    private val wm: WallpaperManager
        get() = WallpaperManager.getInstance(context.applicationContext)

    fun screenSize(): Pair<Int, Int> {
        val app = context.applicationContext
        // 1) 显示指标（后台服务进程也通常可用）
        val dm = app.resources.displayMetrics
        var w = dm.widthPixels
        var h = dm.heightPixels

        // 2) WindowManager
        try {
            val window = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = window.currentWindowMetrics.bounds
                if (b.width() > 0 && b.height() > 0) {
                    w = b.width()
                    h = b.height()
                }
            } else {
                val m = DisplayMetrics()
                @Suppress("DEPRECATION")
                window.defaultDisplay.getRealMetrics(m)
                if (m.widthPixels > 0 && m.heightPixels > 0) {
                    w = m.widthPixels
                    h = m.heightPixels
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "screenSize WM failed: ${e.message}")
        }

        // 3) 配置兜底
        if (w <= 0 || h <= 0) {
            val cfg = app.resources.configuration
            val density = dm.density.takeIf { it > 0f } ?: 1f
            val cw = (cfg.screenWidthDp * density).toInt()
            val ch = (cfg.screenHeightDp * density).toInt()
            if (cw > 0 && ch > 0) {
                w = cw
                h = ch
            }
        }

        // 4) 硬兜底，避免 0 尺寸
        if (w <= 0) w = 1080
        if (h <= 0) h = 1920
        return w to h
    }

    /** 壁纸画布尺寸：优先 desired，但限制上限，避免超大透明/黑图 */
    fun wallpaperCanvasSize(): Pair<Int, Int> {
        val (sw, sh) = screenSize()
        var cw = try {
            wm.desiredMinimumWidth.takeIf { it > 0 } ?: sw
        } catch (_: Exception) {
            sw
        }
        var ch = try {
            wm.desiredMinimumHeight.takeIf { it > 0 } ?: sh
        } catch (_: Exception) {
            sh
        }
        // desired 有时是屏幕数倍；后台进程内存紧，限制在 2× 屏或 MAX_EDGE
        val maxW = min(MAX_EDGE, max(sw * 2, sw))
        val maxH = min(MAX_EDGE, max(sh * 2, sh))
        if (cw > maxW || ch > maxH) {
            val scale = min(maxW.toFloat() / cw, maxH.toFloat() / ch)
            cw = max(1, (cw * scale).toInt())
            ch = max(1, (ch * scale).toInt())
        }
        return cw to ch
    }

    suspend fun setFromFile(
        file: File,
        target: WallpaperTarget,
        fitMode: WallpaperFitMode = WallpaperFitMode.Fill
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 32L) {
            Log.e(TAG, "file missing or too small: ${file.absolutePath} len=${file.length()}")
            return@withContext false
        }

        // 先尝试解码（带采样），再按模式铺画布后 setBitmap
        val decoded = decodeSampled(file.absolutePath) ?: run {
            Log.e(TAG, "decode failed: ${file.absolutePath}")
            // 解码失败时仍尝试 setStream（系统侧解码），比空图好
            return@withContext setViaStream(file, target)
        }

        if (decoded.width <= 0 || decoded.height <= 0) {
            decoded.recycle()
            return@withContext setViaStream(file, target)
        }

        try {
            val (cw, ch) = wallpaperCanvasSize()
            val fitted = applyFit(decoded, cw, ch, fitMode)
            if (fitted !== decoded) {
                try {
                    decoded.recycle()
                } catch (_: Exception) {
                }
            }

            if (fitted.width <= 0 || fitted.height <= 0) {
                try {
                    fitted.recycle()
                } catch (_: Exception) {
                }
                return@withContext setViaStream(file, target)
            }

            // 拒绝“几乎全透明”的结果（后台画布失败时常见）
            if (isMostlyTransparent(fitted)) {
                Log.w(TAG, "fitted bitmap looks empty, fallback setStream")
                try {
                    fitted.recycle()
                } catch (_: Exception) {
                }
                return@withContext setViaStream(file, target)
            }

            val ok = setBitmapSafe(fitted, target)
            try {
                fitted.recycle()
            } catch (_: Exception) {
            }
            if (!ok) {
                Log.w(TAG, "setBitmap failed, try setStream")
                return@withContext setViaStream(file, target)
            }
            true
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM setFromFile", e)
            System.gc()
            return@withContext setViaStream(file, target)
        } catch (e: Exception) {
            Log.e(TAG, "setFromFile error", e)
            return@withContext setViaStream(file, target)
        }
    }

    private fun setBitmapSafe(bitmap: Bitmap, target: WallpaperTarget): Boolean {
        return try {
            val manager = wm
            when (target) {
                WallpaperTarget.Home -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val r = manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        r != 0
                    } else {
                        @Suppress("DEPRECATION")
                        manager.setBitmap(bitmap)
                        true
                    }
                }
                WallpaperTarget.Lock -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val r = manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        r != 0
                    } else {
                        @Suppress("DEPRECATION")
                        manager.setBitmap(bitmap)
                        true
                    }
                }
                WallpaperTarget.Both -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val a = manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        val b = manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        a != 0 || b != 0
                    } else {
                        @Suppress("DEPRECATION")
                        manager.setBitmap(bitmap)
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setBitmapSafe", e)
            false
        }
    }

    /**
     * 后台服务进程更稳妥：让系统从流解码设置，避免自建超大 Bitmap。
     * 注意：setStream 不支持我们的 Fill/Fit 逻辑，但能避免“空壁纸”。
     */
    private fun setViaStream(file: File, target: WallpaperTarget): Boolean {
        return try {
            val manager = wm
            fun one(flag: Int?): Boolean {
                BufferedInputStream(FileInputStream(file), 256 * 1024).use { input ->
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && flag != null) {
                        manager.setStream(input, null, true, flag) != 0
                    } else {
                        @Suppress("DEPRECATION")
                        manager.setStream(input)
                        true
                    }
                }
            }
            when (target) {
                WallpaperTarget.Home -> one(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) WallpaperManager.FLAG_SYSTEM else null)
                WallpaperTarget.Lock -> one(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) WallpaperManager.FLAG_LOCK else null)
                WallpaperTarget.Both -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val a = one(WallpaperManager.FLAG_SYSTEM)
                        // 流只能读一次，Both 需重开文件
                        val b = one(WallpaperManager.FLAG_LOCK)
                        a || b
                    } else {
                        one(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setViaStream", e)
            false
        }
    }

    private fun decodeSampled(path: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            val (sw, sh) = screenSize()
            val targetMax = min(MAX_EDGE, max(sw, sh) * 2)
            if (maxSide > targetMax && targetMax > 0) {
                while (maxSide / sample > targetMax) sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "decode OOM, try smaller", e)
            System.gc()
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(path, opts)
            } catch (_: Exception) {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "decodeSampled", e)
            null
        }
    }

    private fun isMostlyTransparent(bmp: Bitmap): Boolean {
        return try {
            if (!bmp.hasAlpha()) return false
            val w = bmp.width
            val h = bmp.height
            if (w <= 0 || h <= 0) return true
            // 抽样检查四角与中心 alpha
            val pts = listOf(
                0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1, w / 2 to h / 2
            )
            var transparent = 0
            for ((x, y) in pts) {
                val a = (bmp.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1)) ushr 24) and 0xff
                if (a < 8) transparent++
            }
            transparent >= 4
        } catch (_: Exception) {
            false
        }
    }

    private fun applyFit(src: Bitmap, canvasW: Int, canvasH: Int, mode: WallpaperFitMode): Bitmap {
        if (canvasW <= 0 || canvasH <= 0) return src
        if (src.width <= 0 || src.height <= 0) return src

        val out = try {
            Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "createBitmap OOM $canvasW x $canvasH", e)
            return src
        }

        // 不透明黑底，避免“空壁纸”观感（Fit 留边时也有底）
        val canvas = Canvas(out)
        canvas.drawColor(0xFF000000.toInt())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        when (mode) {
            WallpaperFitMode.Stretch -> {
                canvas.drawBitmap(src, null, RectF(0f, 0f, canvasW.toFloat(), canvasH.toFloat()), paint)
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
                val scale = max(canvasW.toFloat() / src.width, canvasH.toFloat() / src.height)
                val w = src.width * scale
                val h = src.height * scale
                val left = (canvasW - w) / 2f
                val top = (canvasH - h) / 2f
                canvas.drawBitmap(src, null, RectF(left, top, left + w, top + h), paint)
            }
            WallpaperFitMode.Center -> {
                // 原图像素居中；大于画布的部分自然被裁切，小于则留黑边
                val left = (canvasW - src.width) / 2f
                val top = (canvasH - src.height) / 2f
                canvas.drawBitmap(src, left, top, paint)
            }
        }
        return out
    }
}
