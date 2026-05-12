package com.peerlock.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "zh",
    val isDeviceOwner: Boolean = false,
    val isBatteryExempt: Boolean = false,
    val versionTapCount: Int = 0,
    val l2Unlocked: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val securePrefs: SecurePrefs,
    private val deviceOwnerManager: DeviceOwnerManager,
) : ViewModel() {

    private val prefs = application.getSharedPreferences("peerlock_settings", 0)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.SYSTEM },
            language = prefs.getString("language", "zh") ?: "zh",
            isDeviceOwner = deviceOwnerManager.isDeviceOwner(),
            isBatteryExempt = securePrefs.batteryOptimizationDone,
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _uiState.value = _uiState.value.copy(language = lang)
    }

    fun onVersionTap() {
        val count = _uiState.value.versionTapCount + 1
        if (count >= 7) {
            _uiState.value = _uiState.value.copy(versionTapCount = 0, l2Unlocked = true)
            viewModelScope.launch {
                delay(5 * 60 * 1000L)
                _uiState.value = _uiState.value.copy(l2Unlocked = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(versionTapCount = count)
        }
    }
}
