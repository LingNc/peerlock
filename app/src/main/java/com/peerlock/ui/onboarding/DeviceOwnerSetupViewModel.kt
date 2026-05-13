package com.peerlock.ui.onboarding

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.system.adb.AdbClient
import com.peerlock.system.adb.AdbKey
import com.peerlock.system.adb.AdbMdns
import com.peerlock.system.adb.AdbPairingClient
import com.peerlock.system.adb.PreferenceAdbKeyStore
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SetupPhase { DO_SETUP, BATTERY_OPTIMIZATION }

enum class AutoSetupState {
    IDLE,           // 未开始
    DISCOVERING,    // mDNS 搜索中
    FOUND,          // 已发现配对服务，等待用户输入配对码
    PAIRING,        // SPAKE2 配对中
    PAIRED,         // 配对成功，连接 ADB 执行 DO 命令中
    SUCCESS,        // DO 设置成功
    ERROR,          // 出错
}

data class DeviceOwnerSetupUiState(
    val phase: SetupPhase = SetupPhase.DO_SETUP,
    val isDeviceOwner: Boolean = false,
    val isChecked: Boolean = false,
    val showWirelessGuide: Boolean = false,
    val isBatteryExempt: Boolean = false,
    val batteryRequested: Boolean = false,
    val statusMessage: String? = null,
    // 一键设置状态
    val autoSetupState: AutoSetupState = AutoSetupState.IDLE,
    val autoSetupMessage: String? = null,
    val pairingPort: Int = 0,
    val connectPort: Int = 0,
    val showPairingCodeDialog: Boolean = false,
)

