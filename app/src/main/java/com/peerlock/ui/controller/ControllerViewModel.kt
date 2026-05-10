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
}
