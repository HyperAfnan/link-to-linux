package com.linktolinux.wifidirect.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.linktolinux.wifidirect.clipboard.ClipboardHelper
import com.linktolinux.wifidirect.network.SocketClient
import com.linktolinux.wifidirect.notifications.NotificationHelper
import com.linktolinux.wifidirect.p2p.WiFiDirectManager
import com.linktolinux.wifidirect.presentation.viewmodel.MainViewModel
import com.linktolinux.wifidirect.presentation.viewmodel.MainViewModelFactory
import com.linktolinux.wifidirect.presentation.viewmodel.SettingsRepository
import com.linktolinux.wifidirect.presentation.viewmodel.SettingsViewModel
import com.linktolinux.wifidirect.presentation.viewmodel.SettingsViewModelFactory
import com.linktolinux.wifidirect.presentation.ui.LinkToLinuxTheme

class MainActivity : AppCompatActivity() {

    private lateinit var p2pManager: WiFiDirectManager

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            p2pManager = p2pManager,
            socketClient = SocketClient(),
            notificationHelper = NotificationHelper(applicationContext),
            clipboardHelper = ClipboardHelper(applicationContext)
        )
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(SettingsRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        p2pManager = WiFiDirectManager(this, manager, channel)
        
        NotificationHelper.createNotificationChannel(this)
        p2pManager.registerReceiver()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val nearbyDevices by viewModel.nearbyDevices.collectAsState()
            
            val savedName by settingsViewModel.savedDeviceName.collectAsState()
            val savedMac by settingsViewModel.savedDeviceMac.collectAsState()
            val isDynamicColorEnabled by settingsViewModel.isDynamicColorEnabled.collectAsState()
            val customColorValue by settingsViewModel.customColorValue.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            
            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            
            LinkToLinuxTheme(
                darkTheme = isDark,
                dynamicColor = isDynamicColorEnabled,
                customColor = androidx.compose.ui.graphics.Color(customColorValue)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.linktolinux.wifidirect.presentation.ui.MainScreen(
                        uiState = uiState,
                        nearbyDevices = nearbyDevices,
                        savedName = savedName,
                        savedMac = savedMac,
                        onFindNearbyClick = { checkPermissionsAndDiscover() },
                        onDeviceClick = { device -> viewModel.connectToDevice(device) },
                        onDisconnectClick = { viewModel.disconnect() },
                        onFeatureClick = { feature -> viewModel.sendMessage("FEATURE_CLICK", feature) },
                        isDynamicColorEnabled = isDynamicColorEnabled,
                        onDynamicColorChange = { settingsViewModel.setDynamicColorEnabled(it) },
                        customColorValue = customColorValue,
                        onCustomColorChange = { settingsViewModel.setCustomColorValue(it) },
                        themeMode = themeMode,
                        onThemeModeChange = { settingsViewModel.setThemeMode(it) }
                    )
                }
            }
            
            val currentState = uiState
            if (currentState is MainViewModel.UiState.Error) {
                LaunchedEffect(currentState) {
                    Toast.makeText(this@MainActivity, currentState.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkPermissionsAndDiscover() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()

        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            viewModel.startDiscovery()
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) viewModel.startDiscovery()
        }

    override fun onDestroy() {
        super.onDestroy()
        p2pManager.unregisterReceiver()
    }
}
