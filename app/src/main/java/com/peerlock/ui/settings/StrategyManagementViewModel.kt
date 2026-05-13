package com.peerlock.ui.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.request.DeviceInfo
import com.peerlock.domain.request.RequestProtocol
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PolicyTabState(
    val policies: List<RestrictionPolicy> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val defaultUnlockMinutes: Int = 5,
    val defaultRequestMinutes: Int = 30,
    val changes: Map<Long, PolicyChange> = emptyMap(),
    val mode: PolicyMode = PolicyMode.MANAGEMENT_CODE,
    val isDirty: Boolean = false,
    val generatedQr: String? = null,
    val error: String? = null,
    val showCodeInput: Boolean = false,
)

data class PolicyChange(
    val dailyLimitMinutes: Int? = null,
    val allowedTimeStart: String? = null,
    val allowedTimeEnd: String? = null,
)

enum class PolicyMode { MANAGEMENT_CODE, REQUEST }
enum class StrategyTab { APP_POLICY, CONFIG_PARAMS }

data class AppInfo(
    val packageName: String,
    val appName: String,
    val hasPolicy: Boolean,
)

data class StrategyUiState(
    val currentTab: StrategyTab = StrategyTab.APP_POLICY,
    val appPolicy: PolicyTabState = PolicyTabState(),
    val configParams: PolicyTabState = PolicyTabState(),
    val role: String = "",
    val isLoading: Boolean = false,
    val installedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
)

@HiltViewModel
class StrategyManagementViewModel @Inject constructor(
    private val application: Application,
    private val storageRepository: StorageRepository,
    private val policyEngine: PolicyEngine,
    private val securePrefs: SecurePrefs,
    private val seedManager: SeedManager,
    private val totpEngine: TotpEngine,
    private val requestProtocol: RequestProtocol,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StrategyUiState())
    val uiState: StateFlow<StrategyUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            val role = securePrefs.role ?: ""
            val installedApps = loadInstalledApps(policies)
            _uiState.value = _uiState.value.copy(
                role = role,
                appPolicy = _uiState.value.appPolicy.copy(policies = policies),
                installedApps = installedApps,
            )
        }
    }

    private fun loadInstalledApps(policies: List<RestrictionPolicy>): List<AppInfo> {
        val pm = application.packageManager
        val policyPackages = policies.map { it.targetPackage }.toSet()
        val selfPackage = application.packageName
        return pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { it.packageName != selfPackage }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    hasPolicy = appInfo.packageName in policyPackages,
                )
            }
            .sortedWith(compareBy<AppInfo> { !it.hasPolicy }.thenBy { it.appName })
    }

    fun addPolicyForApp(packageName: String) {
        viewModelScope.launch {
            val policy = RestrictionPolicy(
                targetPackage = packageName,
                dailyLimitMinutes = 60,
                allowedTimeStart = null,
                allowedTimeEnd = null,
                isBlacklist = true,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis(),
            )
            storageRepository.upsertPolicy(policy)
            storageRepository.insertAuditLog(
                com.peerlock.domain.repository.AuditLog(
                    timestamp = System.currentTimeMillis(),
                    action = "POLICY_ADD",
                    targetPackage = packageName,
                    detail = "新增应用限制",
                )
            )
            loadData()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun switchTab(tab: StrategyTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun togglePolicy(id: Long) {
        val current = _uiState.value.appPolicy.selectedIds
        val newSelected = if (id in current) current - id else current + id
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(selectedIds = newSelected),
        )
    }

    fun selectAllPolicies() {
        val allIds = _uiState.value.appPolicy.policies.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(selectedIds = allIds),
        )
    }

    fun updateDailyLimit(policyId: Long, minutes: Int) {
        val changes = _uiState.value.appPolicy.changes.toMutableMap()
        val existing = changes[policyId] ?: PolicyChange()
        changes[policyId] = existing.copy(dailyLimitMinutes = minutes)
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(changes = changes, isDirty = true),
        )
    }

    fun switchMode(mode: PolicyMode) {
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(mode = mode),
        )
    }

    fun requestSave() {
        // 显示管理码输入
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(showCodeInput = true),
        )
    }

    fun verifyManagementCode(code: String) {
        viewModelScope.launch {
            val seed = seedManager.retrieveSeed(KeyType.SETTING)
            if (seed == null) {
                _uiState.value = _uiState.value.copy(
                    appPolicy = _uiState.value.appPolicy.copy(error = "密钥缺失，无法验证管理码"),
                )
                return@launch
            }
            val valid = totpEngine.verifyCode(seed, code, tolerance = 1)
            if (valid) {
                saveChangesDirect()
            } else {
                _uiState.value = _uiState.value.copy(
                    appPolicy = _uiState.value.appPolicy.copy(error = "管理码错误"),
                )
            }
        }
    }

    private suspend fun saveChangesDirect() {
        val changes = _uiState.value.appPolicy.changes
        for ((policyId, change) in changes) {
            val policy = _uiState.value.appPolicy.policies.find { it.id == policyId } ?: continue
            if (change.dailyLimitMinutes != null) {
                storageRepository.upsertPolicy(
                    policy.copy(
                        dailyLimitMinutes = change.dailyLimitMinutes,
                        lastModified = System.currentTimeMillis(),
                    )
                )
                storageRepository.insertAuditLog(
                    com.peerlock.domain.repository.AuditLog(
                        timestamp = System.currentTimeMillis(),
                        action = "POLICY_CHANGE",
                        targetPackage = policy.targetPackage,
                        detail = "日限制: ${change.dailyLimitMinutes}分钟",
                    )
                )
            }
        }
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(
                changes = emptyMap(),
                isDirty = false,
                showCodeInput = false,
            ),
        )
        loadData()
    }

    fun generateChangeRequest() {
        viewModelScope.launch {
            val changes = _uiState.value.appPolicy.changes
            val policyChanges = changes.map { (policyId, change) ->
                val policy = _uiState.value.appPolicy.policies.find { it.id == policyId }
                com.peerlock.domain.request.PolicyChange(
                    targetPackage = policy?.targetPackage ?: "",
                    dailyLimitMinutes = change.dailyLimitMinutes,
                    allowedTimeStart = change.allowedTimeStart,
                    allowedTimeEnd = change.allowedTimeEnd,
                )
            }
            val sessionId = securePrefs.sessionId ?: run {
                _uiState.value = _uiState.value.copy(
                    appPolicy = _uiState.value.appPolicy.copy(error = "未找到会话 ID"),
                )
                return@launch
            }
            val qr = requestProtocol.generateConfigRequest(
                sessionId = sessionId,
                changes = policyChanges,
                deviceInfo = DeviceInfo(
                    todayScreenTimeMs = 0,
                    suspendedApps = emptyList(),
                    isInSafeMode = false,
                ),
            )
            if (qr != null) {
                _uiState.value = _uiState.value.copy(
                    appPolicy = _uiState.value.appPolicy.copy(
                        generatedQr = qr,
                        changes = emptyMap(),
                        isDirty = false,
                    ),
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    appPolicy = _uiState.value.appPolicy.copy(error = "生成调整指令失败"),
                )
            }
        }
    }

    fun cancelCodeInput() {
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(showCodeInput = false),
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(error = null),
        )
    }
}
