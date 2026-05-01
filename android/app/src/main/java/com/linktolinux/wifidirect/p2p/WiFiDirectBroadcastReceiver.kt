package com.linktolinux.wifidirect.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.util.Log

interface P2pEventCallback {
    fun onP2pStateChanged(enabled: Boolean)
    fun onPeersChanged()
    fun onConnectionChanged(info: android.net.wifi.p2p.WifiP2pInfo?, groupFormed: Boolean)
    fun onThisDeviceChanged(device: WifiP2pDevice)
}

private const val TAG = "P2pReceiver"

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: Channel,
    private val callback: P2pEventCallback
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.d(TAG, "P2P radio enabled: $enabled")
                callback.onP2pStateChanged(enabled)
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d(TAG, "Peers list changed — notifying callback")
                callback.onPeersChanged()
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
               val p2pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                  intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, android.net.wifi.p2p.WifiP2pInfo::class.java)
               } else {
                  @Suppress("DEPRECATION")
                  intent.getParcelableExtra<android.net.wifi.p2p.WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
               }

               if (p2pInfo?.groupFormed == true) {
                  Log.d(TAG, "P2P connected (Group Formed)")
                  callback.onConnectionChanged(p2pInfo, true)
               } else {
                  Log.d(TAG, "P2P disconnected (Group Dissolved)")
                  callback.onConnectionChanged(null, false)
               }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                device?.let {
                    Log.d(TAG, "Own device: name=${it.deviceName}, mac=${it.deviceAddress}")
                    callback.onThisDeviceChanged(it)
                }
            }
        }
    }
}
