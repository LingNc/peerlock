package com.peerlock.ui.controlled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.request.DeviceInfo
import com.peerlock.domain.request.RequestProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlledUiState(
    val policies: List<RestrictionPolicy> = emptyList(),
    val todayScreenTimeMs: Long = 0L,
    val isLoading: Boolean = true,
    val requestQrCode: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ControlledViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val requestProtocol: RequestProtocol,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlledUiState())
    val uiState: StateFlow<ControlledUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            _uiState.value = _uiState.value.copy(
                policies = policies,
                isLoading = false,
            )
        }
    }

    fun generateUnlockRequest(targetPackage: String, durationMinutes: Int) {
        viewModelScope.launch {
            val sessionId = securePrefs.sessionId ?: return@launch
            val result = requestProtocol.generateUnlockRequest(
                sessionId = sessionId,
                targetPackage = targetPackage,
                requestedDuration = durationMinutes,
                durationMode = "cumulative",
                deviceInfo = DeviceInfo(
                    todayScreenTimeMs = _uiState.value.todayScreenTimeMs,
                    suspendedApps = _uiState.value.policies.map { it.targetPackage },
                    isInSafeMode = false,
                ),
            )
            if (result != null) {
                _uiState.value = _uiState.value.copy(requestQrCode = result)
            } else {
                _uiState.value = _uiState.value.copy(error = "生成请求失败（频率限制或密钥缺失）")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
