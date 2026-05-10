package com.peerlock.system.service

import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyAction
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager

/**
 * 巡检逻辑：独立于 Android Service 生命周期，可单元测试。
 */
class PatrolLogic(
    private val policyEngine: PolicyEngine,
    private val usageCollector: UsageStatsCollector,
    private val storageRepository: StorageRepository,
    private val deviceOwnerManager: DeviceOwnerManager,
) {
    suspend fun executePatrol() {
        val now = System.currentTimeMillis()
        val policies = storageRepository.getActivePolicies()

        for (policy in policies) {
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
