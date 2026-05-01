package com.linktolinux.wifidirect.presentation.viewmodel

import android.app.Application
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linktolinux.wifidirect.network.SocketClient
import com.linktolinux.wifidirect.network.models.SocketMessage
import com.linktolinux.wifidirect.p2p.WiFiDirectManager
import com.linktolinux.wifidirect.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val p2pManager: WiFiDirectManager
) : AndroidViewModel(application) {
    
    private val TAG = "MainViewModel"
    private val socketClient = SocketClient()
    private val notificationHelper = NotificationHelper(application)
    private var discoveryActive = false

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<WifiP2pDevice>> = _nearbyDevices.asStateFlow()

    init {
        observeSocketState()
    }

    private fun observeSocketState() {
        viewModelScope.launch {
            socketClient.connectionState.collect { state ->
                when (state) {
                    is SocketClient.State.Connected -> {
                        _uiState.value = UiState.Connected
                    }
                    is SocketClient.State.Disconnected -> {
                        if (_uiState.value is UiState.Connected) {
                            _uiState.value = UiState.Idle
                        }
                    }
                    is SocketClient.State.Error -> {
                        _uiState.value = UiState.Error("Network error: ${state.message}")
                        notificationHelper.showConnectionFailedNotification("Network error: ${state.message}")
                    }
                    is SocketClient.State.Connecting -> {
                        // Optionally update UI for network-level connecting
                    }
                }
            }
        }
    }

    val incomingMessages = socketClient.incomingMessages

    fun startDiscovery() {
        if (discoveryActive || _uiState.value is UiState.Scanning || _uiState.value is UiState.Connecting) {
            Log.d(TAG, "Ignoring duplicate discovery request")
            return
        }

        discoveryActive = true
        _uiState.value = UiState.Scanning
        p2pManager.discoverPeers(
            onSuccess = { Log.d(TAG, "Discovery success") },
            onFailure = { 
                discoveryActive = false
                _uiState.value = UiState.Error("Discovery failed: $it")
                notificationHelper.showConnectionFailedNotification("Discovery failed: $it")
            }
        )
    }

    fun onPeersAvailable(peers: List<WifiP2pDevice>) {
        _nearbyDevices.value = peers
        if (_uiState.value is UiState.Scanning) {
            _uiState.value = UiState.Idle
        }
    }

    fun connectToDevice(device: WifiP2pDevice) {
        discoveryActive = false
        _uiState.value = UiState.Connecting(device)
        p2pManager.connect(
            device,
            onSuccess = { 
               Log.d(TAG, "Connect success")
            },
            onFailure = { 
                _uiState.value = UiState.Error("Connect failed: $it")
                notificationHelper.showConnectionFailedNotification("Connect failed: $it")
            }
        )
    }

    fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (info.groupFormed) {
            // Only trigger connect if we are not already connected at the P2P level
            if (_uiState.value !is UiState.Connected) {
                notificationHelper.showConnectedNotification()
                viewModelScope.launch {
                  val goIp = info.groupOwnerAddress?.hostAddress
                  if (!info.isGroupOwner && goIp != null) {
                     socketClient.connect(goIp)
                  }
                }
            }
        } else {
            if (_uiState.value is UiState.Connected) {
                notificationHelper.showDisconnectedNotification()
            }
            socketClient.disconnect()
            _uiState.value = UiState.Idle
        }
    }

    fun disconnect() {
        discoveryActive = false
        p2pManager.disconnect(
            onSuccess = { 
                if (_uiState.value is UiState.Connected) {
                    notificationHelper.showDisconnectedNotification()
                }
                _uiState.value = UiState.Idle 
            },
            onFailure = { }
        )
        socketClient.disconnect()
    }

    fun stopDiscovery() {
        discoveryActive = false
        p2pManager.stopDiscovery()
    }

    fun sendMessage(type: String, payload: String) {
        viewModelScope.launch {
            socketClient.sendMessage(SocketMessage(
                type = type,
                sender_id = android.os.Build.MODEL,
                payload = payload,
                timestamp = System.currentTimeMillis() / 1000
            ))
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Scanning : UiState()
        data class Connecting(val device: WifiP2pDevice) : UiState()
        object Connected : UiState()
        data class Error(val message: String) : UiState()
    }

    override fun onCleared() {
        super.onCleared()
        discoveryActive = false
        socketClient.disconnect()
    }
}
