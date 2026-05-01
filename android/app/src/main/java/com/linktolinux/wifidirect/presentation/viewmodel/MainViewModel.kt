package com.linktolinux.wifidirect.presentation.viewmodel

import android.net.wifi.p2p.WifiP2pDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linktolinux.wifidirect.clipboard.ClipboardHelper
import com.linktolinux.wifidirect.network.SocketClient
import com.linktolinux.wifidirect.network.models.SocketMessage
import com.linktolinux.wifidirect.notifications.NotificationHelper
import com.linktolinux.wifidirect.p2p.P2pState
import com.linktolinux.wifidirect.p2p.WiFiDirectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val p2pManager: WiFiDirectManager,
    private val socketClient: SocketClient,
    private val notificationHelper: NotificationHelper,
    private val clipboardHelper: ClipboardHelper
) : ViewModel() {
    
    private val TAG = "MainViewModel"

    private val _nearbyDevices = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<WifiP2pDevice>> = _nearbyDevices

    val uiState: StateFlow<UiState> = combine(
        p2pManager.p2pState,
        socketClient.connectionState
    ) { p2pState, socketState ->
        when (p2pState) {
            is P2pState.Idle -> UiState.Idle
            is P2pState.Discovering -> UiState.Scanning
            is P2pState.Connecting -> UiState.Connecting(p2pState.device)
            is P2pState.PeersDiscovered -> {
                _nearbyDevices.value = p2pState.peers
                UiState.Idle
            }
            is P2pState.Connected -> {
                // If we are P2P connected, we show connected UI even if socket is disconnected/reconnecting
                // The socket handles its own reconnection or UI can show "Connecting socket..." later
                UiState.Connected
            }
            is P2pState.Error -> {
                notificationHelper.showConnectionFailedNotification(p2pState.message)
                UiState.Error(p2pState.message)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Idle)

    init {
        observeP2pState()
    }

    private fun observeP2pState() {
        viewModelScope.launch {
            p2pManager.p2pState.collect { state ->
                when (state) {
                    is P2pState.Connected -> {
                        notificationHelper.showConnectedNotification()
                        clipboardHelper.copyTextToClipboard("hello world")
                        val goIp = state.info.groupOwnerAddress?.hostAddress
                        if (!state.info.isGroupOwner && goIp != null) {
                            socketClient.connect(goIp)
                        }
                    }
                    is P2pState.Idle -> {
                        socketClient.disconnect()
                    }
                    else -> {}
                }
            }
        }
    }

    fun startDiscovery() {
        if (uiState.value is UiState.Scanning || uiState.value is UiState.Connecting) {
            return
        }
        p2pManager.discoverPeers()
    }

    private var connectJob: kotlinx.coroutines.Job? = null

    fun connectToDevice(device: WifiP2pDevice) {
        p2pManager.connect(device)
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            kotlinx.coroutines.delay(10000)
            if (uiState.value is UiState.Connecting) {
                p2pManager.timeoutConnection()
            }
        }
    }

    fun disconnect() {
        p2pManager.disconnect()
        socketClient.disconnect()
        notificationHelper.showDisconnectedNotification()
    }

    fun stopDiscovery() {
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
        socketClient.disconnect()
    }
}
