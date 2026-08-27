package com.kers.killove.jhsy.data.local

import android.content.Context
import com.kers.killove.jhsy.domain.WallpaperTarget
import java.io.File

/**
 * 概览页独立预览缓存，与更换记录（Room）无关。
 * 清空记录不会影响此处；每次成功设壁纸时更新对应槽位。
 */
object OverviewCacheStore {

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "overview_cache").apply { mkdirs() }

    fun homeFile(context: Context): File = File(dir(context), "preview_home.jpg")
    fun lockFile(context: Context): File = File(dir(context), "preview_lock.jpg")

    /** 成功设壁纸后写入概览槽（拷贝文件） */
    fun update(context: Context, target: WallpaperTarget, source: File) {
        if (!source.exists() || source.length() < 32L) return
        runCatching {
            when (target) {
                WallpaperTarget.Home -> source.copyTo(homeFile(context), overwrite = true)
                WallpaperTarget.Lock -> source.copyTo(lockFile(context), overwrite = true)
                WallpaperTarget.Both -> {
                    source.copyTo(homeFile(context), overwrite = true)
                    source.copyTo(lockFile(context), overwrite = true)
                }
            }
        }
    }

    /** 仅返回概览缓存路径，不读更换记录 */
    fun paths(context: Context): Pair<String?, String?> {
        val h = homeFile(context).takeIf { it.exists() && it.length() > 32L }?.absolutePath
        val l = lockFile(context).takeIf { it.exists() && it.length() > 32L }?.absolutePath
        return h to (l ?: h)
    }

    fun clear(context: Context) {
        runCatching { homeFile(context).delete() }
        runCatching { lockFile(context).delete() }
    }
}
