package com.peerlock.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.request.RequestProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnlockAppUiState(
    val policies: List<RestrictionPolicy> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val durationMinutes: Int = 5,
    val generatedQr: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class UnlockAppViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val requestProtocol: RequestProtocol,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockAppUiState())
    val uiState: StateFlow<UnlockAppUiState> = _uiState.asStateFlow()

    init {
        loadPolicies()
    }

    private fun loadPolicies() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            _uiState.value = _uiState.value.copy(policies = policies)
        }
    }

    fun togglePackage(pkg: String) {
        val current = _uiState.value.selectedPackages
        _uiState.value = _uiState.value.copy(
            selectedPackages = if (pkg in current) current - pkg else current + pkg
        )
    }

    fun setDuration(minutes: Int) {
        _uiState.value = _uiState.value.copy(durationMinutes = minutes)
    }

    fun generateUnlockInstruction() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.selectedPackages.isEmpty()) {
                _uiState.value = state.copy(error = "请选择至少一个应用")
                return@launch
            }
            _uiState.value = state.copy(isLoading = true, error = null)

            val sessionId = securePrefs.sessionId ?: run {
                _uiState.value = state.copy(isLoading = false, error = "未找到会话 ID")
                return@launch
            }

            // 为每个选中的应用生成解锁请求
            val qr = requestProtocol.generateUnlockRequest(
                sessionId = sessionId,
                targetPackage = state.selectedPackages.first(),
                requestedDuration = state.durationMinutes,
                durationMode = "cumulative",
                deviceInfo = com.peerlock.domain.request.DeviceInfo(
                    todayScreenTimeMs = 0,
                    suspendedApps = emptyList(),
                    isInSafeMode = false,
                ),
            )
            if (qr != null) {
                _uiState.value = state.copy(isLoading = false, generatedQr = qr)
            } else {
                _uiState.value = state.copy(isLoading = false, error = "生成解锁指令失败")
            }
        }
    }

    fun reset() {
        _uiState.value = UnlockAppUiState()
        loadPolicies()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
