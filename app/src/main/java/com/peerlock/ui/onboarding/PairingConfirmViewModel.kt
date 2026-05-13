package com.peerlock.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

data class PairingConfirmUiState(
    val sessionId: String = "",
    val peerDeviceName: String = "",
    val fingerprint: String = "",
    val pairingTime: String = "",
    val cooldownSeconds: Int = 5,
    val isCooldownActive: Boolean = true,
    val isConfirmed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PairingConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pairingSessionDao: PairingSessionDao,
    private val securePrefs: SecurePrefs,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    val role: String = savedStateHandle["role"] ?: "controlled"

    private val _uiState = MutableStateFlow(PairingConfirmUiState())
    val uiState: StateFlow<PairingConfirmUiState> = _uiState.asStateFlow()

    init {
        loadSessionInfo()
        startCooldown()
    }

    private fun loadSessionInfo() {
        val sessionId = securePrefs.sessionId ?: ""
        val peerKey = securePrefs.peerPublicKey ?: ""
        val fingerprint = computeFingerprint(peerKey)

        _uiState.value = PairingConfirmUiState(
            sessionId = sessionId.take(8) + if (sessionId.length > 8) "..." else "",
            peerDeviceName = "对方设备",
            fingerprint = fingerprint,
            pairingTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()),
        )
    }

    private fun startCooldown() {
        viewModelScope.launch {
            for (i in 5 downTo 0) {
                _uiState.value = _uiState.value.copy(cooldownSeconds = i, isCooldownActive = i > 0)
                if (i > 0) delay(1000)
            }
        }
    }

    fun confirmPairing() {
        if (_uiState.value.isCooldownActive) return
        securePrefs.isPaired = true
        securePrefs.role = role
        _uiState.value = _uiState.value.copy(isConfirmed = true)
        viewModelScope.launch {
            storageRepository.insertAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    action = "PAIRING_COMPLETE",
                    targetPackage = null,
                    detail = "配对确认完成，角色: $role",
                )
            )
        }
    }

    private fun computeFingerprint(publicKeyBase64: String): String {
        if (publicKeyBase64.isBlank()) return "--------"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBase64.toByteArray())
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
