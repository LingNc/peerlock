package com.peerlock.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.request.ProcessResult
import com.peerlock.domain.request.RequestEnvelope
import com.peerlock.domain.request.RequestPayload
import com.peerlock.domain.request.RequestProtocol
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TotpCodeInfo(
    val keyType: KeyType,
    val label: String,
    val code: String,
    val remainingSeconds: Int,
)

data class ControllerUiState(
    val totpCodes: List<TotpCodeInfo> = emptyList(),
    val policies: List<RestrictionPolicy> = emptyList(),
    val otpauthUris: List<Pair<String, String>> = emptyList(), // label -> uri
    val isLoading: Boolean = true,
    // 审批流程状态
    val approvalStep: ApprovalStep = ApprovalStep.IDLE,
    val pendingRequest: RequestEnvelope? = null,
    val responseQrCode: String? = null,
    val error: String? = null,
)

enum class ApprovalStep {
    IDLE, SCANNING, REVIEWING, SHOWING_RESPONSE
}

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val storageRepository: StorageRepository,
    private val requestProtocol: RequestProtocol,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

    private val _scanTrigger = MutableStateFlow(0)
    val scanTrigger: StateFlow<Int> = _scanTrigger.asStateFlow()

    init {
        loadPolicies()
        startTotpRefreshLoop()
        loadOtpauthUris()
    }

    private fun loadPolicies() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            _uiState.value = _uiState.value.copy(policies = policies, isLoading = false)
        }
    }

    private fun startTotpRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                refreshTotpCodes()
                delay(1000)
            }
        }
    }

    private suspend fun refreshTotpCodes() {
        val codes = KeyType.entries.mapNotNull { keyType ->
            val seed = seedManager.retrieveSeed(keyType) ?: return@mapNotNull null
            val code = totpEngine.generateCode(seed)
            val step = totpEngine.currentStep()
            val remaining = ((step + 1) * 30 - System.currentTimeMillis() / 1000).toInt()
            TotpCodeInfo(
                keyType = keyType,
                label = keyType.label,
                code = code,
                remainingSeconds = remaining.coerceAtLeast(0),
            )
        }
        _uiState.value = _uiState.value.copy(totpCodes = codes)
    }

    private fun loadOtpauthUris() {
        viewModelScope.launch {
            val uris = KeyType.entries.mapNotNull { keyType ->
                val seed = seedManager.retrieveSeed(keyType) ?: return@mapNotNull null
                val base32Secret = base32Encode(seed)
                val uri = "otpauth://totp/PeerLock:${keyType.label}?secret=$base32Secret&issuer=PeerLock&digits=6&period=30"
                keyType.label to uri
            }
            _uiState.value = _uiState.value.copy(otpauthUris = uris)
        }
    }

    private fun base32Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            val b3 = if (i + 3 < data.size) data[i + 3].toInt() and 0xFF else 0
            val b4 = if (i + 4 < data.size) data[i + 4].toInt() and 0xFF else 0

            result.append(alphabet[(b0 shr 3) and 0x1F])
            result.append(alphabet[((b0 shl 2) or (b1 shr 6)) and 0x1F])
            if (i + 1 < data.size) result.append(alphabet[(b1 shr 1) and 0x1F])
            if (i + 1 < data.size) result.append(alphabet[((b1 shl 4) or (b2 shr 4)) and 0x1F])
            if (i + 2 < data.size) result.append(alphabet[((b2 shl 1) or (b3 shr 7)) and 0x1F])
            if (i + 3 < data.size) result.append(alphabet[(b3 shr 2) and 0x1F])
            if (i + 3 < data.size) result.append(alphabet[((b3 shl 3) or (b4 shr 5)) and 0x1F])
            if (i + 4 < data.size) result.append(alphabet[b4 and 0x1F])

            i += 5
        }
        return result.toString()
    }

    fun requestApprovalScan() {
        _uiState.value = _uiState.value.copy(approvalStep = ApprovalStep.SCANNING)
        _scanTrigger.value++
    }

    fun onQrScanned(scannedData: String) {
        viewModelScope.launch {
            val peerPubKey = securePrefs.peerPublicKey ?: return@launch
            val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)
            when (val result = requestProtocol.processRequest(scannedData, peerPubKeyBytes)) {
                is ProcessResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        approvalStep = ApprovalStep.REVIEWING,
                        pendingRequest = result.envelope,
                    )
                }
                is ProcessResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.reason,
                        approvalStep = ApprovalStep.IDLE,
                    )
                }
            }
        }
    }

    fun approveRequest(duration: Int? = null, durationMode: String? = null) {
        viewModelScope.launch {
            val request = _uiState.value.pendingRequest ?: return@launch
            val qr = when (request.type) {
                "unlock" -> requestProtocol.generateUnlockResponse(
                    request, approved = true, duration = duration, durationMode = durationMode
                )
                "setting" -> requestProtocol.generateConfigResponse(
                    request, approved = true
                )
                else -> null
            }
            if (qr != null) {
                _uiState.value = _uiState.value.copy(
                    approvalStep = ApprovalStep.SHOWING_RESPONSE,
                    responseQrCode = qr,
                )
            } else {
                _uiState.value = _uiState.value.copy(error = "生成响应失败")
            }
        }
    }

    fun rejectRequest(reason: String = "请求被拒绝") {
        viewModelScope.launch {
            val request = _uiState.value.pendingRequest ?: return@launch
            val qr = when (request.type) {
                "unlock" -> requestProtocol.generateUnlockResponse(
                    request, approved = false
                )
                "setting" -> requestProtocol.generateConfigResponse(
                    request, approved = false, rejectReason = reason
                )
                else -> null
            }
            if (qr != null) {
                _uiState.value = _uiState.value.copy(
                    approvalStep = ApprovalStep.SHOWING_RESPONSE,
                    responseQrCode = qr,
                )
            }
        }
    }

    fun resetApprovalFlow() {
        _uiState.value = _uiState.value.copy(
            approvalStep = ApprovalStep.IDLE,
            pendingRequest = null,
            responseQrCode = null,
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
