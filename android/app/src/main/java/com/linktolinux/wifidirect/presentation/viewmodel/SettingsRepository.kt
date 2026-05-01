package com.linktolinux.wifidirect.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("link_to_linux_prefs", Context.MODE_PRIVATE)

    private val _isDynamicColorEnabled = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()

    private val _customColorValue = MutableStateFlow(prefs.getInt("custom_color", 0xFF0F4C3A.toInt()))
    val customColorValue: StateFlow<Int> = _customColorValue.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0)) // 0=System, 1=Light, 2=Dark
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()
    
    private val _savedDeviceName = MutableStateFlow<String?>(prefs.getString("saved_name", null))
    val savedDeviceName: StateFlow<String?> = _savedDeviceName.asStateFlow()

    private val _savedDeviceMac = MutableStateFlow<String?>(prefs.getString("saved_mac", null))
    val savedDeviceMac: StateFlow<String?> = _savedDeviceMac.asStateFlow()

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _isDynamicColorEnabled.value = enabled
    }

    fun setCustomColorValue(color: Int) {
        prefs.edit().putInt("custom_color", color).apply()
        _customColorValue.value = color
    }

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        _themeMode.value = mode
    }
    
    fun setSavedDevice(name: String, mac: String) {
        prefs.edit().putString("saved_name", name).putString("saved_mac", mac).apply()
        _savedDeviceName.value = name
        _savedDeviceMac.value = mac
    }
}
