package com.peerlock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.crypto.CryptoEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

data class IdentityInfoUiState(
    val fingerprint: String = "--------",
    val curveType: String = "",
    val deviceName: String = "",
    val canReset: Boolean = false,
    val resetCooldownSeconds: Int = 10,
    val isResetCooldownActive: Boolean = false,
    val resetComplete: Boolean = false,
)

@HiltViewModel
class IdentityInfoViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val seedManager: SeedManager,
    private val pairingSessionDao: PairingSessionDao,
    private val cryptoEngine: CryptoEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentityInfoUiState())
    val uiState: StateFlow<IdentityInfoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadIdentityInfo() }
    }

    private suspend fun loadIdentityInfo() {
        try {
            val pubKeyBase64 = securePrefs.myPublicKey
            val fingerprint = computeFingerprint(pubKeyBase64)
            val sessions = try { pairingSessionDao.getNonArchived() } catch (_: Exception) { emptyList() }
            _uiState.value = IdentityInfoUiState(
                fingerprint = fingerprint,
                curveType = cryptoEngine.curveName,
                deviceName = android.os.Build.MODEL,
                canReset = sessions.none { it.status == "ACTIVE" },
            )
        } catch (e: Exception) {
            _uiState.value = IdentityInfoUiState(
                fingerprint = "--------",
                curveType = cryptoEngine.curveName,
                deviceName = android.os.Build.MODEL,
            )
        }
    }

    fun requestReset() {
        _uiState.value = _uiState.value.copy(isResetCooldownActive = true)
        viewModelScope.launch {
            for (i in 10 downTo 0) {
                _uiState.value = _uiState.value.copy(resetCooldownSeconds = i, isResetCooldownActive = i > 0)
                if (i > 0) delay(1000)
            }
        }
    }

    fun confirmReset() {
        if (_uiState.value.isResetCooldownActive) return
        viewModelScope.launch {
            seedManager.clearSeeds()
            securePrefs.clearPairingData()
            _uiState.value = _uiState.value.copy(resetComplete = true)
        }
    }

    fun cancelReset() {
        _uiState.value = _uiState.value.copy(isResetCooldownActive = false)
    }

    private fun computeFingerprint(publicKeyBase64: String?): String {
        if (publicKeyBase64.isNullOrBlank()) return "--------"
        val keyBytes = try {
            Base64.getDecoder().decode(publicKeyBase64)
        } catch (_: Exception) {
            return "--------"
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
