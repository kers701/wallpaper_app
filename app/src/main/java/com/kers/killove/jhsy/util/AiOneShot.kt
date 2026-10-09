package com.kers.killove.jhsy.util

import com.kers.killove.jhsy.data.remote.ProxyHttp
import com.kers.killove.jhsy.domain.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 单次对话调用：每次请求只带本轮 user（可选 system），**不保存、不追加任何历史**。
 * 禁止跨请求复用 messages，避免上下文膨胀耗 tokens。
 */
object AiOneShot {

    suspend fun chat(
        settings: AppSettings,
        userContent: String,
        systemContent: String? = null,
        temperature: Double = 0.2
    ): String? = withContext(Dispatchers.IO) {
        val key = settings.translateAiApiKey.trim()
        if (key.isBlank() || userContent.isBlank()) return@withContext null
        val base = settings.translateAiBaseUrl.trim().trimEnd('/')
            .ifBlank { "https://api.openai.com/v1" }
        val model = settings.translateAiModel.trim().ifBlank { "gpt-4o-mini" }

        // 全新 messages，绝不复用上一轮
        val messages = JSONArray()
        if (!systemContent.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemContent))
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", temperature)

        val req = Request.Builder()
            .url("$base/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            // 明确无会话：不传 conversation / previous_response_id 等字段
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            ProxyHttp.execute(req).use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty())
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    ?.ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }
}
