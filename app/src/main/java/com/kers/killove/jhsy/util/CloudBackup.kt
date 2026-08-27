package com.kers.killove.jhsy.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.CloudBackupProvider
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 云备份：WebDAV 完整实现；OneDrive / Google 云盘通过各自 WebDAV 兼容端点或应用密码配置。
 * 横竖屏分离备份：仅在 WiFi 下生效时可上传桌面/锁屏缓存图。
 */
object CloudBackup {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun canUpload(context: Context, settings: AppSettings): Boolean {
        if (settings.cloudBackupProvider == CloudBackupProvider.Off) return false
        if (settings.cloudBackupWifiOnly && !isWifi(context)) return false
        return settings.cloudBackupUrl.isNotBlank()
    }

    /** 上传配置 JSON */
    fun uploadConfig(context: Context, settings: AppSettings): Result<String> {
        if (!canUpload(context, settings)) {
            return Result.failure(IllegalStateException("云备份未启用或非 WiFi"))
        }
        val json = ConfigBackup.toJson(settings)
        val remote = joinPath(settings.cloudBackupPath, ConfigBackup.FILE_NAME)
        return putWebDav(settings, remote, json.toByteArray(Charsets.UTF_8), "application/json")
    }

    /** 下载配置 JSON 文本 */
    fun downloadConfig(settings: AppSettings): Result<String> {
        val remote = joinPath(settings.cloudBackupPath, ConfigBackup.FILE_NAME)
        return getWebDav(settings, remote).map { String(it, Charsets.UTF_8) }
    }

    /**
     * 横竖屏壁纸分离备份：把 wallpapers 缓存中带 home/lock 的文件按子目录上传。
     * 仅 WiFi。
     */
    fun uploadOrientSplitWallpapers(context: Context, settings: AppSettings): Result<String> {
        if (!settings.cloudBackupOrientSplit) {
            return Result.failure(IllegalStateException("未开启横竖屏分离备份"))
        }
        if (!canUpload(context, settings)) {
            return Result.failure(IllegalStateException("云备份未启用或非 WiFi"))
        }
        val dir = File(context.filesDir, "wallpapers")
        if (!dir.isDirectory) return Result.success("无缓存可备份")
        val files = dir.listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()
        var n = 0
        for (f in files) {
            val sub = when {
                f.name.contains("_home") -> "home"
                f.name.contains("_lock") -> "lock"
                f.name.contains("_both") -> "both"
                else -> continue
            }
            val remote = joinPath(settings.cloudBackupPath, "wallpapers/$sub/${f.name}")
            putWebDav(settings, remote, f.readBytes(), "image/jpeg").onSuccess { n++ }
        }
        return Result.success("已上传 $n 个壁纸文件")
    }

    private fun joinPath(base: String, name: String): String {
        val b = base.trim().trimEnd('/')
        val n = name.trimStart('/')
        return if (b.isEmpty()) "/$n" else "$b/$n"
    }

    private fun authHeader(settings: AppSettings): String? {
        val u = settings.cloudBackupUser
        val p = settings.cloudBackupPassword
        if (u.isBlank() && p.isBlank()) return null
        return Credentials.basic(u, p)
    }

    private fun putWebDav(
        settings: AppSettings,
        remotePath: String,
        bytes: ByteArray,
        mime: String
    ): Result<String> {
        val base = settings.cloudBackupUrl.trim().trimEnd('/')
        val url = if (remotePath.startsWith("http")) remotePath else base + remotePath
        return try {
            val body = bytes.toRequestBody(mime.toMediaType())
            val b = Request.Builder().url(url).put(body)
            authHeader(settings)?.let { b.header("Authorization", it) }
            http.newCall(b.build()).execute().use { resp ->
                if (resp.isSuccessful || resp.code in 200..299) {
                    Result.success("OK ${resp.code}")
                } else {
                    Result.failure(IllegalStateException("HTTP ${resp.code}: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getWebDav(settings: AppSettings, remotePath: String): Result<ByteArray> {
        val base = settings.cloudBackupUrl.trim().trimEnd('/')
        val url = if (remotePath.startsWith("http")) remotePath else base + remotePath
        return try {
            val b = Request.Builder().url(url).get()
            authHeader(settings)?.let { b.header("Authorization", it) }
            http.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Result.failure(IllegalStateException("HTTP ${resp.code}"))
                } else {
                    Result.success(resp.body?.bytes() ?: ByteArray(0))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
