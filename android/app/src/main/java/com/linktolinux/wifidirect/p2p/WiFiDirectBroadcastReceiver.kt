package com.linktolinux.wifidirect.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.util.Log
import com.linktolinux.wifidirect.presentation.MainActivity

private const val TAG = "P2pReceiver"

private const val WIFI_P2P_ENABLED = 2

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val enabled = state == WIFI_P2P_ENABLED
                Log.d(TAG, "P2P radio enabled: $enabled")
                activity.onP2pStateChanged(enabled)
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d(TAG, "Peers list changed — requesting updated list")
                try {
                    manager.requestPeers(channel, activity.peerListListener)
                } catch (e: SecurityException) {
                    Log.e(TAG, "requestPeers denied — missing permission", e)
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                @Suppress("DEPRECATION")
                val networkInfo =
                    intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                if (networkInfo?.isConnected == true) {
                    Log.d(TAG, "P2P connected — requesting connection info")
                    // ConnectionInfoListener inside MainActivity will receive the GO IP
                    manager.requestConnectionInfo(channel, activity.connectionInfoListener)
                } else {
                    Log.d(TAG, "P2P disconnected")
                    activity.onP2pDisconnected()
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<WifiP2pDevice>(
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
                )
                device?.let {
                    Log.d(TAG, "Own device: name=${it.deviceName}, mac=${it.deviceAddress}")
                    activity.onThisDeviceChanged(it)
                }
            }
        }
    }
}

