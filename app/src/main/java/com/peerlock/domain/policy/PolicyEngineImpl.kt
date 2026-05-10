package com.peerlock.domain.policy

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class PolicyEngineImpl(
    private val storageRepository: StorageRepository,
    private val usageStatsCollector: UsageStatsCollector,
    private val deviceOwnerManager: DeviceOwnerManager,
    private val seedManager: SeedManager,
    private val totpEngine: TotpEngine,
    private val securePrefs: SecurePrefs,
) : PolicyEngine {

    // 解锁状态：packageName → 过期时间戳
    private val unlockState = mutableMapOf<String, Long>()

    override suspend fun evaluate(packageName: String, currentTimeMillis: Long): PolicyAction {
        val policies = storageRepository.getActivePolicies()

        // 黑名单模式（isBlacklist=true）：列表中的应用受限制
        // 白名单模式（isBlacklist=false）：列表中的应用是允许的，其他全部限制
        val isWhitelistMode = policies.any { !it.isBlacklist }
        if (isWhitelistMode) {
            val inList = policies.any { it.targetPackage == packageName }
            if (!inList) return PolicyAction.Suspend
        }

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

    override suspend fun verifyUnlockCode(code: String): Boolean {
        val now = System.currentTimeMillis()
        // 检查是否处于锁定期（5次错误 → 30秒锁定）
        if (now < securePrefs.totpLockedUntil) return false

        val seed = seedManager.retrieveSeed(KeyType.UNLOCK) ?: return false
        val valid = totpEngine.verifyCode(seed, code, tolerance = 1)
        if (valid) {
            securePrefs.totpErrorCount = 0
            return true
        } else {
            val errors = securePrefs.totpErrorCount + 1
            securePrefs.totpErrorCount = errors
            if (errors >= 5) {
                securePrefs.totpLockedUntil = now + 30_000L
                securePrefs.totpErrorCount = 0
            }
            return false
        }
    }

    override suspend fun recordUnlock(packageName: String, durationMinutes: Int) {
        val expiry = System.currentTimeMillis() + durationMinutes * 60 * 1000L
        unlockState[packageName] = expiry
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "UNLOCK",
                targetPackage = packageName,
                detail = "临时解锁 ${durationMinutes} 分钟"
            )
        )
    }

    override fun isUnlocked(packageName: String, currentTimeMillis: Long): Boolean {
        val expiry = unlockState[packageName] ?: return false
        return currentTimeMillis < expiry
    }

    private fun isWithinAllowedTime(current: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start.isBefore(end) || start == end) {
            !current.isBefore(start) && !current.isAfter(end)
        } else {
            !current.isBefore(start) || !current.isAfter(end)
        }
    }
}
