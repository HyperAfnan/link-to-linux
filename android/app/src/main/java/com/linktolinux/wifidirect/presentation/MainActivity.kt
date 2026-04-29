package com.linktolinux.wifidirect.presentation

import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
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
import com.linktolinux.wifidirect.presentation.ui.Screen3
import com.linktolinux.wifidirect.presentation.ui.LinkToLinuxTheme

class MainActivity : AppCompatActivity() {

    private lateinit var p2pManager: WiFiDirectManager
    private lateinit var receiver: WiFiDirectBroadcastReceiver
    private lateinit var prefs: SharedPreferences

    private val viewModel: MainViewModel by viewModels {
        val manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        p2pManager = WiFiDirectManager(manager, channel)
        MainViewModelFactory(application, p2pManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        prefs = getSharedPreferences("link_to_linux_prefs", Context.MODE_PRIVATE)
        
        // Setup P2P Receiver
        val wifiManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = wifiManager.initialize(this, mainLooper, null)
        receiver = WiFiDirectBroadcastReceiver(wifiManager, channel, this)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val nearbyDevices by viewModel.nearbyDevices.collectAsState()
            
            LinkToLinuxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(uiState, nearbyDevices)
                }
            }
        }
    }

    @Composable
    private fun AppNavigation(uiState: MainViewModel.UiState, nearbyDevices: List<WifiP2pDevice>) {
        val savedName = prefs.getString("saved_name", null)
        val savedMac = prefs.getString("saved_mac", null)

        when (uiState) {
            is MainViewModel.UiState.Connected -> {
                Screen3(
                    deviceName = savedName ?: "Linux PC",
                    onDisconnectClick = { viewModel.disconnect() },
                    onRenameClick = { /* implement dialog */ },
                    onFeatureClick = { feature ->
                        viewModel.sendMessage("FEATURE_CLICK", feature)
                    }
                )
            }
            is MainViewModel.UiState.Scanning, is MainViewModel.UiState.Idle, is MainViewModel.UiState.Error -> {
                if (uiState is MainViewModel.UiState.Scanning || nearbyDevices.isNotEmpty()) {
                    DiscoveryScreen(
                        devices = nearbyDevices,
                        isScanning = uiState is MainViewModel.UiState.Scanning,
                        onDeviceClick = { device -> viewModel.connectToDevice(device) }
                    )
                } else {
                    HomeScreen(
                        savedDeviceName = savedName,
                        savedDeviceMac = savedMac,
                        onFindNearbyClick = { checkPermissionsAndDiscover() },
                        onSavedDeviceClick = { /* Handle saved device click if needed */ }
                    )
                }
            }
            is MainViewModel.UiState.Connecting -> {
                DiscoveryScreen(
                    devices = nearbyDevices,
                    isScanning = true,
                    onDeviceClick = {}
                )
            }
        }
        
        if (uiState is MainViewModel.UiState.Error) {
            LaunchedEffect(uiState) {
                Toast.makeText(this@MainActivity, uiState.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndDiscover() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
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

    // Callbacks for BroadcastReceiver
    fun onP2pStateChanged(enabled: Boolean) {
        if (!enabled) Toast.makeText(this, "Please enable Wi-Fi", Toast.LENGTH_SHORT).show()
    }

    fun onP2pDisconnected() = viewModel.disconnect()

    fun onThisDeviceChanged(device: WifiP2pDevice) {
        Log.d("MainActivity", "Device MAC: ${device.deviceAddress}")
    }

    val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        viewModel.onPeersAvailable(peerList.deviceList.toList())
    }

    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        viewModel.onConnectionInfoAvailable(info)
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
