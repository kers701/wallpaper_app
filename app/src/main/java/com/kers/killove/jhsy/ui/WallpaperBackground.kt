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
 * 背景模式决定唯一数据源：Auto 为本地→API→系统壁纸→莫奈回退，
 * Api 仅使用 API，Local 仅使用本地路径，Monet 仅使用莫奈渐变。
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
        val loaded = withContext(Dispatchers.IO) {
            fun loadLocal(): androidx.compose.ui.graphics.ImageBitmap? {
                val path = settings.bgLocalPath.trim()
                if (path.isEmpty()) return null
                val f = File(path)
                if (!f.exists() || !f.isFile) return null
                return runCatching {
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                }.getOrNull()
            }

            fun loadApi(): androidx.compose.ui.graphics.ImageBitmap? {
                val api = settings.bgApiUrl.trim()
                if (api.isEmpty()) return null
                return runCatching {
                    val imageUrl = WallhavenApi().fetchBackgroundImageUrl(api)
                    URL(imageUrl).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }

            fun loadSystem(): androidx.compose.ui.graphics.ImageBitmap? {
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
                        return bmp.asImageBitmap()
                    }
                }.getOrNull()
            }

            when (settings.bgMode) {
                BgMode.Local -> loadLocal()?.let { it to false } ?: (null to true)
                BgMode.Api -> loadApi()?.let { it to false } ?: (null to true)
                BgMode.Monet -> null to true
                BgMode.Auto -> {
                    val image = loadLocal() ?: loadApi() ?: loadSystem()
                    image to (image == null)
                }
            }
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
