package com.peerlock.domain.policy

import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class PolicyEngineImpl(
    private val storageRepository: StorageRepository,
    private val usageStatsCollector: UsageStatsCollector,
    private val deviceOwnerManager: DeviceOwnerManager,
) : PolicyEngine {

    override suspend fun evaluate(packageName: String, currentTimeMillis: Long): PolicyAction {
        val policies = storageRepository.getActivePolicies()
        val policy = policies.find { it.targetPackage == packageName }
            ?: return PolicyAction.Monitor

        val zone = ZoneId.systemDefault()
        val currentLocalTime = Instant.ofEpochMilli(currentTimeMillis).atZone(zone).toLocalTime()

        // 检查允许时段
        if (policy.allowedTimeStart != null && policy.allowedTimeEnd != null) {
            if (!isWithinAllowedTime(currentLocalTime, policy.allowedTimeStart, policy.allowedTimeEnd)) {
                return PolicyAction.Suspend
            }
        }

        // 检查每日时长限制
        if (policy.dailyLimitMinutes != null) {
            val dayStartMs = currentTimeMillis - (currentTimeMillis % 86_400_000)
            val usageMs = usageStatsCollector.getUsageTimeMs(packageName, dayStartMs, currentTimeMillis)
            if (usageMs >= policy.dailyLimitMinutes * 60 * 1000L) {
                return PolicyAction.Suspend
            }
        }

        return PolicyAction.Monitor
    }

    override suspend fun suspendApp(packageName: String) {
        if (!deviceOwnerManager.isDeviceOwner()) return
        deviceOwnerManager.setPackagesSuspended(listOf(packageName), true)
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "SUSPEND",
                targetPackage = packageName,
                detail = "策略触发暂停"
            )
        )
    }

    override suspend fun unsuspendApp(packageName: String) {
        if (!deviceOwnerManager.isDeviceOwner()) return
        deviceOwnerManager.setPackagesSuspended(listOf(packageName), false)
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "UNSUSPEND",
                targetPackage = packageName,
                detail = "解除暂停"
            )
        )
    }

    override suspend fun resetDailyUsage() {
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "RESET_DAILY",
                targetPackage = null,
                detail = "每日使用数据重置"
            )
        )
    }

    private fun isWithinAllowedTime(current: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start.isBefore(end) || start == end) {
            !current.isBefore(start) && !current.isAfter(end)
        } else {
            !current.isBefore(start) || !current.isAfter(end)
        }
    }
}
