package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel
import com.kers.killove.jhsy.util.GitSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val textColor = LocalUiTextColor.current
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    var user by remember(settings.gitUserName) { mutableStateOf(settings.gitUserName) }
    var email by remember(settings.gitUserEmail) { mutableStateOf(settings.gitUserEmail) }
    var token by remember(settings.gitToken) { mutableStateOf(settings.gitToken) }
    var repo by remember(settings.gitRepo) { mutableStateOf(settings.gitRepo) }
    var branch by remember(settings.gitBranch) { mutableStateOf(settings.gitBranch.ifBlank { "main" }) }
    var interval by remember(settings.gitUploadIntervalMinutes) {
        mutableIntStateOf(settings.gitUploadIntervalMinutes)
    }
    var showToken by remember { mutableStateOf(false) }

    var syncBasic by remember(settings.gitSyncBasic) { mutableStateOf(settings.gitSyncBasic) }
    var syncKeys by remember(settings.gitSyncApiKeys) { mutableStateOf(settings.gitSyncApiKeys) }
    var syncKw by remember(settings.gitSyncKeywords) { mutableStateOf(settings.gitSyncKeywords) }
    var syncJump by remember(settings.gitSyncJump) { mutableStateOf(settings.gitSyncJump) }
    var syncDest by remember(settings.gitSyncDestiny) { mutableStateOf(settings.gitSyncDestiny) }
    var syncBlack by remember(settings.gitSyncBlacklist) { mutableStateOf(settings.gitSyncBlacklist) }
    var syncLoc by remember(settings.gitSyncLocation) { mutableStateOf(settings.gitSyncLocation) }
    var syncProxy by remember(settings.gitSyncProxy) { mutableStateOf(settings.gitSyncProxy) }
    var syncTrans by remember(settings.gitSyncTranslate) { mutableStateOf(settings.gitSyncTranslate) }
    var syncUi by remember(settings.gitSyncUi) { mutableStateOf(settings.gitSyncUi) }
    var enabled by remember(settings.gitSyncEnabled) { mutableStateOf(settings.gitSyncEnabled) }

    fun persist(extra: (com.kers.killove.jhsy.domain.AppSettings) -> com.kers.killove.jhsy.domain.AppSettings = { it }) {
        val base = settings.copy(
            gitSyncEnabled = enabled,
            gitUserName = user.trim(),
            gitUserEmail = email.trim(),
            gitToken = token.trim(),
            gitRepo = repo.trim(),
            gitBranch = branch.trim().ifBlank { "main" },
            gitUploadIntervalMinutes = interval.coerceIn(0, 24 * 60),
            gitSyncBasic = syncBasic,
            gitSyncApiKeys = syncKeys,
            gitSyncKeywords = syncKw,
            gitSyncJump = syncJump,
            gitSyncDestiny = syncDest,
            gitSyncBlacklist = syncBlack,
            gitSyncLocation = syncLoc,
            gitSyncProxy = syncProxy,
            gitSyncTranslate = syncTrans,
            gitSyncUi = syncUi
        )
        vm.saveSettings(extra(base))
    }

    @Composable
    fun ScopeRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = textColor, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = {
                onChange(it)
            })
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("云同步", style = MaterialTheme.typography.headlineSmall, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "通过 GitHub 私有仓库同步配置。需填写用户名、邮箱、Personal Access Token（需 repo 权限）与仓库。\n" +
                "远程路径固定：${GitSync.REMOTE_PATH}\n" +
                "PIN 永不上传。开启「密钥备份」才会同步 API 密钥等敏感字段。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.8f)
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用云同步", color = textColor)
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Git 用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Git 邮箱") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Git 令牌 (PAT)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "隐藏" else "显示")
                }
            }
        )
        OutlinedTextField(
            value = repo, onValueChange = { repo = it },
            label = { Text("仓库 (owner/repo 或 GitHub URL)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = branch, onValueChange = { branch = it },
            label = { Text("分支") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = if (interval == 0) "0" else interval.toString(),
            onValueChange = { v ->
                interval = v.filter { it.isDigit() }.take(4).toIntOrNull()?.coerceIn(0, 24 * 60) ?: 0
            },
            label = { Text("自动上传间隔（分钟，0=仅手动）") },
            supportingText = { Text("建议 ≥15；系统最短周期可能约 15 分钟") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Text("备份内容选择", style = MaterialTheme.typography.titleMedium, color = textColor)
        ScopeRow("基础设置（调度/纯度/分辨率等）", syncBasic) { syncBasic = it }
        ScopeRow("密钥备份（API Key 等）", syncKeys) { syncKeys = it }
        ScopeRow("关键词", syncKw) { syncKw = it }
        ScopeRow("跃迁 / 湮灭 / 虚妄", syncJump) { syncJump = it }
        ScopeRow("命运先机", syncDest) { syncDest = it }
        ScopeRow("应用黑名单", syncBlack) { syncBlack = it }
        ScopeRow("定位避让", syncLoc) { syncLoc = it }
        ScopeRow("代理 / 加速 / 超级代理（不含本机路径）", syncProxy) { syncProxy = it }
        ScopeRow("翻译密钥", syncTrans) { syncTrans = it }
        ScopeRow("界面美化", syncUi) { syncUi = it }

        Button(
            onClick = {
                persist()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) { Text("保存云同步配置") }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    persist()
                    vm.gitSyncUpload()
                },
                modifier = Modifier.weight(1f),
                enabled = !busy
            ) { Text("立即上传") }
            OutlinedButton(
                onClick = {
                    persist()
                    vm.gitSyncDownload()
                },
                modifier = Modifier.weight(1f),
                enabled = !busy
            ) { Text("从 Git 同步到本地") }
        }

        val up = settings.gitLastUploadAt
        val down = settings.gitLastDownloadAt
        Text(
            "上次上传：" + if (up > 0) fmt.format(Date(up)) else "无",
            style = MaterialTheme.typography.bodySmall, color = textColor
        )
        Text(
            "上次拉取：" + if (down > 0) fmt.format(Date(down)) else "无",
            style = MaterialTheme.typography.bodySmall, color = textColor
        )
        Text(status, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.85f))
        Spacer(Modifier.height(88.dp))
    }
}
