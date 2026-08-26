package com.kers.killove.jhsy.data.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.WallpaperItem
import java.io.File
import java.io.FileOutputStream

/**
 * 本地兜底：静态图 + 视频（抽取帧作为静态壁纸）。
 * 视频扩展名：mp4 / webm / mkv / mov / 3gp
 */
class LocalFallbackStore(private val context: Context) {

    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val videoExts = setOf("mp4", "webm", "mkv", "mov", "3gp")

    fun resolveDir(settings: AppSettings): File {
        val custom = settings.localFallbackDir.trim()
        return if (custom.isNotEmpty()) {
            File(custom)
        } else {
            File(context.filesDir, "local_fallback").also { it.mkdirs() }
        }
    }

    fun listMedia(settings: AppSettings): List<File> {
        val dir = resolveDir(settings)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val all = imageExts + videoExts
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in all }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun listImages(settings: AppSettings): List<File> = listMedia(settings)

    fun pickRandom(settings: AppSettings): WallpaperItem? {
        val files = listMedia(settings)
        if (files.isEmpty()) return null
        val file = files.random()
        val ext = file.extension.lowercase()
        if (ext in videoExts) {
            val frame = extractVideoFrame(file) ?: return pickRandomStaticOnly(settings, exclude = file)
            return WallpaperItem(
                id = "local_vid_${file.nameWithoutExtension}_${frame.name}",
                pathUrl = frame.absolutePath,
                thumbsUrl = null,
                width = 0,
                height = 0,
                purity = "local",
                category = "video",
                source = "local_video"
            )
        }
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

    private fun pickRandomStaticOnly(settings: AppSettings, exclude: File): WallpaperItem? {
        val files = listMedia(settings).filter {
            it != exclude && it.extension.lowercase() in imageExts
        }
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

    /** 抽取视频约 1/3 处一帧，落到 cache */
    fun extractVideoFrame(video: File): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val timeUs = if (durationMs > 0) (durationMs * 1000L / 3) else 1_000_000L
            val bmp: Bitmap = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime ?: return null
            val outDir = File(context.cacheDir, "video_frames").also { it.mkdirs() }
            val out = File(outDir, "${video.nameWithoutExtension}_frame.jpg")
            FileOutputStream(out).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            if (!bmp.isRecycled) bmp.recycle()
            out.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun mediaSummary(settings: AppSettings): String {
        val all = listMedia(settings)
        val imgs = all.count { it.extension.lowercase() in imageExts }
        val vids = all.count { it.extension.lowercase() in videoExts }
        return "${resolveDir(settings).absolutePath}（图 $imgs · 视频 $vids）"
    }

    fun listVideos(settings: AppSettings): List<File> {
        val dir = resolveDir(settings)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val exts = setOf("mp4", "webm", "mkv", "3gp")
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in exts }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun defaultDirPath(): String =
        File(context.filesDir, "local_fallback").absolutePath
}
