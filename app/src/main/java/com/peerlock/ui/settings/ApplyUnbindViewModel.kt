package com.peerlock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.repository.AuditLog
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

data class ApplyUnbindUiState(
    val step: UnbindStep = UnbindStep.WARNING,
    val cooldownSeconds: Int = 5,
    val isCooldownActive: Boolean = true,
    val error: String? = null,
    val isLoading: Boolean = false,
    val unbindComplete: Boolean = false,
)

enum class UnbindStep { WARNING, INPUT_CODE, UNBOUND }

@HiltViewModel
class ApplyUnbindViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val seedManager: SeedManager,
    private val totpEngine: TotpEngine,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApplyUnbindUiState())
    val uiState: StateFlow<ApplyUnbindUiState> = _uiState.asStateFlow()

    init {
        startCooldown()
    }

    private fun startCooldown() {
        viewModelScope.launch {
            for (i in 5 downTo 0) {
                _uiState.value = _uiState.value.copy(cooldownSeconds = i, isCooldownActive = i > 0)
                if (i > 0) delay(1000)
            }
        }
    }

    fun proceedToCodeInput() {
        if (_uiState.value.isCooldownActive) return
        _uiState.value = _uiState.value.copy(step = UnbindStep.INPUT_CODE)
    }

    fun verifyTerminateCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val seed = seedManager.retrieveSeed(KeyType.DESTROY)
            if (seed == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "密钥缺失，无法验证终止码")
                return@launch
            }
            val valid = totpEngine.verifyCode(seed, code, tolerance = 1)
            if (valid) {
                executeUnbind()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "终止码错误")
            }
        }
    }

    private suspend fun executeUnbind() {
        // 清除加密材料
        securePrefs.clearPairingData()
        seedManager.clearSeeds()

        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "UNBIND",
                targetPackage = null,
                detail = "被控端申请解除配对: 终止码验证通过",
            )
        )

        _uiState.value = _uiState.value.copy(
            step = UnbindStep.UNBOUND,
            isLoading = false,
            unbindComplete = true,
        )
    }
}
