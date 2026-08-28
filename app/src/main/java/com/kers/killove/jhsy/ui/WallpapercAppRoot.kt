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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
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
import com.kers.killove.jhsy.ui.screens.HomeScreen
import com.kers.killove.jhsy.ui.screens.LocationAvoidListScreen
import com.kers.killove.jhsy.ui.screens.LocationAvoidScreen
import com.kers.killove.jhsy.ui.screens.BlacklistSelectedScreen
import com.kers.killove.jhsy.ui.screens.ProxyNodesScreen
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
                                onOpenLocationAvoid = { nav.navigate("location_avoid") },
                                onOpenProxyNodes = { nav.navigate("proxy_nodes") }
                            )
                        }
                        composable("proxy_nodes") {
                            ProxyNodesScreen(vm, onBack = { nav.popBackStack() })
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
                        composable("help") {
                            HelpGuideScreen(onBack = { nav.popBackStack() })
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
