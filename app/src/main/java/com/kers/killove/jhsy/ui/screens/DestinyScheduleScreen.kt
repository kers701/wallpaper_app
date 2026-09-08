package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.domain.DestinyMode
import com.kers.killove.jhsy.domain.DestinyRule
import com.kers.killove.jhsy.domain.Purity
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import com.kers.killove.jhsy.util.DestinyHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinyScheduleScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val textColor = LocalUiTextColor.current
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var rules by remember(settings.destinyRulesJson) {
        mutableStateOf(DestinyHelper.parseRules(settings.destinyRulesJson).toMutableList())
    }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DestinyRule?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<DestinyRule?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteName by remember { mutableStateOf("") }

    // 进入页面时清理已过期的年月日配置
    androidx.compose.runtime.LaunchedEffect(settings.destinyRulesJson) {
        val parsed = DestinyHelper.parseRules(settings.destinyRulesJson)
        val cleaned = DestinyHelper.removeExpiredDateRangeRules(parsed)
        if (cleaned.size != parsed.size) {
            rules = cleaned.toMutableList()
            vm.saveDestinyRules(cleaned)
            status = "已自动删除过期的年月日配置"
        }
    }

    // editor state
    var startH by remember { mutableIntStateOf(2) }
    var startM by remember { mutableIntStateOf(15) }
    var endH by remember { mutableIntStateOf(5) }
    var endM by remember { mutableIntStateOf(20) }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    var mode by remember { mutableStateOf(DestinyMode.Heartbeat) }
    var customSfw by remember { mutableStateOf(true) }
    var customSketchy by remember { mutableStateOf(true) }
    var customNsfw by remember { mutableStateOf(false) }
    var remainingUsesInput by remember { mutableStateOf("-1") }
    var priorityInput by remember { mutableStateOf("100") }
    var dateRangeOn by remember { mutableStateOf(false) }
    var startYear by remember { mutableIntStateOf(2026) }
    var startMonth by remember { mutableIntStateOf(1) }
    var startDay by remember { mutableIntStateOf(1) }
    var endYear by remember { mutableIntStateOf(2026) }
    var endMonth by remember { mutableIntStateOf(12) }
    var endDay by remember { mutableIntStateOf(31) }

    fun openEditor(existing: DestinyRule?) {
        editing = existing
        if (existing != null) {
            startH = existing.startMinutes / 60
            startM = existing.startMinutes % 60
            endH = existing.endMinutes / 60
            endM = existing.endMinutes % 60
            days = existing.weekdays.toSet()
            mode = existing.mode
            val p = existing.customPurity()
            customSfw = p.sfw
            customSketchy = p.sketchy
            customNsfw = p.nsfw
            nameInput = existing.name
            remainingUsesInput = existing.remainingUses.toString()
            priorityInput = existing.priority.toString()
            dateRangeOn = existing.dateRangeEnabled
            val sy = if (existing.startYmd > 0) existing.startYmd else {
                val c = java.util.Calendar.getInstance()
                c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH)
            }
            val ey = if (existing.endYmd > 0) existing.endYmd else sy
            startYear = sy / 10000; startMonth = (sy / 100) % 100; startDay = sy % 100
            endYear = ey / 10000; endMonth = (ey / 100) % 100; endDay = ey % 100
        } else {
            startH = 2; startM = 15; endH = 5; endM = 20
            days = setOf(1, 2, 5)
            mode = DestinyMode.Heartbeat
            customSfw = true; customSketchy = true; customNsfw = false
            nameInput = ""
            remainingUsesInput = "-1"
            priorityInput = "100"
            dateRangeOn = false
            val c = java.util.Calendar.getInstance()
            startYear = c.get(java.util.Calendar.YEAR)
            startMonth = c.get(java.util.Calendar.MONTH) + 1
            startDay = c.get(java.util.Calendar.DAY_OF_MONTH)
            endYear = startYear; endMonth = startMonth; endDay = startDay
        }
        showEditor = true
    }

    fun editorEndYmd(): Int {
        val a = startYear.coerceIn(1970, 9999) * 10000 + startMonth.coerceIn(1, 12) * 100 + startDay.coerceIn(1, 31)
        val b = endYear.coerceIn(1970, 9999) * 10000 + endMonth.coerceIn(1, 12) * 100 + endDay.coerceIn(1, 31)
        return maxOf(a, b)
    }

    fun editorDateRangeInvalid(): Boolean {
        if (!dateRangeOn) return false
        return editorEndYmd() < DestinyHelper.todayYmd()
    }

    fun buildRuleFromEditor(name: String): DestinyRule {
        val now = System.currentTimeMillis()
        val purity = Purity.fromFlags(customSfw, customSketchy, customNsfw) ?: Purity.SfwSketchy
        val base = editing
        val uses = remainingUsesInput.trim().toIntOrNull().let { v ->
            when {
                v == null -> -1
                v < 0 -> -1
                else -> v
            }
        }
        return DestinyRule(
            id = base?.id ?: UUID.randomUUID().toString(),
            name = name.ifBlank { "命运 ${fmt.format(Date(now))}" },
            enabled = base?.enabled ?: true,
            priority = priorityInput.trim().toIntOrNull()?.coerceIn(0, 999) ?: (base?.priority ?: 100),
            startMinutes = (startH.coerceIn(0, 23) * 60 + startM.coerceIn(0, 59)),
            endMinutes = (endH.coerceIn(0, 23) * 60 + endM.coerceIn(0, 59)),
            weekdays = days.toList().sorted(),
            mode = mode,
            customPurityCode = purity.code,
            createdAt = base?.createdAt ?: now,
            updatedAt = now,
            confidence = base?.confidence ?: 0,
            suppressedUntilEpoch = base?.suppressedUntilEpoch ?: 0L,
            remainingUses = uses,
            dateRangeEnabled = dateRangeOn,
            startYmd = startYear.coerceIn(1970, 9999) * 10000 +
                startMonth.coerceIn(1, 12) * 100 + startDay.coerceIn(1, 31),
            endYmd = endYear.coerceIn(1970, 9999) * 10000 +
                endMonth.coerceIn(1, 12) * 100 + endDay.coerceIn(1, 31)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("命运先机配置", style = MaterialTheme.typography.titleLarge, color = textColor)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(
            "按星期与时段自动劫持纯度。冲突判定：①优先级越小越优先 ②同优先级置信度越大越优先 ③再比最近修改。通知栏切换模式时若已劫持，会询问是否强制切换（是：本时段跳过且置信度-1；否：继续劫持且置信度+1）。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
        val active = DestinyHelper.resolveActive(settings)
        Text(
            if (active != null) "当前命中：${active.name}（${active.mode.label}）"
            else "当前未命中任何已启用规则",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )

        Button(onClick = { openEditor(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("添加命运先机配置")
        }

        rules.forEachIndexed { index, rule ->
            GlassCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rule.enabled,
                            onCheckedChange = { checked ->
                                rules = rules.toMutableList().also {
                                    it[index] = rule.copy(enabled = checked, updatedAt = System.currentTimeMillis())
                                }
                            }
                        )
                        Text(rule.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "优先级 ${rule.priority}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "置信度 ${rule.confidence}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "${rule.startLabel()}–${rule.endLabel()} · 周${rule.weekdays.joinToString("、") { DestinyHelper.weekdayLabel(it) }} · ${rule.mode.label}" +
                            if (rule.mode == DestinyMode.Custom) "（${rule.customPurity().label}）" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (rule.dateRangeEnabled) {
                        Text(
                            "日期：${rule.dateRangeLabel()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "生效次数：" + if (rule.remainingUses < 0) "无限" else "${rule.remainingUses}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "创建：${fmt.format(Date(rule.createdAt))} · 修改：${fmt.format(Date(rule.updatedAt))}" +
                            if (rule.isSuppressed()) " · 本时段已强制跳过" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { openEditor(rule) }) { Text("编辑") }
                        OutlinedButton(onClick = {
                            val next = rules.toMutableList().also {
                                it[index] = rule.copy(
                                    confidence = 0,
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            rules = next
                            vm.saveDestinyRules(next)
                            status = "已重置置信度：${rule.name}"
                        }) { Text("置信度重置") }
                        if (rule.isSuppressed()) {
                            OutlinedButton(onClick = {
                                val next = rules.toMutableList().also {
                                    it[index] = rule.copy(
                                        suppressedUntilEpoch = 0L,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                                rules = next
                                vm.saveDestinyRules(next)
                                status = "已恢复命中：${rule.name}（本时段可再次生效）"
                            }) { Text("恢复") }
                        }
                        OutlinedButton(onClick = {
                            pendingDeleteIndex = index
                            pendingDeleteName = rule.name
                        }) { Text("删除") }
                    }
                }
            }
        }

        Button(
            onClick = {
                vm.saveDestinyRules(rules)
                status = "已保存全部命运先机配置"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存全部命运先机修改")
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }

    
    if (pendingDeleteIndex != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            title = { Text("删除命运先机配置？") },
            text = { Text("确定删除「$pendingDeleteName」吗？删除后需再点保存才会写入本地。") },
            confirmButton = {
                TextButton(onClick = {
                    val idx = pendingDeleteIndex
                    if (idx != null && idx in rules.indices) {
                        rules = rules.toMutableList().also { it.removeAt(idx) }
                    }
                    pendingDeleteIndex = null
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIndex = null }) { Text("取消") }
            }
        )
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editing == null) "添加命运先机配置" else "编辑配置") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("配置名称") },
                        placeholder = { Text("可自定义名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("开始时间（24 小时）")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startH.toString(),
                            onValueChange = { startH = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                            label = { Text("时") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startM.toString(),
                            onValueChange = { startM = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                            label = { Text("分") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text("结束时间（24 小时）")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = endH.toString(),
                            onValueChange = { endH = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                            label = { Text("时") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endM.toString(),
                            onValueChange = { endM = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                            label = { Text("分") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text("生效星期（周一～周日）")
                    // 两行排布，避免窄屏挤掉「日」
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(1, 2, 3, 4).forEach { d ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(DestinyHelper.weekdayLabel(d), style = MaterialTheme.typography.bodySmall)
                                Checkbox(
                                    checked = d in days,
                                    onCheckedChange = {
                                        days = if (it) days + d else days - d
                                    }
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(5, 6, 7).forEach { d ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(DestinyHelper.weekdayLabel(d), style = MaterialTheme.typography.bodySmall)
                                Checkbox(
                                    checked = d in days,
                                    onCheckedChange = {
                                        days = if (it) days + d else days - d
                                    }
                                )
                            }
                        }
                        // 占位对齐，使五六日与上行等宽感
                        Spacer(Modifier.weight(1f))
                    }
                    Text("先机模式")
                    var modeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = it }) {
                        OutlinedTextField(
                            value = mode.label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                            DestinyMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label) },
                                    onClick = {
                                        mode = m
                                        modeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用年月日范围", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = dateRangeOn, onCheckedChange = { dateRangeOn = it })
                    }
                    if (dateRangeOn) {
                        Text("开始日期", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = startYear.toString(),
                                onValueChange = { startYear = it.filter { c -> c.isDigit() }.take(4).toIntOrNull() ?: startYear },
                                label = { Text("年") },
                                modifier = Modifier.weight(1.2f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = startMonth.toString(),
                                onValueChange = { startMonth = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(1, 12) ?: 1 },
                                label = { Text("月") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = startDay.toString(),
                                onValueChange = { startDay = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(1, 31) ?: 1 },
                                label = { Text("日") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Text("结束日期", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = endYear.toString(),
                                onValueChange = { endYear = it.filter { c -> c.isDigit() }.take(4).toIntOrNull() ?: endYear },
                                label = { Text("年") },
                                modifier = Modifier.weight(1.2f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = endMonth.toString(),
                                onValueChange = { endMonth = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(1, 12) ?: 1 },
                                label = { Text("月") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = endDay.toString(),
                                onValueChange = { endDay = it.filter { c -> c.isDigit() }.take(2).toIntOrNull()?.coerceIn(1, 31) ?: 1 },
                                label = { Text("日") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Text(
                            "仅在该日期闭区间内，再按星期与时间生效；关闭开关则忽略年月日。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedTextField(
                        value = priorityInput,
                        onValueChange = { v ->
                            priorityInput = v.filter { it.isDigit() }.take(3)
                        },
                        label = { Text("优先级（0～999，数字越小越优先）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remainingUsesInput,
                        onValueChange = { v ->
                            remainingUsesInput = v.filter { it.isDigit() || it == '-' }.take(6)
                        },
                        label = { Text("生效次数（-1=无限）") },
                        supportingText = { Text("每完成一个时段减 1；到 0 自动删除；-1 不衰减") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == DestinyMode.Custom) {
                        Text("自定义纯度")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = customSfw, onCheckedChange = { customSfw = it })
                            Text("保守级", style = MaterialTheme.typography.bodySmall)
                            Checkbox(checked = customSketchy, onCheckedChange = { customSketchy = it })
                            Text("模糊级", style = MaterialTheme.typography.bodySmall)
                            Checkbox(checked = customNsfw, onCheckedChange = { customNsfw = it })
                            Text("限制级", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (days.isEmpty()) {
                        status = "请至少选择一个星期"
                        return@TextButton
                    }
                    if (editorDateRangeInvalid()) {
                        status = "结束日期不能早于今天，无法保存"
                        return@TextButton
                    }
                    val finalName = nameInput.trim().ifBlank {
                        "命运 ${fmt.format(Date(System.currentTimeMillis()))}"
                    }
                    nameInput = finalName
                    val r = buildRuleFromEditor(finalName)
                    rules = rules.toMutableList().also { list ->
                        if (editing == null) {
                            list.add(r)
                        } else {
                            val i = list.indexOfFirst { it.id == r.id }
                            if (i >= 0) list[i] = r else list.add(r)
                        }
                    }
                    showEditor = false
                    showNameDialog = false
                    pendingSave = null
                    status = if (editing == null) "已添加：$finalName" else "已更新：$finalName"
                }) { Text("保存配置") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("取消") }
            }
        )
    }

    if (showNameDialog && pendingSave != null) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("配置名称") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("自定义名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editorDateRangeInvalid()) {
                        status = "结束日期不能早于今天，无法保存"
                        return@TextButton
                    }
                    val r = pendingSave!!.copy(
                        name = nameInput.ifBlank { pendingSave!!.name },
                        updatedAt = System.currentTimeMillis()
                    )
                    rules = rules.toMutableList().also { it.add(r) }
                    showNameDialog = false
                    showEditor = false
                    pendingSave = null
                }) { Text("确认保存") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("取消") }
            }
        )
    }
}
