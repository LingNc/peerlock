package com.peerlock.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
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
)

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

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
}
