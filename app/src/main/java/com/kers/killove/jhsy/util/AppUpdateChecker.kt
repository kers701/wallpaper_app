package com.kers.killove.jhsy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kers.killove.jhsy.BuildConfig
import com.kers.killove.jhsy.data.remote.ProxyHttp
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * 从 GitHub Releases 检查并下载最新 APK。
 * 仓库：kers701/wallpaper_app
 */
object AppUpdateChecker {

    private const val OWNER = "kers701"
    private const val REPO = "wallpaper_app"
    private const val API_LATEST =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class ReleaseInfo(
        val tag: String,
        val name: String,
        val versionName: String,
        val apkUrl: String?,
        val apkName: String?,
        val apkSize: Long,
        val htmlUrl: String,
        val body: String
    )

    sealed class CheckResult {
        data class UpToDate(val current: String, val latest: String) : CheckResult()
        data class UpdateAvailable(val info: ReleaseInfo) : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    sealed class DownloadResult {
        data class Ok(val file: File) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }

    /** 当前安装版本号（versionName，去掉 -debug 后缀） */
    fun currentVersionName(): String =
        BuildConfig.VERSION_NAME.substringBefore("-").trim()

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    /**
     * 拉取 GitHub latest release，与本机 versionName 比较。
     * tag 形如 v3.0.29 / 3.0.29。
     */
    fun checkLatest(): CheckResult {
        return try {
            val req = Request.Builder()
                .url(API_LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "jhsy-app-update/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            ProxyHttp.execute(req).use { resp ->
                if (!resp.isSuccessful) {
                    return CheckResult.Failed("GitHub ${resp.code}：${resp.message}")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return CheckResult.Failed("空响应")
                val root = JSONObject(body)
                val tag = root.optString("tag_name").ifBlank {
                    return CheckResult.Failed("无 tag_name")
                }
                val versionName = tag.removePrefix("v").removePrefix("V").trim()
                var apkUrl: String? = null
                var apkName: String? = null
                var apkSize = 0L
                val assets = root.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val n = a.optString("name")
                        if (n.endsWith(".apk", ignoreCase = true)) {
                            // 优先 release 命名
                            val url = a.optString("browser_download_url")
                            if (url.isBlank()) continue
                            if (apkUrl == null || n.contains("release", ignoreCase = true)) {
                                apkUrl = url
                                apkName = n
                                apkSize = a.optLong("size", 0L)
                            }
                        }
                    }
                }
                val info = ReleaseInfo(
                    tag = tag,
                    name = root.optString("name").ifBlank { tag },
                    versionName = versionName,
                    apkUrl = apkUrl,
                    apkName = apkName,
                    apkSize = apkSize,
                    htmlUrl = root.optString("html_url")
                        .ifBlank { "https://github.com/$OWNER/$REPO/releases/tag/$tag" },
                    body = root.optString("body")
                )
                val current = currentVersionName()
                return if (compareVersion(versionName, current) > 0) {
                    CheckResult.UpdateAvailable(info)
                } else {
                    CheckResult.UpToDate(current, versionName)
                }
            }
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 比较 a 与 b：a>b 返回正，相等 0，a<b 负。支持 3.0.29 / 3.0.34 */
    fun compareVersion(a: String, b: String): Int {
        fun parts(s: String): List<Int> =
            s.split('.', '-', '_')
                .map { it.filter { ch -> ch.isDigit() } }
                .filter { it.isNotEmpty() }
                .map { it.toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    fun downloadApk(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit = {}): DownloadResult {
        val url = info.apkUrl ?: return DownloadResult.Failed("该版本没有 APK 资源")
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, info.apkName ?: "app-update.apk")
            if (out.exists()) out.delete()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "jhsy-app-update/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            ProxyHttp.execute(req).use { resp ->
                if (!resp.isSuccessful) {
                    return DownloadResult.Failed("下载失败 HTTP ${resp.code}")
                }
                val body = resp.body ?: return DownloadResult.Failed("空响应体")
                val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            sum += read
                            if (total > 0) onProgress((sum.toFloat() / total).coerceIn(0f, 1f))
                        }
                        output.flush()
                    }
                }
            }
            if (!out.exists() || out.length() < 1024) {
                return DownloadResult.Failed("下载文件无效")
            }
            onProgress(1f)
            DownloadResult.Ok(out)
        } catch (e: Exception) {
            DownloadResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun openReleasePage(context: Context, htmlUrl: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
