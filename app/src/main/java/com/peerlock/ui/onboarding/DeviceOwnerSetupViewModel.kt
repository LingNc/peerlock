package com.peerlock.ui.onboarding

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.system.adb.AdbPairingService
import com.peerlock.system.adb.AdbPairingState
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val checkTimestamp: Long = 0L,
    // 一键设置状态（来自 AdbPairingService）
    val autoSetupState: AdbPairingState = AdbPairingState.IDLE,
    val autoSetupMessage: String? = null,
)

@HiltViewModel
class DeviceOwnerSetupViewModel @Inject constructor(
    private val deviceOwnerManager: DeviceOwnerManager,
    private val securePrefs: SecurePrefs,
    private val application: Application,
) : ViewModel() {

    companion object {
        private const val TAG = "DOSetupVM"
    }

    private val _uiState = MutableStateFlow(DeviceOwnerSetupUiState())
    val uiState: StateFlow<DeviceOwnerSetupUiState> = _uiState.asStateFlow()

    private val powerManager = application.getSystemService(PowerManager::class.java)

    val adbCommand: String
        get() = "adb shell dpm set-device-owner ${application.packageName}/.system.deviceadmin.PeerLockDeviceAdminReceiver"

    val supportsWirelessAdb: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    init {
        checkDeviceOwnerStatus()
        checkBatteryOptimization()
        // 观察 AdbPairingService 状态
        viewModelScope.launch {
            AdbPairingService.state.collect { state ->
                _uiState.value = _uiState.value.copy(autoSetupState = state)
            }
        }
        viewModelScope.launch {
            AdbPairingService.message.collect { msg ->
                _uiState.value = _uiState.value.copy(autoSetupMessage = msg)
                // 配对成功后刷新 DO 状态
                if (_uiState.value.autoSetupState == AdbPairingState.SUCCESS) {
                    checkDeviceOwnerStatus()
                }
            }
        }
    }

    fun checkDeviceOwnerStatus() {
        _uiState.value = _uiState.value.copy(
            statusMessage = "正在检查...",
            checkTimestamp = System.currentTimeMillis(),
        )
        val isOwner = deviceOwnerManager.isDeviceOwner()
        _uiState.value = _uiState.value.copy(
            isDeviceOwner = isOwner,
            isChecked = true,
            statusMessage = if (isOwner) "Device Owner 已设置" else "Device Owner 未设置",
            checkTimestamp = System.currentTimeMillis(),
        )
    }

    fun toggleWirelessGuide() {
        _uiState.value = _uiState.value.copy(showWirelessGuide = !_uiState.value.showWirelessGuide)
    }

    fun checkBatteryOptimization() {
        val exempt = powerManager.isIgnoringBatteryOptimizations(application.packageName)
        _uiState.value = _uiState.value.copy(isBatteryExempt = exempt)
    }

    /**
     * DO 步骤完成后的处理。
     * @return true 如果已豁免电池优化（调用方应直接导航），false 如果需要进入电池优化步骤
     */
    fun advanceToDoComplete(): Boolean {
        checkBatteryOptimization()
        val batteryExempt = _uiState.value.isBatteryExempt
        if (batteryExempt) {
            securePrefs.batteryOptimizationDone = true
            return true
        }
        _uiState.value = _uiState.value.copy(phase = SetupPhase.BATTERY_OPTIMIZATION)
        return false
    }

    fun requestBatteryExemption() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${application.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(intent)
            _uiState.value = _uiState.value.copy(batteryRequested = true)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                application.startActivity(intent)
                _uiState.value = _uiState.value.copy(batteryRequested = true)
            } catch (_: Exception) {}
        }
    }

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

    // === 一键设置 Device Owner（通过 AdbPairingService）===

    fun startAutoSetup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        AdbPairingService.start(application)
    }

    fun stopAutoSetup() {
        AdbPairingService.stop(application)
        _uiState.value = _uiState.value.copy(
            autoSetupState = AdbPairingState.IDLE,
            autoSetupMessage = null,
        )
    }
}
