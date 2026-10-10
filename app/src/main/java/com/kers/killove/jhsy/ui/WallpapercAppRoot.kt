package com.kers.killove.jhsy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kers.killove.jhsy.domain.CardStyle
import com.kers.killove.jhsy.ui.screens.BlacklistScreen
import com.kers.killove.jhsy.ui.screens.GlassCard
import com.kers.killove.jhsy.ui.screens.HistoryScreen
import com.kers.killove.jhsy.ui.screens.HelpGuideScreen
import com.kers.killove.jhsy.ui.screens.CloudSyncScreen
import com.kers.killove.jhsy.ui.screens.DestinyScheduleScreen
import com.kers.killove.jhsy.ui.screens.HomeScreen
import com.kers.killove.jhsy.ui.screens.LocationAvoidListScreen
import com.kers.killove.jhsy.ui.screens.LocationAvoidScreen
import com.kers.killove.jhsy.ui.screens.BlacklistSelectedScreen
import com.kers.killove.jhsy.ui.screens.OverviewScreen
import com.kers.killove.jhsy.ui.screens.PermissionOnboardingScreen
import com.kers.killove.jhsy.ui.screens.SettingsScreen

val LocalUiTextColor = compositionLocalOf { Color.White }
val LocalCardAlpha = compositionLocalOf { 0.28f }
val LocalCardStyle = compositionLocalOf { CardStyle.None }

