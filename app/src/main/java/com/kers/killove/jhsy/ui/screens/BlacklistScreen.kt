package com.kers.killove.jhsy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(vm: MainViewModel, onBack: () -> Unit, onOpenSelected: () -> Unit = {}) {
    val settings by vm.settings.collectAsState()
    val apps by vm.launcherApps.collectAsState()
    val textColor = LocalUiTextColor.current
    var query by remember { mutableStateOf("") }
    val black = settings.blacklistPackages.toSet()

    LaunchedEffect(Unit) { vm.loadLauncherApps() }

    val selected = remember(apps, black) {
        apps.filter { it.packageName in black }.sortedBy { it.label.lowercase() }
    }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("应用黑名单", color = textColor) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "勾选的应用在前台时休眠不换壁纸。需授予「使用情况访问」权限。",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            val hasUsage = vm.hasUsageAccess()
            Text(
                if (hasUsage) "使用情况访问：已授权" else "使用情况访问：未授权",
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
            OutlinedButton(
                onClick = { vm.openUsageAccessSettings() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (hasUsage) "打开使用情况访问设置" else "前往授权") }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenSelected,
                modifier = Modifier.fillMaxWidth()
            ) { Text("查看已选名单（${selected.size}）") }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = textColor.copy(alpha = 0.2f))
            Text("搜索全部应用", style = MaterialTheme.typography.titleSmall, color = textColor)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("名称或包名") }
            )
            Text("${filtered.size} / ${apps.size}", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.6f))
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = app.packageName in black,
                        onCheckedChange = { vm.toggleBlacklistPackage(app.packageName) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        Text(app.packageName, color = textColor.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
