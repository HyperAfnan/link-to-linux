package com.linktolinux.wifidirect.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.linktolinux.wifidirect.clipboard.ClipboardHelper
import com.linktolinux.wifidirect.network.SocketClient
import com.linktolinux.wifidirect.notifications.NotificationHelper
import com.linktolinux.wifidirect.p2p.WiFiDirectManager

class MainViewModelFactory(
    private val p2pManager: WiFiDirectManager,
    private val socketClient: SocketClient,
    private val notificationHelper: NotificationHelper,
    private val clipboardHelper: ClipboardHelper
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(p2pManager, socketClient, notificationHelper, clipboardHelper) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
