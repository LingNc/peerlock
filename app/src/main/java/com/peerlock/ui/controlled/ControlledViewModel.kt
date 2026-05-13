package com.peerlock.ui.controlled

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.request.DeviceInfo
import com.peerlock.domain.request.InstalledApp
import com.peerlock.domain.request.RequestProtocol
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlledUiState(
    val policies: List<RestrictionPolicy> = emptyList(),
    val todayScreenTimeMs: Long = 0L,
    val isLoading: Boolean = true,
    val requestQrCode: String? = null,
    val quickCodeResult: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ControlledViewModel @Inject constructor(
    private val application: Application,
    private val storageRepository: StorageRepository,
    private val requestProtocol: RequestProtocol,
    private val securePrefs: SecurePrefs,
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val policyEngine: PolicyEngine,
    private val usageStatsCollector: UsageStatsCollector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlledUiState())
    val uiState: StateFlow<ControlledUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            val todayStart = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            val stats = usageStatsCollector.queryUsageStats(todayStart, now)
            val screenTimeMs = stats.sumOf { it.totalTimeMs }
            _uiState.value = _uiState.value.copy(
                policies = policies,
                todayScreenTimeMs = screenTimeMs,
                isLoading = false,
            )
        }
    }

    fun generateUnlockRequest(targetPackage: String, durationMinutes: Int) {
        viewModelScope.launch {
            val sessionId = securePrefs.sessionId ?: return@launch
            val result = requestProtocol.generateUnlockRequest(
                sessionId = sessionId,
                targetPackage = targetPackage,
                requestedDuration = durationMinutes,
                durationMode = "cumulative",
                deviceInfo = DeviceInfo(
                    todayScreenTimeMs = _uiState.value.todayScreenTimeMs,
                    suspendedApps = _uiState.value.policies.map { it.targetPackage },
                    isInSafeMode = false,
                    installedApps = getInstalledApps(),
                ),
            )
            if (result != null) {
                _uiState.value = _uiState.value.copy(requestQrCode = result)
                storageRepository.insertAuditLog(
                    AuditLog(
                        timestamp = System.currentTimeMillis(),
                        action = "UNLOCK_REQUEST",
                        targetPackage = targetPackage,
                        detail = "生成解锁请求: ${durationMinutes}分钟",
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(error = "生成请求失败（频率限制或密钥缺失）")
            }
        }
    }

    fun verifyQuickCode(code: String, targetPackage: String?, durationMinutes: Int) {
        viewModelScope.launch {
            val seed = seedManager.retrieveSeed(KeyType.UNLOCK)
            if (seed == null) {
                _uiState.value = _uiState.value.copy(error = "密钥缺失")
                return@launch
            }
            val valid = totpEngine.verifyCode(seed, code, tolerance = 1)
            if (valid) {
                val packages = if (targetPackage.isNullOrBlank()) {
                    _uiState.value.policies.map { it.targetPackage }
                } else {
                    listOf(targetPackage)
                }
                for (pkg in packages) {
                    policyEngine.recordUnlock(pkg, durationMinutes)
                    policyEngine.unsuspendApp(pkg)
                }
                val desc = if (packages.size == 1) packages[0] else "全部受限应用"
                _uiState.value = _uiState.value.copy(
                    quickCodeResult = "已解锁 $desc，${durationMinutes} 分钟后自动暂停",
                )
                storageRepository.insertAuditLog(
                    AuditLog(
                        timestamp = System.currentTimeMillis(),
                        action = "UNLOCK_QUICK_CODE",
                        targetPackage = desc,
                        detail = "快速码解锁: ${durationMinutes}分钟",
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(error = "验证码错误")
            }
        }
    }

    fun clearQuickCodeResult() {
        _uiState.value = _uiState.value.copy(quickCodeResult = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun getInstalledApps(): List<InstalledApp> {
        val pm = application.packageManager
        val selfPackage = application.packageName
        return pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { it.packageName != selfPackage }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.appName }
    }
}
