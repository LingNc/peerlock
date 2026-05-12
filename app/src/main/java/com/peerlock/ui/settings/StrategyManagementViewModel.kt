package com.peerlock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
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
)

data class PolicyChange(
    val dailyLimitMinutes: Int? = null,
    val allowedTimeStart: String? = null,
    val allowedTimeEnd: String? = null,
)

enum class PolicyMode { MANAGEMENT_CODE, REQUEST }
enum class StrategyTab { APP_POLICY, CONFIG_PARAMS }

data class StrategyUiState(
    val currentTab: StrategyTab = StrategyTab.APP_POLICY,
    val appPolicy: PolicyTabState = PolicyTabState(),
    val configParams: PolicyTabState = PolicyTabState(),
    val role: String = "",
    val isLoading: Boolean = false,
)

@HiltViewModel
class StrategyManagementViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val policyEngine: PolicyEngine,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StrategyUiState())
    val uiState: StateFlow<StrategyUiState> = _uiState.asStateFlow()

    init {
        loadPolicies()
    }

    private fun loadPolicies() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            val role = securePrefs.role ?: ""
            _uiState.value = _uiState.value.copy(
                role = role,
                appPolicy = _uiState.value.appPolicy.copy(policies = policies),
            )
        }
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

    fun saveChanges() {
        // TODO: 管理码模式 — 验证管理码后保存
        val changes = _uiState.value.appPolicy.changes
        viewModelScope.launch {
            for ((policyId, change) in changes) {
                val policy = _uiState.value.appPolicy.policies.find { it.id == policyId } ?: continue
                if (change.dailyLimitMinutes != null) {
                    storageRepository.upsertPolicy(
                        policy.copy(
                            dailyLimitMinutes = change.dailyLimitMinutes,
                            lastModified = System.currentTimeMillis(),
                        )
                    )
                }
            }
            _uiState.value = _uiState.value.copy(
                appPolicy = _uiState.value.appPolicy.copy(changes = emptyMap(), isDirty = false),
            )
            loadPolicies()
        }
    }

    fun generateChangeRequest() {
        // TODO: 申请模式 — 生成策略变更 QR/字符串
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(error = "生成调整指令功能开发中"),
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            appPolicy = _uiState.value.appPolicy.copy(error = null),
        )
    }
}
