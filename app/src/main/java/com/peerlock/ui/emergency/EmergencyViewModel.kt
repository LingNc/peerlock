package com.peerlock.ui.emergency

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.domain.emergency.EmergencyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyUiState(
    val destroyCode: String = "",
    val destroyVerified: Boolean = false,
    val destroyExecuted: Boolean = false,
    val isLocked: Boolean = false,
    val error: String? = null,
    // L2
    val l2ChallengeCode: String? = null,
    val l2ChallengeVerified: Boolean = false,
    val l2ChallengeInput: String = "",
    val adbCommand: String? = null,
    val l2Executed: Boolean = false,
)

@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val emergencyManager: EmergencyManager,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    fun updateDestroyCode(code: String) {
        _uiState.value = _uiState.value.copy(destroyCode = code, error = null)
    }

    fun verifyDestroyCode() {
        viewModelScope.launch {
            val code = _uiState.value.destroyCode
            if (code.length != 6) {
                _uiState.value = _uiState.value.copy(error = "请输入6位终止码")
                return@launch
            }
            val valid = emergencyManager.verifyDestroyCode(code)
            if (valid) {
                _uiState.value = _uiState.value.copy(destroyVerified = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = "终止码无效或已锁定")
            }
        }
    }

    fun executeDestroy() {
        viewModelScope.launch {
            val success = emergencyManager.executeDestroy()
            _uiState.value = _uiState.value.copy(destroyExecuted = success)
        }
    }

    fun generateAdbCommand() {
        // 先生成 4 位 hex 挑战码
        val challengeBytes = ByteArray(2)
        java.security.SecureRandom().nextBytes(challengeBytes)
        val challenge = challengeBytes.joinToString("") { "%02X".format(it) }
        _uiState.value = _uiState.value.copy(l2ChallengeCode = challenge, l2ChallengeVerified = false, l2ChallengeInput = "")
    }

    fun updateL2ChallengeInput(input: String) {
        _uiState.value = _uiState.value.copy(l2ChallengeInput = input.uppercase(), error = null)
    }

    fun verifyL2Challenge() {
        val expected = _uiState.value.l2ChallengeCode
        val input = _uiState.value.l2ChallengeInput
        if (input == expected) {
            val nonce = emergencyManager.generateNonce()
            val pkg = application.packageName
            val cmd = "adb shell am broadcast -a com.peerlock.ACTION_EMERGENCY --es unlock_nonce \"$nonce\" -p $pkg"
            _uiState.value = _uiState.value.copy(l2ChallengeVerified = true, adbCommand = cmd)
        } else {
            _uiState.value = _uiState.value.copy(error = "挑战码错误")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
