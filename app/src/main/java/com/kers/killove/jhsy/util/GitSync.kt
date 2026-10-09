package com.kers.killove.jhsy.util

import android.content.Context
import android.util.Base64
import com.kers.killove.jhsy.domain.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 基于 GitHub Contents API 的配置云同步。
 * 仓库内固定路径：miroflweat/config.json
 */
object GitSync {
    const val REMOTE_PATH = "miroflweat/config.json"
    private const val API = "https://api.github.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class RepoRef(val owner: String, val repo: String)

    fun parseRepo(raw: String): RepoRef? {
        val s = raw.trim().removeSuffix(".git")
        if (s.isBlank()) return null
        val m = Regex("""(?:https?://)?(?:www\.)?github\.com[/:]([^/]+)/([^/]+)""").find(s)
        if (m != null) return RepoRef(m.groupValues[1], m.groupValues[2].removeSuffix(".git"))
        val parts = s.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size == 2) return RepoRef(parts[0], parts[1])
        return null
    }

    const val DEFAULT_REPO_NAME = "wallpaper_back"

    /** 生效仓库：自定义开启用 gitRepo，否则 {用户名}/wallpaper_back */
    fun effectiveRepo(settings: AppSettings): String {
        if (settings.gitRepoCustom) {
            return settings.gitRepo.trim()
        }
        val user = settings.gitUserName.trim()
        if (user.isBlank()) return ""
        return "$user/$DEFAULT_REPO_NAME"
    }

    fun canSync(settings: AppSettings): Boolean {
        if (settings.gitUserName.isBlank() || settings.gitUserEmail.isBlank()) return false
        if (settings.gitToken.isBlank()) return false
        return parseRepo(effectiveRepo(settings)) != null
    }

    suspend fun upload(context: Context, settings: AppSettings): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!canSync(settings)) {
                    return@withContext Result.failure(IllegalStateException("请先配置 Git 用户名、邮箱、令牌与仓库"))
                }
                val ref = parseRepo(effectiveRepo(settings))!!
                val json = ConfigBackup.toJsonSelective(settings)
                val contentB64 = Base64.encodeToString(
                    json.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                val sha = getFileSha(ref, settings.gitBranch, settings.gitToken)
                val body = JSONObject()
                    .put("message", "MiroFlweat cloud sync ${System.currentTimeMillis()}")
                    .put("content", contentB64)
                    .put("branch", settings.gitBranch.ifBlank { "main" })
                    .put(
                        "committer",
                        JSONObject()
                            .put("name", settings.gitUserName)
                            .put("email", settings.gitUserEmail)
                    )
                if (sha != null) body.put("sha", sha)

                val req = Request.Builder()
                    .url("$API/repos/${ref.owner}/${ref.repo}/contents/$REMOTE_PATH")
                    .header("Authorization", "Bearer ${settings.gitToken.trim()}")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .put(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("上传失败 HTTP ${resp.code}: ${text.take(200)}")
                        )
                    }
                    Result.success("已上传到 ${ref.owner}/${ref.repo}@$REMOTE_PATH")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun download(settings: AppSettings): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!canSync(settings)) {
                    return@withContext Result.failure(IllegalStateException("请先配置 Git 用户名、邮箱、令牌与仓库"))
                }
                val ref = parseRepo(effectiveRepo(settings))!!
                val branch = settings.gitBranch.ifBlank { "main" }
                val req = Request.Builder()
                    .url("$API/repos/${ref.owner}/${ref.repo}/contents/$REMOTE_PATH?ref=$branch")
                    .header("Authorization", "Bearer ${settings.gitToken.trim()}")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("拉取失败 HTTP ${resp.code}: ${text.take(200)}")
                        )
                    }
                    val o = JSONObject(text)
                    val b64 = o.optString("content").replace("\n", "")
                    if (b64.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("远程文件为空"))
                    }
                    val json = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                    Result.success(json)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun getFileSha(ref: RepoRef, branch: String, token: String): String? {
        return try {
            val b = branch.ifBlank { "main" }
            val req = Request.Builder()
                .url("$API/repos/${ref.owner}/${ref.repo}/contents/$REMOTE_PATH?ref=$b")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val o = JSONObject(resp.body?.string().orEmpty())
                o.optString("sha").ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }
}
