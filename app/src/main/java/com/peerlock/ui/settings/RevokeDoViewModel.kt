package com.peerlock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RevokeDoUiState(
    val isRevoking: Boolean = false,
    val revoked: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RevokeDoViewModel @Inject constructor(
    private val deviceOwnerManager: DeviceOwnerManager,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RevokeDoUiState())
    val uiState: StateFlow<RevokeDoUiState> = _uiState.asStateFlow()

    fun revokeDeviceOwner() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRevoking = true, error = null)
            try {
                val success = deviceOwnerManager.removeDeviceOwner()
                if (success) {
                    securePrefs.isDeviceOwner = false
                    _uiState.value = _uiState.value.copy(isRevoking = false, revoked = true)
                } else {
                    _uiState.value = _uiState.value.copy(isRevoking = false, error = "取消失败，请重试")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRevoking = false, error = "取消失败: ${e.message}")
            }
        }
    }
}
