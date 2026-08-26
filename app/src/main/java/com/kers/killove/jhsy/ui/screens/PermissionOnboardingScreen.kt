package com.kers.killove.jhsy.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kers.killove.jhsy.util.ForegroundAppHelper

data class PermItem(
    val title: String,
    val desc: String,
    val granted: Boolean,
    val onGrant: () -> Unit
)

@Composable
fun PermissionOnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val items = remember(tick) {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val g = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                add(
                    PermItem(
                        "通知权限",
                        "前台服务常驻通知、立即更换按钮",
                        g
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            } else {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                add(PermItem("通知", "系统通知渠道", nm.areNotificationsEnabled(), {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }))
            }
            val mediaPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
            val mediaOk = ContextCompat.checkSelfPermission(context, mediaPerm) == PackageManager.PERMISSION_GRANTED
            add(
                PermItem("读取图片", "本地兜底 / 备份选图（可选）", mediaOk) {
                    mediaLauncher.launch(mediaPerm)
                }
            )
            add(
                PermItem(
                    "使用情况访问",
                    "应用黑名单识别前台（可选）",
                    ForegroundAppHelper.hasUsageAccess(context)
                ) { ForegroundAppHelper.openUsageAccessSettings(context) }
            )
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val ignore = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true
            add(
                PermItem("忽略电池优化", "减少后台被杀（推荐）", ignore) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("欢迎使用镜花水月", style = MaterialTheme.typography.headlineSmall)
        Text("首次使用请检查下列权限，可随时在系统设置中修改。", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (item.granted) Color(0xFF1B5E20).copy(alpha = 0.35f)
                    else Color.Black.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (item.granted) "已授权" else "未授权", color = if (item.granted) Color(0xFF81C784) else Color(0xFFFFB74D))
                    }
                    Text(item.desc, style = MaterialTheme.typography.bodySmall)
                    if (!item.granted) {
                        OutlinedButton(onClick = item.onGrant, modifier = Modifier.fillMaxWidth()) {
                            Text("去授权")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
            Text("下一步，进入软件")
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开应用详情设置") }
    }
}
