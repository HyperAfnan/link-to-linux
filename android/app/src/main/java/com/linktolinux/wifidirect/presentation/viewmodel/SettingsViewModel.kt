package com.linktolinux.wifidirect.presentation.viewmodel

import androidx.lifecycle.ViewModel

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val isDynamicColorEnabled = settingsRepository.isDynamicColorEnabled
    val customColorValue = settingsRepository.customColorValue
    val themeMode = settingsRepository.themeMode
    val savedDeviceName = settingsRepository.savedDeviceName
    val savedDeviceMac = settingsRepository.savedDeviceMac

    fun setDynamicColorEnabled(enabled: Boolean) = settingsRepository.setDynamicColorEnabled(enabled)
    fun setCustomColorValue(color: Int) = settingsRepository.setCustomColorValue(color)
    fun setThemeMode(mode: Int) = settingsRepository.setThemeMode(mode)
    fun setSavedDevice(name: String, mac: String) = settingsRepository.setSavedDevice(name, mac)
}
