package com.kers.killove.jhsy.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** AI 生效项目 */
enum class AiFeature(val code: String, val label: String) {
    PAST_LIFE("past_life", "前世今生"),
    NATURAL_LAW("natural_law", "自然法则"),
    TRANSLATE("translate", "智能翻译"),
    GENERAL("general", "通用/检测");

    companion object {
        fun fromCode(c: String): AiFeature? = entries.find { it.code == c }
        fun allCodes(): Set<String> = entries.map { it.code }.toSet()
    }
}

/** 提供商配置（密钥 + URL + 可选模型名列表） */
data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    /** 多行模型名，一行一个；拉取后也会写入模型组 */
    val modelNamesText: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun modelNames(): List<String> =
        modelNamesText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
            .toList()
}

/** 模型组中的单条模型 */
data class AiModelEntry(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String = "",
    val modelName: String = "",
    /** 自定义标签 */
    val tag: String = "",
    /** 0–100，数字越小越优先；冲突时先比优先级再比更新时间 */
    val priority: Int = 50,
    /** 生效项目 code 集合 */
    val features: Set<String> = AiFeature.allCodes(),
    val enabled: Boolean = true,
    /** 失败冷却截止时间戳，期间跳过 */
    val failedUntil: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun covers(feature: AiFeature): Boolean = features.contains(feature.code)
    fun isUsable(now: Long = System.currentTimeMillis()): Boolean =
        enabled && modelName.isNotBlank() && failedUntil <= now
}

object AiConfigCodec {
    fun providersToJson(list: List<AiProviderConfig>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("apiKey", p.apiKey)
                    .put("baseUrl", p.baseUrl)
                    .put("modelNamesText", p.modelNamesText)
                    .put("enabled", p.enabled)
                    .put("createdAt", p.createdAt)
                    .put("updatedAt", p.updatedAt)
            )
        }
        return arr.toString()
    }

    fun providersFromJson(json: String?): List<AiProviderConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        AiProviderConfig(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = o.optString("name"),
                            apiKey = o.optString("apiKey"),
                            baseUrl = o.optString("baseUrl").ifBlank { "https://api.openai.com/v1" },
                            modelNamesText = o.optString("modelNamesText"),
                            enabled = o.optBoolean("enabled", true),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun modelsToJson(list: List<AiModelEntry>): String {
        val arr = JSONArray()
        list.forEach { m ->
            val feats = JSONArray()
            m.features.forEach { feats.put(it) }
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("providerId", m.providerId)
                    .put("modelName", m.modelName)
                    .put("tag", m.tag)
                    .put("priority", m.priority.coerceIn(0, 100))
                    .put("features", feats)
                    .put("enabled", m.enabled)
                    .put("failedUntil", m.failedUntil)
                    .put("updatedAt", m.updatedAt)
            )
        }
        return arr.toString()
    }

    fun modelsFromJson(json: String?): List<AiModelEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val featsArr = o.optJSONArray("features")
                    val feats = if (featsArr != null && featsArr.length() > 0) {
                        buildSet {
                            for (j in 0 until featsArr.length()) {
                                val c = featsArr.optString(j)
                                if (c.isNotBlank()) add(c)
                            }
                        }
                    } else AiFeature.allCodes()
                    add(
                        AiModelEntry(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            providerId = o.optString("providerId"),
                            modelName = o.optString("modelName"),
                            tag = o.optString("tag"),
                            priority = o.optInt("priority", 50).coerceIn(0, 100),
                            features = feats,
                            enabled = o.optBoolean("enabled", true),
                            failedUntil = o.optLong("failedUntil", 0L),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * 解析可用调用端点：按优先级选模型，失败可回退。
 */
object AiRouter {
    data class Endpoint(
        val provider: AiProviderConfig,
        val model: AiModelEntry
    )

    fun providers(s: AppSettings): List<AiProviderConfig> {
        val list = AiConfigCodec.providersFromJson(s.aiProvidersJson)
        if (list.isNotEmpty()) return list
        // 兼容旧单配置
        if (s.translateAiApiKey.isNotBlank()) {
            return listOf(
                AiProviderConfig(
                    id = "legacy",
                    name = "默认",
                    apiKey = s.translateAiApiKey,
                    baseUrl = s.translateAiBaseUrl.ifBlank { "https://api.openai.com/v1" },
                    modelNamesText = s.translateAiModel.ifBlank { "gpt-4o-mini" },
                    enabled = true
                )
            )
        }
        return emptyList()
    }

    fun models(s: AppSettings): List<AiModelEntry> {
        val list = AiConfigCodec.modelsFromJson(s.aiModelsJson)
        if (list.isNotEmpty()) return list
        val p = providers(s).firstOrNull() ?: return emptyList()
        val name = s.translateAiModel.ifBlank { p.modelNames().firstOrNull().orEmpty() }
        if (name.isBlank()) return emptyList()
        return listOf(
            AiModelEntry(
                id = "legacy-model",
                providerId = p.id,
                modelName = name,
                tag = "默认",
                priority = 0,
                features = AiFeature.allCodes(),
                enabled = true
            )
        )
    }

    fun hasAnyConfigured(s: AppSettings): Boolean {
        val ps = providers(s)
        if (ps.any { it.enabled && it.apiKey.isNotBlank() }) {
            val ms = models(s)
            if (ms.any { it.enabled && it.modelName.isNotBlank() }) return true
            if (ps.any { it.modelNames().isNotEmpty() }) return true
        }
        return s.translateAiApiKey.isNotBlank() && s.translateAiModel.isNotBlank()
    }

    /** 按优先级列出可用于某功能的端点 */
    fun candidates(s: AppSettings, feature: AiFeature): List<Endpoint> {
        val now = System.currentTimeMillis()
        val providerMap = providers(s).filter { it.enabled && it.apiKey.isNotBlank() }.associateBy { it.id }
        return models(s)
            .filter { it.isUsable(now) && it.covers(feature) }
            .mapNotNull { m ->
                val p = providerMap[m.providerId] ?: providerMap.values.firstOrNull()
                if (p == null || !p.enabled || p.apiKey.isBlank()) null else Endpoint(p, m)
            }
            .sortedWith(
                compareBy<Endpoint> { it.model.priority }
                    .thenByDescending { it.model.updatedAt }
            )
    }
}
