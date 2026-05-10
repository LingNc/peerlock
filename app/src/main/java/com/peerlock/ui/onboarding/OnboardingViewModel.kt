package com.peerlock.ui.onboarding

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.domain.pairing.PairingProtocol
import com.peerlock.domain.pairing.PairingRequest
import com.peerlock.domain.pairing.PairingResponse
import com.peerlock.domain.pairing.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ROLE_SELECTION,
    val role: String = "",
    val pairRequest: PairingRequest? = null,
    val pairRequestQr: String = "",
    val pairResponseQr: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
)

enum class OnboardingStep {
    ROLE_SELECTION,
    SHOW_MY_QR,
    SCAN_PEER_QR,
    COMPLETED,
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val pairingProtocol: PairingProtocol,
) : ViewModel() {

    private val deviceName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _scanTrigger = MutableStateFlow(0)
    val scanTrigger: StateFlow<Int> = _scanTrigger.asStateFlow()

    fun requestScan() {
        _scanTrigger.value++
    }

    fun selectRole(role: String) {
        _uiState.value = _uiState.value.copy(role = role, step = OnboardingStep.SHOW_MY_QR)
        if (role == "controlled") {
            generatePairRequest()
        }
    }

    private fun generatePairRequest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val request = pairingProtocol.generatePairRequest(deviceName)
                _uiState.value = _uiState.value.copy(
                    pairRequest = request,
                    pairRequestQr = request.pub,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "生成配对请求失败: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    fun onQrScanned(scannedData: String) {
        val state = _uiState.value
        when (state.role) {
            "controller" -> handleControllerScan(scannedData)
            "controlled" -> handleControlledScan(scannedData)
        }
    }

    private fun handleControllerScan(scannedPubKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val request = PairingRequest(
                    id = "",
                    pub = scannedPubKey,
                    name = deviceName,
                )
                val response = pairingProtocol.processPairRequest(request, deviceName)
                _uiState.value = _uiState.value.copy(
                    pairResponseQr = response.data,
                    step = OnboardingStep.SCAN_PEER_QR,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "处理配对请求失败: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    private fun handleControlledScan(scannedResponse: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = PairingResponse(
                    pub = "",
                    signPub = "",
                    data = scannedResponse,
                )
                val result = pairingProtocol.processPairResponse(response)
                when (result) {
                    is PairingResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            step = OnboardingStep.COMPLETED,
                            isLoading = false,
                        )
                    }
                    is PairingResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = result.reason,
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "配对失败: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
