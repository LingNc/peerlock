package com.peerlock.system.service

import com.peerlock.data.usage.UsageAggregator
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyAction
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.security.SafeModeManager
import com.peerlock.domain.security.SyncResult
import com.peerlock.domain.security.TimeSyncManager
import com.peerlock.system.deviceadmin.DeviceOwnerManager

/**
 * 巡检逻辑：独立于 Android Service 生命周期，可单元测试。
 */
class PatrolLogic(
    private val policyEngine: PolicyEngine,
    private val usageCollector: UsageStatsCollector,
    private val storageRepository: StorageRepository,
    private val deviceOwnerManager: DeviceOwnerManager,
    private val timeSyncManager: TimeSyncManager,
    private val safeModeManager: SafeModeManager,
    private val usageAggregator: UsageAggregator,
) {
    suspend fun executePatrol() {
        val now = System.currentTimeMillis()

        // 1. 检查时间可靠性
        if (!timeSyncManager.isSystemTimeReliable()) {
            val syncResult = timeSyncManager.syncWithNtp()
            if (syncResult is SyncResult.Failed && !safeModeManager.isInSafeMode()) {
                safeModeManager.enterSafeMode("NTP 同步失败，系统时间不可靠")
            }
        } else if (safeModeManager.isInSafeMode()) {
            // 时间恢复可靠，尝试退出安全模式
            val syncResult = timeSyncManager.syncWithNtp()
            if (syncResult is SyncResult.Success) {
                safeModeManager.exitSafeMode()
            }
        }

        // 2. 安全模式 → 暂停所有受限应用
        if (safeModeManager.isInSafeMode()) {
            val policies = storageRepository.getActivePolicies()
            for (policy in policies) {
                policyEngine.suspendApp(policy.targetPackage)
            }
            return
        }

        // 3. 正常巡检
        val policies = storageRepository.getActivePolicies()
        for (policy in policies) {
            // 已解锁的应用跳过
            if (policyEngine.isUnlocked(policy.targetPackage, now)) continue

            val action = policyEngine.evaluate(policy.targetPackage, now)
            when (action) {
                is PolicyAction.Suspend -> {
                    policyEngine.suspendApp(policy.targetPackage)
                }
                is PolicyAction.Unsuspend -> {
                    policyEngine.unsuspendApp(policy.targetPackage)
                }
                is PolicyAction.Unlock -> {
                    // 解锁由 verifyUnlockCode 处理
                }
                is PolicyAction.Monitor -> {
                    // 正常状态，无需操作
                }
            }
        }
    }
}
