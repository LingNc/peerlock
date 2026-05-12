package com.peerlock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.db.entity.PairingSessionEntity
import com.peerlock.data.prefs.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PairingInfoUiState(
    val role: String = "",
    val peerDeviceName: String = "未知设备",
    val fingerprint: String = "--------",
    val pairingTime: String = "",
    val sessionId: String = "",
    val status: String = "ACTIVE",
    val historySessions: List<HistorySession> = emptyList(),
    val isLoading: Boolean = true,
)

data class HistorySession(
    val sessionId: String,
    val peerDeviceName: String,
    val status: String,
    val createdAt: Long,
)

@HiltViewModel
class PairingInfoViewModel @Inject constructor(
    private val pairingSessionDao: PairingSessionDao,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingInfoUiState())
    val uiState: StateFlow<PairingInfoUiState> = _uiState.asStateFlow()

    init {
        loadPairingInfo()
    }

    private fun loadPairingInfo() {
        viewModelScope.launch {
            val role = securePrefs.role ?: ""
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            // Try Room first
            val activeSessions = pairingSessionDao.getNonArchived()
            if (activeSessions.isNotEmpty()) {
                val current = activeSessions.firstOrNull { it.status == "ACTIVE" }
                    ?: activeSessions.first()
                val history = activeSessions.filter { it != current }.map {
                    HistorySession(it.sessionId, it.peerDeviceName, it.status, it.createdAt)
                }
                _uiState.value = PairingInfoUiState(
                    role = role,
                    peerDeviceName = current.peerDeviceName,
                    fingerprint = current.identityFingerprint,
                    pairingTime = dateFormat.format(Date(current.createdAt)),
                    sessionId = current.sessionId.take(8) + if (current.sessionId.length > 8) "..." else "",
                    status = current.status,
                    historySessions = history,
                    isLoading = false,
                )
            } else {
                // Fallback to SecurePrefs
                val sessionId = securePrefs.sessionId ?: ""
                val peerKey = securePrefs.peerPublicKey ?: ""
                _uiState.value = PairingInfoUiState(
                    role = role,
                    peerDeviceName = "对方设备",
                    fingerprint = computeFingerprint(peerKey),
                    pairingTime = dateFormat.format(Date()),
                    sessionId = sessionId.take(8) + if (sessionId.length > 8) "..." else "",
                    status = if (securePrefs.isPaired) "ACTIVE" else "未配对",
                    isLoading = false,
                )
            }
        }
    }

    private fun computeFingerprint(publicKeyBase64: String): String {
        if (publicKeyBase64.isBlank()) return "--------"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBase64.toByteArray())
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
