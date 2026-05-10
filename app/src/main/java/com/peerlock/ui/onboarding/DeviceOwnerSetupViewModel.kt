package com.peerlock.ui.onboarding

import android.app.Application
import androidx.lifecycle.ViewModel
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DeviceOwnerSetupUiState(
    val isDeviceOwner: Boolean = false,
    val isChecked: Boolean = false,
)

@HiltViewModel
class DeviceOwnerSetupViewModel @Inject constructor(
    private val deviceOwnerManager: DeviceOwnerManager,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceOwnerSetupUiState())
    val uiState: StateFlow<DeviceOwnerSetupUiState> = _uiState.asStateFlow()

    val adbCommand: String
        get() = "adb shell dpm set-device-owner ${application.packageName}/.system.deviceadmin.PeerLockDeviceAdminReceiver"

    fun checkDeviceOwnerStatus() {
        val isOwner = deviceOwnerManager.isDeviceOwner()
        _uiState.value = _uiState.value.copy(isDeviceOwner = isOwner, isChecked = true)
    }
}
