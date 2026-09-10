package com.kers.killove.jhsy.data.translate

import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.TranslateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import com.kers.killove.jhsy.data.remote.ProxyHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * 仅用于展示与日志：把跃迁/关键词译成中文，不参与搜索。
 */
class KeywordTranslator(
    
) {
    suspend fun translateList(words: List<String>, settings: AppSettings): Map<String, String> {
        if (settings.translateProvider == TranslateProvider.Off || words.isEmpty()) {
            return emptyMap()
        }
        val key = settings.translateApiKey.trim()
        if (key.isEmpty() && settings.translateProvider != TranslateProvider.Google) {
            // 谷歌可走免费网页端备用；其它必须有 key
            return emptyMap()
        }
        return try {
            when (settings.translateProvider) {
                TranslateProvider.Off -> emptyMap()
                TranslateProvider.Google -> translateGoogle(words, key)
                TranslateProvider.Microsoft -> translateMicrosoft(words, key, settings.translateRegion)
                TranslateProvider.Tencent -> translateTencent(words, key, settings.translateSecret)
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun translateGoogle(words: List<String>, apiKey: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val out = linkedMapOf<String, String>()
            for (w in words.take(20)) {
                val q = URLEncoder.encode(w, "UTF-8")
                val url = if (apiKey.isNotBlank()) {
                    "https://translation.googleapis.com/language/translate/v2?key=$apiKey&q=$q&target=zh-CN&source=en"
                } else {
                    // 非官方网页接口，仅作展示备用
                    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=$q"
                }
                val req = Request.Builder().url(url).get().build()
                ProxyHttp.execute(req).use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string().orEmpty()
                    val zh = if (apiKey.isNotBlank()) {
                        JSONObject(body).optJSONObject("data")
                            ?.optJSONArray("translations")
                            ?.optJSONObject(0)
                            ?.optString("translatedText")
                    } else {
                        try {
                            JSONArray(body).optJSONArray(0)?.optJSONArray(0)?.optString(0)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (!zh.isNullOrBlank()) out[w] = zh
                }
            }
            out
        }

    private suspend fun translateMicrosoft(
        words: List<String>,
        key: String,
        region: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        words.take(20).forEach { arr.put(JSONObject().put("Text", it)) }
        val body = arr.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=zh-Hans")
            .addHeader("Ocp-Apim-Subscription-Key", key)
            .addHeader("Ocp-Apim-Subscription-Region", region.ifBlank { "global" })
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        ProxyHttp.execute(req).use { resp ->
            if (!resp.isSuccessful) return@withContext emptyMap()
            val root = JSONArray(resp.body?.string().orEmpty())
            val out = linkedMapOf<String, String>()
            for (i in 0 until minOf(root.length(), words.size)) {
                val zh = root.optJSONObject(i)
                    ?.optJSONArray("translations")
                    ?.optJSONObject(0)
                    ?.optString("text")
                if (!zh.isNullOrBlank()) out[words[i]] = zh
            }
            out
        }
    }

    private suspend fun translateTencent(
        words: List<String>,
        secretId: String,
        secretKey: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (secretKey.isBlank()) return@withContext emptyMap()
        val out = linkedMapOf<String, String>()
        // 简化：逐条调用 TextTranslate
        for (w in words.take(15)) {
            val payload = JSONObject()
                .put("SourceText", w)
                .put("Source", "auto")
                .put("Target", "zh")
                .put("ProjectId", 0)
                .toString()
            val ts = (System.currentTimeMillis() / 1000).toString()
            val date = java.time.Instant.ofEpochSecond(ts.toLong())
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .toString()
            val service = "tmt"
            val host = "tmt.tencentcloudapi.com"
            val algorithm = "TC3-HMAC-SHA256"
            val hashedPayload = sha256Hex(payload)
            val canonical = "POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:$host\n\ncontent-type;host\n$hashedPayload"
            val credentialScope = "$date/$service/tc3_request"
            val stringToSign = "$algorithm\n$ts\n$credentialScope\n${sha256Hex(canonical)}"
            val secretDate = hmac("TC3$secretKey".toByteArray(), date)
            val secretService = hmac(secretDate, service)
            val secretSigning = hmac(secretService, "tc3_request")
            val signature = hmacHex(secretSigning, stringToSign)
            val auth = "$algorithm Credential=$secretId/$credentialScope, SignedHeaders=content-type;host, Signature=$signature"
            val req = Request.Builder()
                .url("https://$host")
                .addHeader("Authorization", auth)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Host", host)
                .addHeader("X-TC-Action", "TextTranslate")
                .addHeader("X-TC-Timestamp", ts)
                .addHeader("X-TC-Version", "2018-03-21")
                .addHeader("X-TC-Region", "ap-guangzhou")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            ProxyHttp.execute(req).use { resp ->
                val body = resp.body?.string().orEmpty()
                val zh = try {
                    JSONObject(body).optJSONObject("Response")?.optString("TargetText")
                } catch (_: Exception) {
                    null
                }
                if (!zh.isNullOrBlank()) out[w] = zh
            }
        }
        out
    }

    private fun sha256Hex(s: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String =
        hmac(key, data).joinToString("") { "%02x".format(it) }
}
