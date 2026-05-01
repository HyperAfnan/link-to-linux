package com.linktolinux.wifidirect.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
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
import com.linktolinux.wifidirect.p2p.WiFiDirectBroadcastReceiver
import com.linktolinux.wifidirect.p2p.WiFiDirectManager
import com.linktolinux.wifidirect.presentation.viewmodel.MainViewModel
import com.linktolinux.wifidirect.presentation.viewmodel.MainViewModelFactory
import com.linktolinux.wifidirect.presentation.ui.HomeScreen
import com.linktolinux.wifidirect.presentation.ui.DiscoveryScreen
import com.linktolinux.wifidirect.presentation.ui.Connected
import com.linktolinux.wifidirect.presentation.ui.LinkToLinuxTheme
import com.linktolinux.wifidirect.notifications.NotificationHelper
import com.linktolinux.wifidirect.p2p.P2pEventCallback
import android.net.wifi.p2p.WifiP2pInfo

class MainActivity : AppCompatActivity(), P2pEventCallback {

    private lateinit var p2pManager: WiFiDirectManager
    private lateinit var receiver: WiFiDirectBroadcastReceiver
    private lateinit var prefs: SharedPreferences

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, p2pManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Hardware Eagerly
        val manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        p2pManager = WiFiDirectManager(manager, channel)
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        
        // Initialize Notification Channel
        NotificationHelper.createNotificationChannel(this)
        
        prefs = getSharedPreferences("link_to_linux_prefs", Context.MODE_PRIVATE)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val nearbyDevices by viewModel.nearbyDevices.collectAsState()
            val savedName = prefs.getString("saved_name", null)
            val savedMac = prefs.getString("saved_mac", null)
            
            var isDynamicColorEnabled by remember { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }
            var customColorValue by remember { mutableStateOf(prefs.getInt("custom_color", 0xFF0F4C3A.toInt())) }
            var themeMode by remember { mutableStateOf(prefs.getInt("theme_mode", 0)) } // 0=System, 1=Light, 2=Dark
            
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
                        onDynamicColorChange = {
                            isDynamicColorEnabled = it
                            prefs.edit().putBoolean("dynamic_color", it).apply()
                        },
                        customColorValue = customColorValue,
                        onCustomColorChange = {
                            customColorValue = it
                            prefs.edit().putInt("custom_color", it).apply()
                        },
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            prefs.edit().putInt("theme_mode", it).apply()
                        }
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
            Log.d("MainActivity", "All permissions granted, starting discovery")
            viewModel.startDiscovery()
            Log.d("MainActivity", "Discovery started")
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) viewModel.startDiscovery()
        }

    // Callbacks for P2pEventCallback
    override fun onP2pStateChanged(enabled: Boolean) {
        if (!enabled) Toast.makeText(this, "Please enable Wi-Fi", Toast.LENGTH_SHORT).show()
    }

    override fun onPeersChanged() {
        p2pManager.requestPeers { peerList ->
            viewModel.onPeersAvailable(peerList.deviceList.toList())
        }
    }

    override fun onConnectionChanged(info: WifiP2pInfo?, groupFormed: Boolean) {
        if (groupFormed && info != null) {
            viewModel.onConnectionInfoAvailable(info)
        } else {
            viewModel.disconnect()
        }
    }

    override fun onThisDeviceChanged(device: WifiP2pDevice) {
        Log.d("MainActivity", "Device MAC: ${device.deviceAddress}")
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}
