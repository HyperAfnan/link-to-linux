package com.linktolinux.wifidirect.p2p

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WiFiDirectManager(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel
) {
    private val TAG = "WiFiDirectManager"

    @SuppressLint("MissingPermission")
    fun discoverPeers(onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
                requestPeers { peers ->
                    Log.d(TAG, "Peers discovered: ${peers.deviceList.joinToString { it.deviceName + " (" + it.deviceAddress + ")" }}")
                }
                onSuccess()
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
                onFailure(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = android.net.wifi.WpsInfo.PBC
            groupOwnerIntent = 0 // Let Linux be Group Owner
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect initiated")
                onSuccess()
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connect failed: $reason")
                onFailure(reason)
            }
        })
    }

    fun disconnect(onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected")
                onSuccess()
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason")
                onFailure(reason)
            }
        })
    }

    fun stopDiscovery() {
        manager.stopPeerDiscovery(channel, null)
    }

    @SuppressLint("MissingPermission")
    fun requestPeers(listener: WifiP2pManager.PeerListListener) {
        manager.requestPeers(channel, listener)
    }

    @SuppressLint("MissingPermission")
    fun requestConnectionInfo(listener: WifiP2pManager.ConnectionInfoListener) {
        manager.requestConnectionInfo(channel, listener)
    }
}