@HiltViewModel
class DeviceOwnerSetupViewModel @Inject constructor(
    private val deviceOwnerManager: DeviceOwnerManager,
    private val securePrefs: SecurePrefs,
    private val application: Application,
) : ViewModel() {

    companion object {
        private const val TAG = "DOSetupVM"
    }

    private val _uiState = MutableStateFlow(DeviceOwnerSetupUiState())
    val uiState: StateFlow<DeviceOwnerSetupUiState> = _uiState.asStateFlow()

    private val powerManager = application.getSystemService(PowerManager::class.java)

    private var pairingMdns: AdbMdns? = null
    private var connectMdns: AdbMdns? = null

    val adbCommand: String
        get() = "adb shell dpm set-device-owner ${application.packageName}/.system.deviceadmin.PeerLockDeviceAdminReceiver"

    val supportsWirelessAdb: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    init {
        checkDeviceOwnerStatus()
        checkBatteryOptimization()
    }

    fun checkDeviceOwnerStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = "正在检查...")
        val isOwner = deviceOwnerManager.isDeviceOwner()
        _uiState.value = _uiState.value.copy(
            isDeviceOwner = isOwner,
            isChecked = true,
            statusMessage = if (isOwner) "Device Owner 已设置" else "Device Owner 未设置",
        )
    }

    fun toggleWirelessGuide() {
        _uiState.value = _uiState.value.copy(showWirelessGuide = !_uiState.value.showWirelessGuide)
    }

    fun checkBatteryOptimization() {
        val exempt = powerManager.isIgnoringBatteryOptimizations(application.packageName)
        _uiState.value = _uiState.value.copy(isBatteryExempt = exempt)
    }

    fun advanceToDoComplete() {
        checkBatteryOptimization()
        val batteryExempt = _uiState.value.isBatteryExempt
        if (batteryExempt) {
            securePrefs.batteryOptimizationDone = true
        } else {
            _uiState.value = _uiState.value.copy(phase = SetupPhase.BATTERY_OPTIMIZATION)
        }
    }

    fun requestBatteryExemption() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${application.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(intent)
            _uiState.value = _uiState.value.copy(batteryRequested = true)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                application.startActivity(intent)
                _uiState.value = _uiState.value.copy(batteryRequested = true)
            } catch (_: Exception) {}
        }
    }

    fun onActivityResumed() {
        if (_uiState.value.phase == SetupPhase.BATTERY_OPTIMIZATION && _uiState.value.batteryRequested) {
            checkBatteryOptimization()
            if (_uiState.value.isBatteryExempt) {
                securePrefs.batteryOptimizationDone = true
            }
        }
    }

    fun skipBatteryOptimization() {
        securePrefs.batteryOptimizationDone = true
    }

    // === 一键设置 Device Owner ===

    fun startAutoSetup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        _uiState.value = _uiState.value.copy(
            autoSetupState = AutoSetupState.DISCOVERING,
            autoSetupMessage = "正在搜索 ADB 配对服务...",
        )
        pairingMdns?.stop()
        pairingMdns = AdbMdns(application, AdbMdns.TLS_PAIRING) { port ->
            Log.i(TAG, "Pairing service found on port $port")
            _uiState.value = _uiState.value.copy(
                autoSetupState = AutoSetupState.FOUND,
                autoSetupMessage = "已发现配对服务（端口 $port）",
                pairingPort = port,
                showPairingCodeDialog = true,
            )
        }.also { it.start() }

        // 同时搜索 ADB 连接端口
        connectMdns?.stop()
        connectMdns = AdbMdns(application, AdbMdns.TLS_CONNECT) { port ->
            Log.i(TAG, "ADB connect service found on port $port")
            _uiState.value = _uiState.value.copy(connectPort = port)
        }.also { it.start() }
    }

    fun stopAutoSetup() {
        pairingMdns?.stop()
        connectMdns?.stop()
        pairingMdns = null
        connectMdns = null
        _uiState.value = _uiState.value.copy(
            autoSetupState = AutoSetupState.IDLE,
            autoSetupMessage = null,
            showPairingCodeDialog = false,
        )
    }

    fun dismissPairingCodeDialog() {
        _uiState.value = _uiState.value.copy(showPairingCodeDialog = false)
    }

    fun onPairingCodeEntered(code: String) {
        _uiState.value = _uiState.value.copy(
            showPairingCodeDialog = false,
            autoSetupState = AutoSetupState.PAIRING,
            autoSetupMessage = "正在配对...",
        )

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { performPairing(code) }
                if (result) {
                    _uiState.value = _uiState.value.copy(
                        autoSetupState = AutoSetupState.PAIRED,
                        autoSetupMessage = "配对成功，正在设置 Device Owner...",
                    )
                    val doResult = withContext(Dispatchers.IO) { executeDeviceOwnerCommand() }
                    if (doResult) {
                        pairingMdns?.stop()
                        connectMdns?.stop()
                        _uiState.value = _uiState.value.copy(
                            autoSetupState = AutoSetupState.SUCCESS,
                            autoSetupMessage = "Device Owner 设置成功！",
                        )
                        checkDeviceOwnerStatus()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            autoSetupState = AutoSetupState.ERROR,
                            autoSetupMessage = "DO 命令执行失败，请检查设备是否已添加账户",
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        autoSetupState = AutoSetupState.ERROR,
                        autoSetupMessage = "配对失败，请检查配对码是否正确",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto setup failed", e)
                _uiState.value = _uiState.value.copy(
                    autoSetupState = AutoSetupState.ERROR,
                    autoSetupMessage = "错误: ${e.message}",
                )
            }
        }
    }

    private fun performPairing(pairCode: String): Boolean {
        val port = _uiState.value.pairingPort
        if (port <= 0) return false

        val keyStore = PreferenceAdbKeyStore(
            application.getSharedPreferences("peerlock_adb", android.content.Context.MODE_PRIVATE),
        )
        val key = AdbKey(keyStore, "peerlock")

        AdbPairingClient("127.0.0.1", port, pairCode, key).use { client ->
            return client.start()
        }
    }

    private fun executeDeviceOwnerCommand(): Boolean {
        // 先尝试连接已发现的端口
        val connectPort = _uiState.value.connectPort
        if (connectPort > 0) {
            return executeOnPort(connectPort)
        }

        // 如果没有通过 mDNS 发现，尝试常见端口
        for (port in listOf(5555, 37217, 43221)) {
            if (executeOnPort(port)) return true
        }
        return false
    }

    private fun executeOnPort(port: Int): Boolean {
        return try {
            val keyStore = PreferenceAdbKeyStore(
                application.getSharedPreferences("peerlock_adb", android.content.Context.MODE_PRIVATE),
            )
            val key = AdbKey(keyStore, "peerlock")

            AdbClient("127.0.0.1", port, key).use { client ->
                client.connect()
                val result = client.shellCommand(
                    "dpm set-device-owner ${application.packageName}/.system.deviceadmin.PeerLockDeviceAdminReceiver",
                )
                Log.i(TAG, "DO command result: $result")
                result.contains("Success") || deviceOwnerManager.isDeviceOwner()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed on port $port: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        pairingMdns?.stop()
        connectMdns?.stop()
    }
}
