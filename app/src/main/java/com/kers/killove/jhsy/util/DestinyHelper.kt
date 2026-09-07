package com.kers.killove.jhsy.util

import com.kers.killove.jhsy.domain.AppSettings
import com.kers.killove.jhsy.domain.DestinyMode
import com.kers.killove.jhsy.domain.DestinyRule
import com.kers.killove.jhsy.domain.Purity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object DestinyHelper {

    fun parseRules(json: String): List<DestinyRule> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val days = o.optJSONArray("weekdays")
                    val wd = buildList {
                        if (days != null) {
                            for (j in 0 until days.length()) add(days.optInt(j))
                        }
                    }.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }
                    add(
                        DestinyRule(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            name = o.optString("name", "未命名"),
                            enabled = o.optBoolean("enabled", true),
                            priority = o.optInt("priority", 100).coerceIn(0, 999),
                            startMinutes = o.optInt("startMinutes", 0).coerceIn(0, 1439),
                            endMinutes = o.optInt("endMinutes", 60).coerceIn(0, 1439),
                            weekdays = wd.map { it.coerceIn(1, 7) }.distinct().sorted(),
                            mode = DestinyMode.fromCode(o.optString("mode", "user")),
                            customPurityCode = o.optString("customPurityCode", "110"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun toJson(rules: List<DestinyRule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("enabled", r.enabled)
            o.put("priority", r.priority.coerceIn(0, 999))
            o.put("startMinutes", r.startMinutes.coerceIn(0, 1439))
            o.put("endMinutes", r.endMinutes.coerceIn(0, 1439))
            val days = JSONArray()
            r.weekdays.forEach { days.put(it) }
            o.put("weekdays", days)
            o.put("mode", r.mode.code)
            o.put("customPurityCode", r.customPurityCode)
            o.put("createdAt", r.createdAt)
            o.put("updatedAt", r.updatedAt)
            arr.put(o)
        }
        return arr.toString()
    }

    /** Calendar: Monday=2 in US, we use ISO 1=Mon … 7=Sun */
    fun isoWeekday(cal: Calendar = Calendar.getInstance()): Int {
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    fun minutesOfDay(cal: Calendar = Calendar.getInstance()): Int =
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    fun matches(rule: DestinyRule, cal: Calendar = Calendar.getInstance()): Boolean {
        if (!rule.enabled) return false
        if (isoWeekday(cal) !in rule.weekdays) return false
        val now = minutesOfDay(cal)
        val s = rule.startMinutes.coerceIn(0, 1439)
        val e = rule.endMinutes.coerceIn(0, 1439)
        return if (s <= e) {
            // 闭区间 [start, end]，如 2:15–5:20
            now in s..e
        } else {
            // 跨午夜：例如 22:00–05:00
            now >= s || now <= e
        }
    }

    /**
     * 命中多条时：priority 最小优先；同 priority 取 updatedAt 最新。
     */
    fun resolveActive(settings: AppSettings, cal: Calendar = Calendar.getInstance()): DestinyRule? {
        if (!settings.destinyEnabled) return null
        val rules = parseRules(settings.destinyRulesJson)
        val hit = rules.filter { matches(it, cal) }
        if (hit.isEmpty()) return null
        val minP = hit.minOf { it.priority }
        return hit.filter { it.priority == minP }.maxByOrNull { it.updatedAt }
    }

    fun applyToSettings(settings: AppSettings, rule: DestinyRule): AppSettings {
        return when (rule.mode) {
            DestinyMode.Health ->
                settings.copy(purity = Purity.SketchyOnly, purityFilterEnabled = true)
            DestinyMode.Heartbeat ->
                settings.copy(purity = Purity.SketchyNsfw, purityFilterEnabled = true)
            DestinyMode.User -> {
                if (!settings.purityFilterEnabled) {
                    settings.copy(purity = Purity.randomAny())
                } else settings
            }
            DestinyMode.Custom ->
                settings.copy(
                    purity = Purity.fromCode(rule.customPurityCode),
                    purityFilterEnabled = true
                )
        }
    }

    fun weekdayLabel(d: Int): String = when (d) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> "?"
    }
}
