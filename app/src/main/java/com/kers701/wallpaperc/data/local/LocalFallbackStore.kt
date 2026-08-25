package com.kers701.wallpaperc.data.local

import android.content.Context
import com.kers701.wallpaperc.domain.AppSettings
import com.kers701.wallpaperc.domain.WallpaperItem
import java.io.File

/**
 * 本地兜底图库：从配置目录或 App 私有 local_fallback/ 随机取图。
 */
class LocalFallbackStore(private val context: Context) {

    fun resolveDir(settings: AppSettings): File {
        val custom = settings.localFallbackDir.trim()
        return if (custom.isNotEmpty()) {
            File(custom)
        } else {
            File(context.filesDir, "local_fallback").also { it.mkdirs() }
        }
    }

    fun listImages(settings: AppSettings): List<File> {
        val dir = resolveDir(settings)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val exts = setOf("jpg", "jpeg", "png", "webp", "bmp")
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in exts }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun pickRandom(settings: AppSettings): WallpaperItem? {
        val files = listImages(settings)
        if (files.isEmpty()) return null
        val file = files.random()
        return WallpaperItem(
            id = "local_${file.name}",
            pathUrl = file.absolutePath,
            thumbsUrl = null,
            width = 0,
            height = 0,
            purity = "local",
            category = "local",
            source = "local"
        )
    }

    fun defaultDirPath(): String =
        File(context.filesDir, "local_fallback").absolutePath
}
