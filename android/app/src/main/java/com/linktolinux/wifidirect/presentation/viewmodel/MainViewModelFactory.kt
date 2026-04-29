package com.linktolinux.wifidirect.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.linktolinux.wifidirect.p2p.WiFiDirectManager

class MainViewModelFactory(
    private val application: Application,
    private val p2pManager: WiFiDirectManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, p2pManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
