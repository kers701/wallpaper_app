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
                            confidence = o.optInt("confidence", 0),
                            startMinutes = o.optInt("startMinutes", 0).coerceIn(0, 1439),
                            endMinutes = o.optInt("endMinutes", 60).coerceIn(0, 1439),
                            weekdays = wd.map { it.coerceIn(1, 7) }.distinct().sorted(),
                            mode = DestinyMode.fromCode(o.optString("mode", "user")),
                            customPurityCode = o.optString("customPurityCode", "110"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                            suppressedUntilEpoch = o.optLong("suppressedUntilEpoch", 0L),
                            remainingUses = o.optInt("remainingUses", -1),
                            dateRangeEnabled = o.optBoolean("dateRangeEnabled", false),
                            startYmd = o.optInt("startYmd", 0),
                            endYmd = o.optInt("endYmd", 0),
                            weekdaysEnabled = o.optBoolean("weekdaysEnabled", true),
                            timeEnabled = o.optBoolean("timeEnabled", true)
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
            o.put("confidence", r.confidence)
            o.put("startMinutes", r.startMinutes.coerceIn(0, 1439))
            o.put("endMinutes", r.endMinutes.coerceIn(0, 1439))
            val days = JSONArray()
            r.weekdays.forEach { days.put(it) }
            o.put("weekdays", days)
            o.put("mode", r.mode.code)
            o.put("customPurityCode", r.customPurityCode)
            o.put("createdAt", r.createdAt)
            o.put("updatedAt", r.updatedAt)
            o.put("suppressedUntilEpoch", r.suppressedUntilEpoch)
            o.put("remainingUses", r.remainingUses)
            o.put("dateRangeEnabled", r.dateRangeEnabled)
            o.put("startYmd", r.startYmd)
            o.put("endYmd", r.endYmd)
            o.put("weekdaysEnabled", r.weekdaysEnabled)
            o.put("timeEnabled", r.timeEnabled)
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

    /** 今日 YYYYMMDD */
    fun todayYmd(cal: Calendar = Calendar.getInstance()): Int {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return y * 10000 + m * 100 + d
    }

    fun inDateRange(rule: DestinyRule, cal: Calendar = Calendar.getInstance()): Boolean {
        if (!rule.dateRangeEnabled) return true
        var a = rule.startYmd
        var b = rule.endYmd
        if (a <= 0 && b <= 0) return true
        if (a > 0 && b > 0 && a > b) {
            val tmp = a; a = b; b = tmp
        }
        val today = todayYmd(cal)
        if (a > 0 && today < a) return false
        if (b > 0 && today > b) return false
        return true
    }

    /** 开启年月日且今天已晚于结束日（闭区间外）→ 应删除，与生效次数无关 */
    fun isDateRangeExpired(rule: DestinyRule, cal: Calendar = Calendar.getInstance()): Boolean {
        if (!rule.dateRangeEnabled) return false
        var a = rule.startYmd
        var b = rule.endYmd
        if (a <= 0 && b <= 0) return false
        if (a > 0 && b > 0 && a > b) {
            val tmp = a; a = b; b = tmp
        }
        val end = if (b > 0) b else a
        if (end <= 0) return false
        return todayYmd(cal) > end
    }

    /** 剔除已过期的「启用年月日」规则（忽略 remainingUses） */
    fun removeExpiredDateRangeRules(
        rules: List<DestinyRule>,
        cal: Calendar = Calendar.getInstance()
    ): List<DestinyRule> = rules.filterNot { isDateRangeExpired(it, cal) }


    /**
     * 匹配顺序：日期 → 星期 → 时间。
     * 三维全关 → 无效（永不命中）。
     * 关时间 → 全天；关星期 → 不限星期；关日期 → 不限年月日。
     */
    fun inScheduledWindow(rule: DestinyRule, cal: Calendar = Calendar.getInstance()): Boolean {
        val useDate = rule.dateRangeEnabled
        val useWeek = rule.weekdaysEnabled
        val useTime = rule.timeEnabled
        if (!useDate && !useWeek && !useTime) return false

        if (useDate && !inDateRange(rule, cal)) return false
        if (useWeek) {
            if (rule.weekdays.isEmpty()) return false
            if (isoWeekday(cal) !in rule.weekdays) return false
        }
        if (useTime) {
            val now = minutesOfDay(cal)
            val s = rule.startMinutes.coerceIn(0, 1439)
            val e = rule.endMinutes.coerceIn(0, 1439)
            if (s <= e) {
                if (now !in s..e) return false
            } else {
                if (!(now >= s || now <= e)) return false
            }
        }
        return true
    }

    fun matches(rule: DestinyRule, cal: Calendar = Calendar.getInstance()): Boolean {
        if (!rule.enabled) return false
        if (rule.isSuppressed(cal.timeInMillis)) return false
        return inScheduledWindow(rule, cal)
    }

    /**
     * 当前命中时段的结束时刻（epoch ms）。用于强制切换后压制到本段结束。
     * 非跨午夜：今天 endMinutes；跨午夜且已过 0 点处于后半段：今天 end；跨午夜前半段：明天 end。
     */
    fun currentWindowEndEpoch(rule: DestinyRule, cal: Calendar = Calendar.getInstance()): Long {
        val s = rule.startMinutes.coerceIn(0, 1439)
        val e = rule.endMinutes.coerceIn(0, 1439)
        val nowMin = minutesOfDay(cal)
        val endCal = cal.clone() as Calendar
        endCal.set(Calendar.SECOND, 0)
        endCal.set(Calendar.MILLISECOND, 0)
        if (s <= e) {
            endCal.set(Calendar.HOUR_OF_DAY, e / 60)
            endCal.set(Calendar.MINUTE, e % 60)
            // 若刚好在结束点，压制到下一分钟，避免边界抖动
            if (endCal.timeInMillis <= cal.timeInMillis) {
                endCal.add(Calendar.MINUTE, 1)
            }
        } else {
            // 跨午夜
            if (nowMin >= s) {
                // 还在开始日一侧 → 结束在明天
                endCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            endCal.set(Calendar.HOUR_OF_DAY, e / 60)
            endCal.set(Calendar.MINUTE, e % 60)
            if (endCal.timeInMillis <= cal.timeInMillis) {
                endCal.add(Calendar.MINUTE, 1)
            }
        }
        return endCal.timeInMillis
    }

    /**
     * 命中多条时：① priority 最小 ② confidence 最大 ③ updatedAt 最新（再比 createdAt）。
     */
    fun resolveActive(settings: AppSettings, cal: Calendar = Calendar.getInstance()): DestinyRule? {
        if (!settings.destinyEnabled) return null
        return pickWinner(parseRules(settings.destinyRulesJson), cal)
    }

    /**
     * 服务进程优先用桥接文件（与 DataStore 可能不同步）。
     */
    fun resolveActive(context: android.content.Context, cal: Calendar = Calendar.getInstance()): DestinyRule? {
        if (!ProcessBridgePrefs.destinyEnabled(context)) return null
        return pickWinner(parseRules(ProcessBridgePrefs.destinyRulesJson(context)), cal)
    }

    /**
     * 首页/概览展示用：当前实际生效纯度（命运/绿色/健康/心跳接管优先于用户设定）。
     */
    fun effectiveDisplayPurity(
        settings: AppSettings,
        purityMode: String = ProcessBridgePrefs.MODE_NORMAL,
        greenActive: Boolean = false
    ): Purity {
        resolveActive(settings)?.let { rule ->
            return when (rule.mode) {
                DestinyMode.Health -> Purity.SketchyOnly
                DestinyMode.Heartbeat -> Purity.SketchyNsfw
                DestinyMode.Custom -> rule.customPurity()
                DestinyMode.User -> settings.purity
            }
        }
        if (greenActive) return Purity.SfwSketchy
        return when (purityMode) {
            ProcessBridgePrefs.MODE_HEALTH -> Purity.SketchyOnly
            ProcessBridgePrefs.MODE_HEARTBEAT -> Purity.SketchyNsfw
            else -> settings.purity
        }
    }

    private fun pickWinner(rules: List<DestinyRule>, cal: Calendar): DestinyRule? {
        val hit = rules.filter { matches(it, cal) }
        if (hit.isEmpty()) return null
        val minP = hit.minOf { it.priority }
        val byPri = hit.filter { it.priority == minP }
        val maxC = byPri.maxOf { it.confidence }
        val byConf = byPri.filter { it.confidence == maxC }
        return byConf.maxWithOrNull(
            compareBy<DestinyRule> { it.updatedAt }.thenBy { it.createdAt }
        )
    }

    /**
     * 检测各规则「计划时段」由内→外，完成一次周期：
     * remainingUses>0 则减 1；减到 0 删除；-1 不衰减。
     * @return 更新后的规则列表 + 新的 in-window 状态表
     */
    fun applyPeriodEndDecay(
        rules: List<DestinyRule>,
        prevInWindow: Map<String, Boolean>,
        cal: Calendar = Calendar.getInstance()
    ): Pair<List<DestinyRule>, Map<String, Boolean>> {
        val nextIn = linkedMapOf<String, Boolean>()
        val out = mutableListOf<DestinyRule>()
        for (r in rules) {
            // 年月日过期直接丢弃（不走生效次数）
            if (isDateRangeExpired(r, cal)) continue
            val nowIn = r.enabled && inScheduledWindow(r, cal)
            val wasIn = prevInWindow[r.id] == true
            nextIn[r.id] = nowIn
            if (r.remainingUses == 0) {
                // 已耗尽，移除
                continue
            }
            if (wasIn && !nowIn && r.remainingUses > 0) {
                val left = r.remainingUses - 1
                if (left <= 0) continue
                out += r.copy(remainingUses = left, updatedAt = System.currentTimeMillis())
            } else {
                out += r
            }
        }
        // 清理已不存在 id 的状态由调用方覆盖写入 nextIn（仅保留仍存在的）
        val alive = out.map { it.id }.toSet()
        val cleaned = nextIn.filterKeys { it in alive }
        return out to cleaned
    }

    /** 强制跳过当前时段：压制到本段结束 + 置信度 -1 */
    fun forceSkipCurrentWindow(rule: DestinyRule, cal: Calendar = Calendar.getInstance()): DestinyRule {
        val end = currentWindowEndEpoch(rule, cal)
        return rule.copy(
            confidence = rule.confidence - 1,
            suppressedUntilEpoch = end,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 拒绝强制切换：置信度 +1 */
    fun reinforceConfidence(rule: DestinyRule): DestinyRule {
        return rule.copy(
            confidence = rule.confidence + 1,
            updatedAt = System.currentTimeMillis()
        )
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

    /** 配置名最多显示 3 个字符，超出加 … */
    fun shortName(name: String, maxChars: Int = 3): String {
        val n = name.trim()
        if (n.length <= maxChars) return n.ifEmpty { "未命名" }
        return n.take(maxChars) + "…"
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
