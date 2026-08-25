package com.kers701.wallpaperc.data.remote

import com.kers701.wallpaperc.domain.AppSettings
import com.kers701.wallpaperc.domain.CategoryMode
import com.kers701.wallpaperc.domain.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WallhavenApi(
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * categories: general, anime, people → 三位
     * zr ≈ people, dm ≈ anime
     */
    suspend fun search(
        settings: AppSettings,
        categoryCode: String,
        keyword: String? = null,
        page: Int = 1
    ): List<WallpaperItem> = withContext(Dispatchers.IO) {
        val categories = when (categoryCode) {
            "dm" -> "010"
            "zr" -> "001"
            else -> "111"
        }
        val (atleast, ratios) = when (settings.resolutionMode) {
            com.kers701.wallpaperc.domain.ResolutionMode.Min15k ->
                "1500x1500" to null
            com.kers701.wallpaperc.domain.ResolutionMode.Custom ->
                "${settings.minWidth}x${settings.minHeight}" to null
            else -> null to "portrait"
        }

        val urlBuilder = "https://wallhaven.cc/api/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("purity", settings.purity.code)
            .addQueryParameter("categories", categories)
            .addQueryParameter("sorting", if (keyword.isNullOrBlank()) "random" else "relevance")
            .addQueryParameter("page", page.toString())
        atleast?.let { urlBuilder.addQueryParameter("atleast", it) }
        ratios?.let { urlBuilder.addQueryParameter("ratios", it) }
        if (!keyword.isNullOrBlank()) {
            urlBuilder.addQueryParameter("q", keyword)
        }
        settings.nextApiKey()?.let {
            urlBuilder.addQueryParameter("apikey", it)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "Wallpaperc/1.1 (Android)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Wallhaven HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("空响应")
            parseSearch(body, categoryCode)
        }
    }

    /**
     * 网络兜底：请求用户配置的 URL。
     * - 若响应 Content-Type 为 image/* → 直接当图片地址用（返回合成 item，path 为该 url）
     * - 若为文本一行 URL
     * - 若为 JSON，尝试常见字段 path/url/image 等
     */
    suspend fun fetchFallbackApi(templateUrl: String, width: Int, height: Int): WallpaperItem =
        withContext(Dispatchers.IO) {
            val url = templateUrl
                .replace("{width}", width.toString())
                .replace("{height}", height.toString())
                .replace("{w}", width.toString())
                .replace("{h}", height.toString())
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Wallpaperc/1.1 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("兜底 API HTTP ${response.code}")
                }
                val type = response.header("Content-Type").orEmpty()
                val body = response.body?.string().orEmpty()
                if (type.startsWith("image/")) {
                    // 响应本身是图：用请求 URL 作为下载地址
                    return@use WallpaperItem(
                        id = "fb_${System.currentTimeMillis()}",
                        pathUrl = url,
                        thumbsUrl = null,
                        width = width,
                        height = height,
                        purity = "fallback",
                        category = "net_fb",
                        source = "fallback_api"
                    )
                }
                val imageUrl = parseFallbackBody(body)
                    ?: throw IllegalStateException("兜底 API 无法解析图片地址")
                WallpaperItem(
                    id = "fb_${imageUrl.hashCode()}_${System.currentTimeMillis() % 100000}",
                    pathUrl = imageUrl,
                    thumbsUrl = null,
                    width = width,
                    height = height,
                    purity = "fallback",
                    category = "net_fb",
                    source = "fallback_api"
                )
            }
        }

    private fun parseFallbackBody(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.lineSequence().first().trim()
        }
        return try {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                if (arr.length() == 0) return null
                extractUrl(arr.get(0))
            } else {
                extractUrl(JSONObject(trimmed))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractUrl(node: Any): String? {
        when (node) {
            is String -> if (node.startsWith("http")) return node
            is JSONObject -> {
                val keys = listOf(
                    "path", "url", "image", "image_url", "img", "src",
                    "content", "file", "download_url"
                )
                for (k in keys) {
                    val v = node.opt(k) ?: continue
                    when (v) {
                        is String -> if (v.startsWith("http")) return v
                        is JSONObject -> extractUrl(v)?.let { return it }
                    }
                }
                node.optJSONObject("data")?.let { extractUrl(it)?.let { u -> return u } }
                node.optJSONArray("data")?.let { arr ->
                    if (arr.length() > 0) extractUrl(arr.get(0))?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseSearch(json: String, categoryCode: String): List<WallpaperItem> {
        val root = JSONObject(json)
        val data = root.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<WallpaperItem>()
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val thumbs = o.optJSONObject("thumbs")
            list += WallpaperItem(
                id = o.getString("id"),
                pathUrl = o.getString("path"),
                thumbsUrl = thumbs?.optString("small"),
                width = o.optInt("dimension_x"),
                height = o.optInt("dimension_y"),
                purity = o.optString("purity"),
                category = categoryCode,
                source = "wallhaven"
            )
        }
        return list
    }

    suspend fun downloadToFile(url: String, dest: java.io.File): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Wallpaperc/1.1 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val bytes = response.body?.bytes() ?: return@withContext false
                dest.parentFile?.mkdirs()
                dest.writeBytes(bytes)
                true
            }
        }

    /** 拉取远程 txt，每行一个关键词 */
    suspend fun fetchRemoteKeywordList(url: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Wallpaperc/1.1 (Android)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("关键词列表 HTTP ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
    }

    fun nextCategory(settings: AppSettings): String {
        return when (settings.categoryMode) {
            CategoryMode.Zr -> "zr"
            CategoryMode.Dm -> "dm"
            CategoryMode.Rotate ->
                if (settings.lastCategory == "zr") "dm" else "zr"
        }
    }
}