@Composable
fun WallpapercAppRoot(vm: MainViewModel = viewModel()) {
    val onboardingDone by vm.onboardingDone.collectAsState()
    if (!onboardingDone) {
        PermissionOnboardingScreen(onFinished = { vm.finishOnboarding() })
        return
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "overview"
    val settings by vm.settings.collectAsState()
    val textColor = Color(settings.uiTextColor.argb)
    val cardAlpha = settings.uiCardAlpha
    val cardStyle = settings.cardStyle
    val minimal = settings.overviewMinimalMode

    var updateDialog by remember { mutableStateOf<com.kers.killove.jhsy.util.AppUpdateChecker.ReleaseInfo?>(null) }
    var updateChecked by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(0f) }
    var updateMsg by remember { mutableStateOf("") }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val appScope = rememberCoroutineScope()
    val status by vm.status.collectAsState()
    var statusPopup by remember { mutableStateOf<String?>(null) }
    var lastStatusSeen by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (updateChecked) return@LaunchedEffect
        updateChecked = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                when (val r = com.kers.killove.jhsy.util.AppUpdateChecker.checkLatest()) {
                    is com.kers.killove.jhsy.util.AppUpdateChecker.CheckResult.UpdateAvailable -> {
                        updateDialog = r.info
                    }
                    else -> Unit
                }
            }
        }
    }

    // 状态变化弹窗：任意页面可见（排除壁纸更换过程中的进度类文案）
    LaunchedEffect(status) {
        val s = status.trim()
        if (s.isBlank() || s == lastStatusSeen) return@LaunchedEffect
        lastStatusSeen = s
        val wallpaperProgress = listOf(
            "已交由", "当场下载", "更换流程", "正在下载壁纸", "正在设置壁纸",
            "预下载中", "更换中", "下载壁纸", "设置壁纸", "正在更换"
        ).any { s.contains(it) }
        if (wallpaperProgress) return@LaunchedEffect
        // 忽略过于琐碎的初始态
        if (s in listOf("就绪", "空闲", "—", "-")) return@LaunchedEffect
        statusPopup = s
    }

    updateDialog?.let { info ->
        val sizeMb = if (info.apkSize > 0)
            String.format("%.1f MB", info.apkSize / (1024.0 * 1024.0))
        else "未知大小"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!updateDownloading) updateDialog = null
            },
            title = { androidx.compose.material3.Text("发现新版本 ${info.tag}") },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Text(
                        listOf(info.name, info.versionName, "大小 $sizeMb", info.body.take(280))
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                    )
                    if (updateDownloading) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { updateProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.Text(
                            if (updateMsg.isNotBlank()) updateMsg
                            else "下载中 ${(updateProgress * 100).toInt()}%"
                        )
                    } else if (updateMsg.isNotBlank()) {
                        androidx.compose.material3.Text(updateMsg)
                    }
                }
            },
            confirmButton = {
                com.kers.killove.jhsy.ui.screens.ThemeOutlinedButton(
                    onClick = {
                        if (updateDownloading) return@ThemeOutlinedButton
                        val ctx = appContext
                        if (!com.kers.killove.jhsy.util.AppUpdateChecker.canInstallPackages(ctx)) {
                            updateMsg = "需要允许安装未知应用，已打开系统设置"
                            com.kers.killove.jhsy.util.AppUpdateChecker.openInstallPermissionSettings(ctx)
                            return@ThemeOutlinedButton
                        }
                        updateDownloading = true
                        updateProgress = 0f
                        updateMsg = "正在下载…"
                        appScope.launch {
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.kers.killove.jhsy.util.AppUpdateChecker.downloadApk(ctx, info) { p ->
                                    updateProgress = p
                                }
                            }
                            updateDownloading = false
                            when (result) {
                                is com.kers.killove.jhsy.util.AppUpdateChecker.DownloadResult.Ok -> {
                                    updateMsg = "下载完成，正在调起安装…"
                                    val ok = com.kers.killove.jhsy.util.AppUpdateChecker.installApk(ctx, result.file)
                                    if (!ok) {
                                        updateMsg = "无法调起安装，可打开发布页手动下载"
                                    } else {
                                        updateDialog = null
                                    }
                                }
                                is com.kers.killove.jhsy.util.AppUpdateChecker.DownloadResult.Failed -> {
                                    updateMsg = "下载失败：${result.message}"
                                }
                            }
                        }
                    },
                    enabled = !updateDownloading
                ) { androidx.compose.material3.Text(if (updateDownloading) "下载中…" else "下载并安装") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    com.kers.killove.jhsy.ui.screens.ThemeOutlinedButton(
                        onClick = {
                            com.kers.killove.jhsy.util.AppUpdateChecker.openReleasePage(appContext, info.htmlUrl)
                        },
                        enabled = !updateDownloading
                    ) { androidx.compose.material3.Text("浏览器") }
                    com.kers.killove.jhsy.ui.screens.ThemeOutlinedButton(
                        onClick = { if (!updateDownloading) updateDialog = null },
                        enabled = !updateDownloading
                    ) { androidx.compose.material3.Text("稍后") }
                }
            }
        )
    }

    statusPopup?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { statusPopup = null },
            title = { androidx.compose.material3.Text("状态更新") },
            text = { androidx.compose.material3.Text(msg) },
            confirmButton = {
                com.kers.killove.jhsy.ui.screens.ThemeOutlinedButton(
                    onClick = { statusPopup = null }
                ) { androidx.compose.material3.Text("知道了") }
            }
        )
    }

    LaunchedEffect(minimal) {
        if (minimal && route != "overview") {
            nav.navigate("overview") {
                popUpTo(0) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(
        LocalUiTextColor provides textColor,
        LocalCardAlpha provides cardAlpha,
        LocalCardStyle provides cardStyle
    ) {
        WallpaperBackground(settings = settings, scrimAlpha = settings.uiScrimAlpha) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = textColor,
                bottomBar = { }
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = nav,
                        startDestination = "overview",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(bottom = if (minimal) 0.dp else 88.dp)
                    ) {
                        composable("overview") { OverviewScreen(vm) }
                        composable("home") { HomeScreen(vm, onOpenHelp = { nav.navigate("help") }) }
                        composable("settings") {
                            SettingsScreen(
                                vm,
                                onOpenBlacklist = { nav.navigate("blacklist") },
                                onOpenLocationAvoid = { nav.navigate("location_avoid") }, onOpenDestiny = { nav.navigate("destiny") },
                                onOpenCloudSync = { nav.navigate("cloud_sync") }
                            )
                        }
                        composable("history") { HistoryScreen(vm) }
                        composable("blacklist") {
                            BlacklistScreen(
                                vm,
                                onBack = { nav.popBackStack() },
                                onOpenSelected = { nav.navigate("blacklist_selected") }
                            )
                        }
                        composable("blacklist_selected") {
                            BlacklistSelectedScreen(vm, onBack = { nav.popBackStack() })
                        }
                        composable("location_avoid") {
                            LocationAvoidScreen(
                                vm,
                                onBack = { nav.popBackStack() },
                                onOpenList = { nav.navigate("location_avoid_list") }
                            )
                        }
                        composable("location_avoid_list") {
                            LocationAvoidListScreen(vm, onBack = { nav.popBackStack() })
                        }
                        composable("cloud_sync") {
                            CloudSyncScreen(vm, onBack = { nav.popBackStack() })
                        }
                        composable("help") {
                            HelpGuideScreen(onBack = { nav.popBackStack() })
                        }
                        composable("destiny") {
                            DestinyScheduleScreen(vm, onBack = { nav.popBackStack() })
                        }
                    }

                    // 悬浮底栏：GlassCard 追随主题美化
                    if (!minimal) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth()
                        ) {
                            GlassCard {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    contentColor = textColor,
                                    tonalElevation = 0.dp
                                ) {
                                    val colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = textColor,
                                        selectedTextColor = textColor,
                                        unselectedIconColor = textColor.copy(alpha = 0.55f),
                                        unselectedTextColor = textColor.copy(alpha = 0.55f),
                                        indicatorColor = textColor.copy(alpha = 0.18f)
                                    )
                                    NavigationBarItem(
                                        selected = route == "overview",
                                        onClick = { nav.navigate("overview") { launchSingleTop = true } },
                                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                        label = { Text("概览") },
                                        colors = colors
                                    )
                                    NavigationBarItem(
                                        selected = route == "home",
                                        onClick = { nav.navigate("home") { launchSingleTop = true } },
                                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                        label = { Text("首页") },
                                        colors = colors
                                    )
                                    NavigationBarItem(
                                        selected = route == "settings",
                                        onClick = { nav.navigate("settings") { launchSingleTop = true } },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        label = { Text("设置") },
                                        colors = colors
                                    )
                                    NavigationBarItem(
                                        selected = route == "history",
                                        onClick = { nav.navigate("history") { launchSingleTop = true } },
                                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                                        label = { Text("记录") },
                                        colors = colors
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
