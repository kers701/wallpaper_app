package com.kers701.wallpaperc.ui

import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/** 以当前系统桌面壁纸作为背景，叠加半透明遮罩保证文字可读。 */
@Composable
fun WallpaperBackground(
    scrimAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    DisposableEffect(Unit) {
        try {
            val wm = WallpaperManager.getInstance(context)
            val drawable: Drawable? = try {
                wm.drawable
            } catch (_: SecurityException) {
                wm.peekDrawable()
            } catch (_: Exception) {
                null
            }
            if (drawable != null) {
                val bmp = when (drawable) {
                    is BitmapDrawable -> drawable.bitmap
                    else -> drawable.toBitmap(
                        width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080),
                        height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920)
                    )
                }
                bitmap = bmp.asImageBitmap()
            }
        } catch (_: Exception) {
            bitmap = null
        }
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        )
        content()
    }
}
