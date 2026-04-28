package com.linktolinux.wifidirect

import android.Manifest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.linktolinux.wifidirect.databinding.ActivityMainBinding

private const val TAG = "MainActivity"
private const val PREFS_NAME = "link_to_linux_prefs"
private const val KEY_SAVED_MAC = "saved_mac"
private const val KEY_SAVED_NAME = "saved_name"
private const val KEY_SAVED_TYPE = "saved_type"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WiFiDirectBroadcastReceiver
    private lateinit var prefs: SharedPreferences

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var ownMacAddress: String = "(discovering…)"
    
    private var pendingDevice: WifiP2pDevice? = null
    private var pendingIconRes: Int? = null

    internal val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val peers = peerList.deviceList.toList()
        Log.d(TAG, "Peers available: ${peers.size}")
        
        binding.loadingLayout.visibility = View.GONE
        binding.listDevices.removeAllViews()

        if (peers.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No devices found. Make sure Linux is scanning."
                setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                setPadding(0, 32, 0, 0)
            }
            binding.listDevices.addView(emptyText)
            return@PeerListListener
        }

        peers.forEach { device ->
            val cardView = layoutInflater.inflate(R.layout.item_device_card, binding.listDevices, false)
            
            val ivIcon = cardView.findViewById<ImageView>(R.id.ivDeviceType)
            val tvName = cardView.findViewById<TextView>(R.id.tvDeviceName)
            val tvMac = cardView.findViewById<TextView>(R.id.tvDeviceMac)
            
            val name = device.deviceName.ifBlank { "Unknown Device" }
            tvName.text = name
            tvMac.text = device.deviceAddress
            
            val typeStr = device.primaryDeviceType ?: ""
            val iconRes = when {
                typeStr.startsWith("10-") -> android.R.drawable.ic_menu_call
                typeStr.startsWith("7-") -> android.R.drawable.ic_menu_gallery
                typeStr.startsWith("1-") -> android.R.drawable.ic_menu_manage
                name.contains("TV", ignoreCase = true) -> android.R.drawable.ic_menu_gallery
                else -> android.R.drawable.ic_menu_help
            }
            ivIcon.setImageResource(iconRes)

            val pbConnecting = cardView.findViewById<android.widget.ProgressBar>(R.id.pbConnecting)
            val ivArrow = cardView.findViewById<ImageView>(R.id.ivArrow)
            
            if (device.deviceAddress == pendingDevice?.deviceAddress) {
                pbConnecting?.visibility = View.VISIBLE
                ivArrow?.visibility = View.GONE
            }

            cardView.setOnClickListener {
                connectToPeer(device, iconRes, cardView)
            }
            
            binding.listDevices.addView(cardView)
        }
    }

    internal val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info: WifiP2pInfo ->
        Log.d(TAG, "Connection info received: GO=${info.isGroupOwner}")
        
        pendingDevice?.let { 
            saveConnectedDevice(it, pendingIconRes ?: android.R.drawable.ic_menu_help) 
        }
        pendingDevice = null
        pendingIconRes = null
        
        Toast.makeText(this, "Device is connected", Toast.LENGTH_SHORT).show()
        showScreen1()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

        binding.btnFindNearby.setOnClickListener { checkPermissionsAndDiscover() }
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.screen2.visibility == View.VISIBLE) {
                    stopDiscovery()
                    showScreen1()
                } else {
                    finish()
                }
            }
        })
        
        binding.cardSavedDevice.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Disconnect")
                .setMessage("Do you want to disconnect from this device and forget it?")
                .setPositiveButton("Disconnect") { _, _ -> disconnectAndForget() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        showScreen1()
        fetchOwnDeviceInfo()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun showScreen1() {
        binding.screen1.visibility = View.VISIBLE
        binding.screen2.visibility = View.GONE
        
        val savedMac = prefs.getString(KEY_SAVED_MAC, null)
        if (savedMac != null) {
            val savedName = prefs.getString(KEY_SAVED_NAME, "Linux PC")
            val savedType = prefs.getInt(KEY_SAVED_TYPE, android.R.drawable.ic_menu_manage)
            
            binding.ivHero.visibility = View.GONE
            binding.tvStatusTitle.text = "Connected Device"
            
            binding.cardSavedDevice.visibility = View.VISIBLE
            binding.tvSavedDeviceName.text = savedName
            binding.tvSavedDeviceMac.text = savedMac
            binding.ivSavedDeviceIcon.setImageResource(savedType)
        } else {
            binding.ivHero.visibility = View.VISIBLE
            binding.tvStatusTitle.text = "No Connected Device"
            binding.cardSavedDevice.visibility = View.GONE
        }
    }

    private fun showScreen2() {
        binding.screen1.visibility = View.GONE
        binding.screen2.visibility = View.VISIBLE
        binding.loadingLayout.visibility = View.VISIBLE
        binding.listDevices.removeAllViews()
    }

    private fun saveConnectedDevice(device: WifiP2pDevice, iconRes: Int) {
        val name = device.deviceName.ifBlank { "Linux PC" }
        prefs.edit()
            .putString(KEY_SAVED_MAC, device.deviceAddress)
            .putString(KEY_SAVED_NAME, name)
            .putInt(KEY_SAVED_TYPE, iconRes)
            .apply()
        showScreen1()
    }

    private fun disconnectAndForget() {
        prefs.edit().clear().apply()
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = showScreen1()
            override fun onFailure(reason: Int) = showScreen1()
        })
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                startDiscovery()
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
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
            startDiscovery()
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private fun startDiscovery() {
        showScreen2()
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = doDiscover()
            override fun onFailure(reason: Int) = doDiscover()
        })
    }

    private fun stopDiscovery() {
        try {
            manager.stopPeerDiscovery(channel, null)
            Log.d(TAG, "Peer discovery stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to stop discovery", e)
        }
    }

    private fun doDiscover() {
        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery started")
                }
                override fun onFailure(reason: Int) {
                    Toast.makeText(this@MainActivity, "Discovery failed", Toast.LENGTH_SHORT).show()
                    binding.loadingLayout.visibility = View.GONE
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "discoverPeers denied", e)
        }
    }

    private fun connectToPeer(device: WifiP2pDevice, iconRes: Int, cardView: View) {
        if (pendingDevice != null) {
            Log.d(TAG, "Already connecting, ignoring click")
            return
        }

        val pbConnecting = cardView.findViewById<android.widget.ProgressBar>(R.id.pbConnecting)
        val ivArrow = cardView.findViewById<ImageView>(R.id.ivArrow)
        pbConnecting?.visibility = View.VISIBLE
        ivArrow?.visibility = View.GONE
        
        pendingDevice = device
        pendingIconRes = iconRes

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = android.net.wifi.WpsInfo.PBC
            groupOwnerIntent = 15 // Force Android to be Group Owner
        }

        try {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // Framework accepted the request. Actual connection handled by connectionInfoListener.
                    Log.d(TAG, "Connect initiated")
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(this@MainActivity, "Connect failed", Toast.LENGTH_SHORT).show()
                    pendingDevice = null
                    pendingIconRes = null
                    
                    try {
                        manager.requestPeers(channel, peerListListener)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "requestPeers failed", e)
                    }
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "connect denied", e)
        }
    }

    fun onP2pStateChanged(enabled: Boolean) {
        if (!enabled) Toast.makeText(this, "Please enable Wi-Fi", Toast.LENGTH_SHORT).show()
    }

    fun onP2pDisconnected() {
        Log.d(TAG, "disconnected from peer.")
    }

    fun onThisDeviceChanged(device: WifiP2pDevice) {
        ownMacAddress = device.deviceAddress
        Log.d(TAG, "Own P2P MAC updated: $ownMacAddress")
    }

    private fun fetchOwnDeviceInfo() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                manager.requestDeviceInfo(channel) { device ->
                    device?.let {
                        ownMacAddress = it.deviceAddress
                        Log.d(TAG, "Own P2P MAC: $ownMacAddress")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "requestDeviceInfo denied — MAC will appear after discovery", e)
        }
    }

    private fun p2pErrorReason(reason: Int) = when (reason) {
        WifiP2pManager.ERROR           -> "Internal error"
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P not supported on this device"
        WifiP2pManager.BUSY            -> "Framework busy — try again"
        else                           -> "Unknown error ($reason)"
    }
}
