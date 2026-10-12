package com.kers.killove.jhsy.util

import com.kers.killove.jhsy.data.remote.ProxyHttp
import com.kers.killove.jhsy.domain.AiFeature
import com.kers.killove.jhsy.domain.AiModelEntry
import com.kers.killove.jhsy.domain.AiRouter
import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.AiConfigCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 单次对话：支持多配置 / 模型组优先级回退。
 * 每次请求只带本轮 messages，不保存历史。
 */
object AiOneShot {

    private const val FAIL_COOLDOWN_MS = 5 * 60_000L

    suspend fun chat(
        settings: AppSettings,
        userContent: String,
        systemContent: String? = null,
        temperature: Double = 0.2,
        feature: AiFeature = AiFeature.GENERAL,
        onSettingsUpdate: (suspend (AppSettings) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        if (userContent.isBlank()) return@withContext null
        val candidates = AiRouter.candidates(settings, feature)
        if (candidates.isEmpty()) {
            // 最终回退：旧单字段
            return@withContext chatLegacy(settings, userContent, systemContent, temperature)
        }
        var working = settings
        for (ep in candidates) {
            val reply = chatOnce(
                key = ep.provider.apiKey.trim(),
                baseUrl = ep.provider.baseUrl,
                model = ep.model.modelName,
                userContent = userContent,
                systemContent = systemContent,
                temperature = temperature
            )
            if (!reply.isNullOrBlank()) return@withContext reply
            // 标记失败，冷却后可再试；写回模型组
            val now = System.currentTimeMillis()
            val updated = AiRouter.models(working).map { m ->
                if (m.id == ep.model.id) m.copy(failedUntil = now + FAIL_COOLDOWN_MS, updatedAt = now)
                else m
            }
            working = working.copy(aiModelsJson = AiConfigCodec.modelsToJson(updated))
            onSettingsUpdate?.invoke(working)
        }
        null
    }

    private suspend fun chatLegacy(
        settings: AppSettings,
        userContent: String,
        systemContent: String?,
        temperature: Double
    ): String? {
        val key = settings.translateAiApiKey.trim()
        if (key.isBlank()) return null
        val base = settings.translateAiBaseUrl.trim().trimEnd('/')
            .ifBlank { "https://api.openai.com/v1" }
        val model = settings.translateAiModel.trim().ifBlank { "gpt-4o-mini" }
        return chatOnce(key, base, model, userContent, systemContent, temperature)
    }

    suspend fun chatOnce(
        key: String,
        baseUrl: String,
        model: String,
        userContent: String,
        systemContent: String? = null,
        temperature: Double = 0.2
    ): String? = withContext(Dispatchers.IO) {
        if (key.isBlank() || model.isBlank() || userContent.isBlank()) return@withContext null
        val base = baseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
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

    /** 拉取 /v1/models 列表 */
    suspend fun listModels(apiKey: String, baseUrl: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank()) return@withContext Result.failure(IllegalArgumentException("API Key 为空"))
            val base = baseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
            val req = Request.Builder()
                .url("$base/models")
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()
            try {
                ProxyHttp.execute(req).use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${resp.code}")
                        )
                    }
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data")
                        ?: return@withContext Result.success(emptyList())
                    val names = buildList {
                        for (i in 0 until arr.length()) {
                            val id = arr.optJSONObject(i)?.optString("id").orEmpty().trim()
                            if (id.isNotEmpty()) add(id)
                        }
                    }.distinct().sorted()
                    Result.success(names)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
