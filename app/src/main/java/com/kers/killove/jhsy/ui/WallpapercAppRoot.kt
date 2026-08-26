package com.kers.killove.jhsy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kers.killove.jhsy.domain.UiTextColor
import com.kers.killove.jhsy.ui.screens.HistoryScreen
import com.kers.killove.jhsy.ui.screens.HomeScreen
import com.kers.killove.jhsy.ui.screens.SettingsScreen

val LocalUiTextColor = compositionLocalOf { Color.White }
val LocalCardAlpha = compositionLocalOf { 0.28f }

@Composable
fun WallpapercAppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val settings by vm.settings.collectAsState()
    val textColor = Color(settings.uiTextColor.argb)
    val cardAlpha = settings.uiCardAlpha

    CompositionLocalProvider(
        LocalUiTextColor provides textColor,
        LocalCardAlpha provides cardAlpha
    ) {
        WallpaperBackground(settings = settings, scrimAlpha = settings.uiScrimAlpha) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = textColor,
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.Black.copy(alpha = 0.25f + cardAlpha * 0.4f),
                        contentColor = textColor,
                        windowInsets = NavigationBarDefaults.windowInsets
                    ) {
                        NavigationBarItem(
                            selected = route == "home",
                            onClick = { nav.navigate("home") { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text("首页") }
                        )
                        NavigationBarItem(
                            selected = route == "settings",
                            onClick = { nav.navigate("settings") { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("设置") }
                        )
                        NavigationBarItem(
                            selected = route == "history",
                            onClick = { nav.navigate("history") { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.List, contentDescription = null) },
                            label = { Text("记录") }
                        )
                    }
                }
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.padding(padding)
                ) {
                    composable("home") { HomeScreen(vm) }
                    composable("settings") { SettingsScreen(vm) }
                    composable("history") { HistoryScreen(vm) }
                }
            }
        }
    }
}
