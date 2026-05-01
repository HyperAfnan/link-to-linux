package com.linktolinux.wifidirect.presentation.ui

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linktolinux.wifidirect.presentation.viewmodel.MainViewModel

sealed class NavRoute(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : NavRoute("home", "DEVICES", Icons.Default.Home)
    object Discovery : NavRoute("discovery", "DISCOVERY", Icons.Default.Search)
    object Settings : NavRoute("settings", "SETTINGS", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    uiState: MainViewModel.UiState,
    nearbyDevices: List<WifiP2pDevice>,
    savedName: String?,
    savedMac: String?,
    onFindNearbyClick: () -> Unit,
    onDeviceClick: (WifiP2pDevice) -> Unit,
    onDisconnectClick: () -> Unit,
    onFeatureClick: (String) -> Unit,
    isDynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    customColorValue: Int,
    onCustomColorChange: (Int) -> Unit,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit
) {
    val navController = rememberNavController()

    if (uiState is MainViewModel.UiState.Connected) {
        Connected(
            deviceName = savedName ?: "Linux PC",
            onDisconnectClick = onDisconnectClick,
            onRenameClick = { /* implement dialog */ },
            onFeatureClick = onFeatureClick
        )
    } else {
        Scaffold(
            bottomBar = { BottomNavigationBar(navController = navController) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = NavRoute.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(NavRoute.Home.route) {
                    HomeScreen(
                        savedDeviceName = savedName,
                        savedDeviceMac = savedMac,
                        onFindNearbyClick = onFindNearbyClick,
                        onSavedDeviceClick = { /* Handle saved device click if needed */ }
                    )
                }
                composable(NavRoute.Discovery.route) {
                    DiscoveryScreen(
                        devices = nearbyDevices,
                        isScanning = uiState is MainViewModel.UiState.Scanning,
                        connectingDevice = if (uiState is MainViewModel.UiState.Connecting) uiState.device else null,
                        onDeviceClick = onDeviceClick,
                        onRetryClick = onFindNearbyClick
                    )
                }
                composable(NavRoute.Settings.route) {
                    SettingsScreen(
                        isDynamicColorEnabled = isDynamicColorEnabled,
                        onDynamicColorChange = onDynamicColorChange,
                        customColorValue = customColorValue,
                        onCustomColorChange = onCustomColorChange,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange
                    )
                }
            }
            
            // Auto navigate to discovery if state becomes scanning
            LaunchedEffect(uiState) {
                if (uiState is MainViewModel.UiState.Scanning) {
                    navController.navigate(NavRoute.Discovery.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(NavRoute.Home, NavRoute.Discovery, NavRoute.Settings)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}


