package com.peerlock.ui.controlled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.request.RequestProtocol
import com.peerlock.domain.request.ResponseEnvelope
import com.peerlock.domain.request.ResponsePayload
import com.peerlock.domain.request.ResponseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiveCommandUiState(
    val step: ReceiveStep = ReceiveStep.IDLE,
    val commandType: CommandType? = null,
    val unlockTarget: String? = null,
    val unlockDuration: Int? = null,
    val policyChanges: List<String> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = false,
)

enum class ReceiveStep { IDLE, REVIEWING, EXECUTED }
enum class CommandType { UNLOCK, POLICY, STATS }

@HiltViewModel
class ReceiveCommandViewModel @Inject constructor(
    private val requestProtocol: RequestProtocol,
    private val policyEngine: PolicyEngine,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveCommandUiState())
    val uiState: StateFlow<ReceiveCommandUiState> = _uiState.asStateFlow()

    private val _scanTrigger = MutableStateFlow(0)
    val scanTrigger: StateFlow<Int> = _scanTrigger.asStateFlow()

    fun requestScan() {
        _scanTrigger.value++
    }

    fun onCommandScanned(data: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val peerPubKey = securePrefs.peerPublicKey ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "未找到对方公钥")
                return@launch
            }
            val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

            when (val result = requestProtocol.processResponse(data, peerPubKeyBytes)) {
                is ResponseResult.Success -> {
                    val envelope = result.envelope
                    when (val payload = envelope.payload) {
                        is ResponsePayload.UnlockResponse -> {
                            _uiState.value = _uiState.value.copy(
                                step = ReceiveStep.REVIEWING,
                                commandType = CommandType.UNLOCK,
                                unlockTarget = payload.targetPackage,
                                unlockDuration = payload.duration,
                                isLoading = false,
                            )
                        }
                        is ResponsePayload.ConfigResponse -> {
                            _uiState.value = _uiState.value.copy(
                                step = ReceiveStep.REVIEWING,
                                commandType = CommandType.POLICY,
                                policyChanges = payload.changes.map { "${it.targetPackage}: ${it.dailyLimitMinutes ?: "?"}分" },
                                isLoading = false,
                            )
                        }
                        is ResponsePayload.Rejected -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = payload.reason ?: "请求被拒绝",
                            )
                        }
                    }
                }
                is ResponseResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.reason)
                }
            }
        }
    }

    fun confirmExecute() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // TODO: 从缓存中获取 response 并执行
            _uiState.value = _uiState.value.copy(step = ReceiveStep.EXECUTED, isLoading = false)
        }
    }

    fun reject() {
        _uiState.value = ReceiveCommandUiState()
    }

    fun reset() {
        _uiState.value = ReceiveCommandUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
