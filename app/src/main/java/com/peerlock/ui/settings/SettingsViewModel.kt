package com.peerlock.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
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
    val isPaired: Boolean = false,
    val role: String = "",
    val versionTapCount: Int = 0,
    val l2Unlocked: Boolean = false,
    val showResetConfirm: Boolean = false,
    val showBatteryGuide: Boolean = false,
    val resetCooldownSeconds: Int = 10,
    val isResetCooldownActive: Boolean = false,
    val resetComplete: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val securePrefs: SecurePrefs,
    private val deviceOwnerManager: DeviceOwnerManager,
    private val seedManager: SeedManager,
) : ViewModel() {

    private val prefs = application.getSharedPreferences("peerlock_settings", 0)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.SYSTEM },
            language = prefs.getString("language", "zh") ?: "zh",
            isDeviceOwner = deviceOwnerManager.isDeviceOwner(),
            isBatteryExempt = securePrefs.batteryOptimizationDone,
            isPaired = securePrefs.isPaired,
            role = securePrefs.role ?: "",
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

    fun requestIdentityReset() {
        _uiState.value = _uiState.value.copy(showResetConfirm = true, isResetCooldownActive = true)
        viewModelScope.launch {
            for (i in 10 downTo 0) {
                _uiState.value = _uiState.value.copy(resetCooldownSeconds = i, isResetCooldownActive = i > 0)
                if (i > 0) delay(1000)
            }
        }
    }

    fun confirmIdentityReset() {
        if (_uiState.value.isResetCooldownActive) return
        viewModelScope.launch {
            securePrefs.clearPairingData()
            seedManager.clearSeeds()
            _uiState.value = _uiState.value.copy(showResetConfirm = false, resetComplete = true)
        }
    }

    fun showBatteryGuideDialog() {
        _uiState.value = _uiState.value.copy(showBatteryGuide = true)
    }

    fun dismissBatteryGuide() {
        _uiState.value = _uiState.value.copy(showBatteryGuide = false)
    }

    fun openBatterySettings() {
        _uiState.value = _uiState.value.copy(showBatteryGuide = false)
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${application.packageName}"),
            ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
            application.startActivity(intent)
        } catch (_: Exception) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
            application.startActivity(intent)
        }
    }

    fun refreshBatteryStatus() {
        val pm = application.getSystemService(android.os.PowerManager::class.java)
        val exempt = pm.isIgnoringBatteryOptimizations(application.packageName)
        _uiState.value = _uiState.value.copy(isBatteryExempt = exempt)
        // 首次返回时系统可能延迟更新，延迟 500ms 再检查一次
        if (!exempt) {
            viewModelScope.launch {
                delay(500)
                val recheck = pm.isIgnoringBatteryOptimizations(application.packageName)
                if (recheck) {
                    _uiState.value = _uiState.value.copy(isBatteryExempt = true)
                }
            }
        }
    }

    fun cancelReset() {
        _uiState.value = _uiState.value.copy(showResetConfirm = false, isResetCooldownActive = false)
    }
}
