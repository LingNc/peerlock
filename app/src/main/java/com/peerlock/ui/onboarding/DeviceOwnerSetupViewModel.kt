package com.peerlock.ui.onboarding

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class SetupPhase { DO_SETUP, BATTERY_OPTIMIZATION }

data class DeviceOwnerSetupUiState(
    val phase: SetupPhase = SetupPhase.DO_SETUP,
    val isDeviceOwner: Boolean = false,
    val isChecked: Boolean = false,
    val showWirelessGuide: Boolean = false,
    val isBatteryExempt: Boolean = false,
    val batteryRequested: Boolean = false,
    val statusMessage: String? = null,
)

@HiltViewModel
class DeviceOwnerSetupViewModel @Inject constructor(
    private val deviceOwnerManager: DeviceOwnerManager,
    private val securePrefs: SecurePrefs,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceOwnerSetupUiState())
    val uiState: StateFlow<DeviceOwnerSetupUiState> = _uiState.asStateFlow()

    private val powerManager = application.getSystemService(PowerManager::class.java)

    val adbCommand: String
        get() = "adb shell dpm set-device-owner ${application.packageName}/.system.deviceadmin.PeerLockDeviceAdminReceiver"

    val supportsWirelessAdb: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R // Android 11+

    init {
        checkDeviceOwnerStatus()
        checkBatteryOptimization()
    }

    fun checkDeviceOwnerStatus() {
        val isOwner = deviceOwnerManager.isDeviceOwner()
        _uiState.value = _uiState.value.copy(
            isDeviceOwner = isOwner,
            isChecked = true,
            statusMessage = if (!isOwner) "Device Owner 未设置，请先在电脑上执行命令" else null,
        )
    }

    fun toggleWirelessGuide() {
        _uiState.value = _uiState.value.copy(showWirelessGuide = !_uiState.value.showWirelessGuide)
    }

    fun checkBatteryOptimization() {
        val exempt = powerManager.isIgnoringBatteryOptimizations(application.packageName)
        _uiState.value = _uiState.value.copy(isBatteryExempt = exempt)
    }

    fun advanceToDoComplete() {
        // DO 步骤完成后，检查电池优化是否已豁免
        checkBatteryOptimization()
        if (_uiState.value.isBatteryExempt) {
            // 已豁免，直接标记完成
            securePrefs.batteryOptimizationDone = true
        } else {
            _uiState.value = _uiState.value.copy(phase = SetupPhase.BATTERY_OPTIMIZATION)
        }
    }

    fun requestBatteryExemption() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${application.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(intent)
            _uiState.value = _uiState.value.copy(batteryRequested = true)
        } catch (e: Exception) {
            // Fallback: 打开通用电池优化设置页
            try {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                application.startActivity(intent)
                _uiState.value = _uiState.value.copy(batteryRequested = true)
            } catch (_: Exception) {}
        }
    }

    /** Activity onResume 时调用，重新检查电池优化状态 */
    fun onActivityResumed() {
        if (_uiState.value.phase == SetupPhase.BATTERY_OPTIMIZATION && _uiState.value.batteryRequested) {
            checkBatteryOptimization()
            if (_uiState.value.isBatteryExempt) {
                securePrefs.batteryOptimizationDone = true
            }
        }
    }

    fun skipBatteryOptimization() {
        securePrefs.batteryOptimizationDone = true
    }
}
