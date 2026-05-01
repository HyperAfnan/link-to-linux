package com.linktolinux.wifidirect.p2p

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class P2pState {
    object Idle : P2pState()
    object Discovering : P2pState()
    data class PeersDiscovered(val peers: List<WifiP2pDevice>) : P2pState()
    data class Connecting(val device: WifiP2pDevice) : P2pState()
    data class Connected(val info: WifiP2pInfo) : P2pState()
    data class Error(val message: String) : P2pState()
}

class WiFiDirectManager(
    private val context: Context,
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel
) : P2pEventCallback {
    private val TAG = "WiFiDirectManager"

    private val _p2pState = MutableStateFlow<P2pState>(P2pState.Idle)
    val p2pState: StateFlow<P2pState> = _p2pState.asStateFlow()

    private val receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

    fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        context.registerReceiver(receiver, intentFilter, flags)
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        _p2pState.value = P2pState.Discovering
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
                _p2pState.value = P2pState.Error("Discovery failed with code: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        _p2pState.value = P2pState.Connecting(device)
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = android.net.wifi.WpsInfo.PBC
            groupOwnerIntent = 0 // Let Linux be Group Owner
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect initiated")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connect failed: $reason")
                _p2pState.value = P2pState.Error("Connect failed with code: $reason")
            }
        })
    }

    fun cancelConnect() {
        manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect canceled")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Cancel connect failed: $reason")
            }
        })
    }

    fun timeoutConnection() {
        if (_p2pState.value is P2pState.Connecting) {
            cancelConnect()
            _p2pState.value = P2pState.Error("Connection timed out after 10 seconds")
        }
    }

    fun disconnect() {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected")
                _p2pState.value = P2pState.Idle
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason")
            }
        })
    }

    fun stopDiscovery() {
        manager.stopPeerDiscovery(channel, null)
        if (_p2pState.value is P2pState.Discovering) {
            _p2pState.value = P2pState.Idle
        }
    }

    // Callbacks from Receiver
    override fun onP2pStateChanged(enabled: Boolean) {
        if (!enabled) {
            _p2pState.value = P2pState.Error("Please enable Wi-Fi")
        }
    }

    @SuppressLint("MissingPermission")
    override fun onPeersChanged() {
        manager.requestPeers(channel) { peers ->
            _p2pState.value = P2pState.PeersDiscovered(peers.deviceList.toList())
        }
    }

    override fun onConnectionChanged(info: WifiP2pInfo?, groupFormed: Boolean) {
        if (groupFormed && info != null) {
            _p2pState.value = P2pState.Connected(info)
        } else {
            _p2pState.value = P2pState.Idle
        }
    }

    override fun onThisDeviceChanged(device: WifiP2pDevice) {
        // Track own device if needed
    }
}
