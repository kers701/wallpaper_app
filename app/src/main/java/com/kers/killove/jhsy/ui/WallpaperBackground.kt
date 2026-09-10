package com.kers.killove.jhsy.ui

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.kers.killove.jhsy.data.remote.WallhavenApi
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.BgMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * 软件背景：
 * 1. 填了本地路径 → 读本地图
 * 2. 填了 API 链接 → 打开时自动拉取
 * 3. 都为空 → 尝试系统壁纸，失败则莫奈渐变取色
 */
/** 供卡片磨砂：当前软件背景图（已加载），高斯模式卡片背后叠一层模糊图 */
val LocalBackdropBitmap = staticCompositionLocalOf<androidx.compose.ui.graphics.ImageBitmap?> { null }

@Composable
fun WallpaperBackground(
    settings: AppSettings,
    scrimAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var useMonet by remember { mutableStateOf(false) }

    LaunchedEffect(settings.bgApiUrl, settings.bgLocalPath, settings.bgMode) {
        bitmap = null
        useMonet = false
        val local = settings.bgLocalPath.trim()
        val api = settings.bgApiUrl.trim()

        val loaded = withContext(Dispatchers.IO) {
            // 1. 本地路径
            if (local.isNotEmpty()) {
                val f = File(local)
                if (f.exists() && f.isFile) {
                    runCatching {
                        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                    }.getOrNull()?.let { return@withContext it to false }
                }
            }
            // 2. API
            if (api.isNotEmpty()) {
                runCatching {
                    val imageUrl = WallhavenApi().fetchBackgroundImageUrl(api)
                    URL(imageUrl).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()?.let { return@withContext it to false }
            }
            // 3. 系统壁纸
            if (settings.bgMode != BgMode.Monet) {
                runCatching {
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
                        return@withContext bmp.asImageBitmap() to false
                    }
                }
            }
            // 4. 莫奈
            null to true
        }
        bitmap = loaded.first
        useMonet = loaded.second || loaded.first == null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bmp = bitmap
        when {
            bmp != null && !useMonet -> {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                // 莫奈风格：Material 主题色渐变
                val c = MaterialTheme.colorScheme
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    c.primaryContainer.copy(alpha = 0.95f),
                                    c.secondaryContainer.copy(alpha = 0.9f),
                                    c.tertiaryContainer.copy(alpha = 0.85f),
                                    c.surface
                                )
                            )
                        )
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        )
        CompositionLocalProvider(LocalBackdropBitmap provides bitmap) {
            content()
        }
    }
}
