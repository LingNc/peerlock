package com.peerlock.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.db.entity.PairingSessionEntity
import com.peerlock.data.pairing.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceSelectUiState(
    val devices: List<PairingSessionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val showReceiptQr: Boolean = false,
    val receiptQrData: String? = null,
)

@HiltViewModel
class DeviceSelectViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceSelectUiState())
    val uiState: StateFlow<DeviceSelectUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            val devices = pairingRepository.getNonArchived()
            _uiState.value = _uiState.value.copy(devices = devices, isLoading = false)
        }
    }

    fun showReceipt(device: PairingSessionEntity) {
        // TODO: 生成回执 QR 数据（需要种子签名等）
        _uiState.value = _uiState.value.copy(
            showReceiptQr = true,
            receiptQrData = "session:${device.sessionId}",
        )
    }

    fun dismissReceipt() {
        _uiState.value = _uiState.value.copy(showReceiptQr = false, receiptQrData = null)
    }
}
