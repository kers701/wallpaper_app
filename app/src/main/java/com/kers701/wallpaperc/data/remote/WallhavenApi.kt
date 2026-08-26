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
            .header("User-Agent", "Wallpaperc/1.3 (Android)")
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
     * - 响应为图片（image 类型或魔数检测）则预取字节，避免二次下载失败
     * - 文本一行 URL / JSON 常见字段 path/url/image 等
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
                .header("User-Agent", "Wallpaperc/1.3 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("兜底 API HTTP ${response.code}")
                }
                val type = response.header("Content-Type").orEmpty().lowercase()
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) {
                    throw IllegalStateException("兜底 API 空响应")
                }
                // 图片：Content-Type 或文件魔数
                if (isImageContentType(type) || looksLikeImage(bytes)) {
                    return@use WallpaperItem(
                        id = "fb_${System.currentTimeMillis()}",
                        pathUrl = url,
                        thumbsUrl = null,
                        width = width,
                        height = height,
                        purity = "fallback",
                        category = "net_fb",
                        source = "fallback_api",
                        prefetchedBytes = bytes
                    )
                }
                val body = bytes.toString(Charsets.UTF_8)
                val imageUrl = parseFallbackBody(body)
                    ?: throw IllegalStateException(
                        "兜底 API 无法解析图片地址（Content-Type=$type，前缀=${body.take(80)}）"
                    )
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

    private fun isImageContentType(type: String): Boolean {
        if (type.startsWith("image/")) return true
        // 部分图床返回 octet-stream
        if (type.contains("octet-stream")) return true
        return false
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // JPEG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        // PNG
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) return true
        // GIF
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) return true
        // WEBP: RIFF....WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()
        ) return true
        return false
    }

    /**
     * 连通性/延迟探测：返回毫秒；失败抛异常或返回负值由调用方处理。
     */
    data class ProbeResult(
        val name: String,
        val ok: Boolean,
        val latencyMs: Long,
        val detail: String
    )

    suspend fun probeUrl(name: String, url: String): ProbeResult = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Wallpaperc/1.3 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val ms = (System.nanoTime() - start) / 1_000_000
                val type = response.header("Content-Type").orEmpty()
                // 读一点 body 确认链路完整
                val peek = response.body?.bytes()?.size ?: 0
                if (response.isSuccessful) {
                    ProbeResult(name, true, ms, "HTTP ${response.code} · ${peek}B · $type")
                } else {
                    ProbeResult(name, false, ms, "HTTP ${response.code} · $type")
                }
            }
        } catch (e: Exception) {
            val ms = (System.nanoTime() - start) / 1_000_000
            ProbeResult(name, false, ms, e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun probeWallhaven(apiKey: String? = null): ProbeResult = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            val builder = "https://wallhaven.cc/api/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", "nature")
                .addQueryParameter("page", "1")
                .addQueryParameter("sorting", "random")
            if (!apiKey.isNullOrBlank()) builder.addQueryParameter("apikey", apiKey)
            val request = Request.Builder()
                .url(builder.build())
                .header("User-Agent", "Wallpaperc/1.3 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val ms = (System.nanoTime() - start) / 1_000_000
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use ProbeResult("Wallhaven", false, ms, "HTTP ${response.code}")
                }
                val count = try {
                    JSONObject(body).optJSONArray("data")?.length() ?: 0
                } catch (_: Exception) {
                    -1
                }
                ProbeResult("Wallhaven", true, ms, "HTTP ${response.code} · ${count} 条结果")
            }
        } catch (e: Exception) {
            val ms = (System.nanoTime() - start) / 1_000_000
            ProbeResult("Wallhaven", false, ms, e.message ?: e.javaClass.simpleName)
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
            val tags = mutableListOf<String>()
            val tagsArr = o.optJSONArray("tags")
            if (tagsArr != null) {
                for (t in 0 until tagsArr.length()) {
                    val tagObj = tagsArr.optJSONObject(t) ?: continue
                    val name = tagObj.optString("name").trim()
                    if (name.isNotEmpty()) tags += name
                }
            }
            list += WallpaperItem(
                id = o.getString("id"),
                pathUrl = o.getString("path"),
                thumbsUrl = thumbs?.optString("small"),
                width = o.optInt("dimension_x"),
                height = o.optInt("dimension_y"),
                purity = o.optString("purity"),
                category = categoryCode,
                source = "wallhaven",
                tags = tags
            )
        }
        return list
    }

    /** 解析背景 API：返回图片 URL（若直接返回图则仍用原 URL） */
    suspend fun fetchBackgroundImageUrl(templateUrl: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(templateUrl.trim())
            .header("User-Agent", "Wallpaperc/1.3 (Android)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("背景 API HTTP ${response.code}")
            }
            val type = response.header("Content-Type").orEmpty().lowercase()
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (isImageContentType(type) || looksLikeImage(bytes)) {
                return@use templateUrl.trim()
            }
            val body = bytes.toString(Charsets.UTF_8)
            parseFallbackBody(body)
                ?: throw IllegalStateException("背景 API 无法解析图片地址")
        }
    }

    suspend fun downloadToFile(url: String, dest: java.io.File): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Wallpaperc/1.3 (Android)")
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
            .header("User-Agent", "Wallpaperc/1.3 (Android)")
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


    /**
     * 搜索接口往往不带 tags；详情接口补拉标签用于跃迁。
     */
    suspend fun fetchWallpaperTags(id: String, apiKey: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            val builder = "https://wallhaven.cc/api/v1/w/$id".toHttpUrl().newBuilder()
            if (!apiKey.isNullOrBlank()) builder.addQueryParameter("apikey", apiKey)
            val request = Request.Builder()
                .url(builder.build())
                .header("User-Agent", "Wallpaperc/1.3 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                try {
                    val data = JSONObject(body).optJSONObject("data") ?: return@withContext emptyList()
                    val tagsArr = data.optJSONArray("tags") ?: return@withContext emptyList()
                    val out = mutableListOf<String>()
                    for (i in 0 until tagsArr.length()) {
                        val o = tagsArr.optJSONObject(i) ?: continue
                        val name = o.optString("name").trim()
                        if (name.isNotEmpty()) out += name
                    }
                    out
                } catch (_: Exception) {
                    emptyList()
                }
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
