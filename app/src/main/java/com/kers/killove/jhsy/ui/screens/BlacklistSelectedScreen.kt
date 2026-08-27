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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kers.killove.jhsy.ui.LocalUiTextColor
import com.kers.killove.jhsy.ui.MainViewModel

/** 已选黑名单次级页 */
@Composable
fun BlacklistSelectedScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val apps by vm.launcherApps.collectAsState()
    val textColor = LocalUiTextColor.current
    val black = settings.blacklistPackages

    LaunchedEffect(Unit) { vm.loadLauncherApps() }

    val rows = remember(apps, black) {
        black.map { pkg ->
            val label = apps.firstOrNull { it.packageName == pkg }?.label
                ?: pkg
            pkg to label
        }.sortedBy { it.second.lowercase() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("已选黑名单", style = MaterialTheme.typography.headlineSmall, color = textColor)
            TextButton(onClick = onBack) { Text("返回", color = textColor) }
        }
        Text(
            "共 ${black.size} 个应用。这些应用在前台时将休眠不换壁纸。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text("尚未勾选任何应用。", color = textColor.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.first }) { (pkg, label) ->
                    GlassCard {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, color = textColor)
                                Text(
                                    pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.65f)
                                )
                            }
                            OutlinedButton(onClick = { vm.toggleBlacklistPackage(pkg) }) {
                                Text("移除")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}
