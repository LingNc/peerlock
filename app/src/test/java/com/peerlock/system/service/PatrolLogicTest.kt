package com.peerlock.system.service

import com.peerlock.data.usage.UsageAggregator
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyAction
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.security.SafeModeManager
import com.peerlock.domain.security.SyncResult
import com.peerlock.domain.security.TimeSyncManager
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PatrolLogicTest {

    private lateinit var policyEngine: PolicyEngine
    private lateinit var usageCollector: UsageStatsCollector
    private lateinit var storageRepo: StorageRepository
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private lateinit var timeSyncManager: TimeSyncManager
    private lateinit var safeModeManager: SafeModeManager
    private lateinit var usageAggregator: UsageAggregator
    private lateinit var patrolLogic: PatrolLogic

    private val testPolicy = RestrictionPolicy(
        id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
        allowedTimeStart = null, allowedTimeEnd = null,
        isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
    )

    @BeforeEach
    fun setup() {
        policyEngine = mockk(relaxed = true)
        usageCollector = mockk(relaxed = true)
        storageRepo = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        timeSyncManager = mockk(relaxed = true)
        safeModeManager = mockk(relaxed = true)
        usageAggregator = mockk(relaxed = true)
        patrolLogic = PatrolLogic(
            policyEngine, usageCollector, storageRepo, deviceOwnerManager,
            timeSyncManager, safeModeManager, usageAggregator
        )

        // 默认：时间可靠，非安全模式
        every { timeSyncManager.isSystemTimeReliable() } returns true
        every { safeModeManager.isInSafeMode() } returns false
    }

    @Test
    fun `巡检应检查所有受限应用`() = runTest {
        val policies = listOf(
            testPolicy,
            RestrictionPolicy(
                id = 2, targetPackage = "com.app2", dailyLimitMinutes = 30,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
        )
        coEvery { storageRepo.getActivePolicies() } returns policies
        every { usageCollector.getUsageTimeMs(any(), any(), any()) } returns 0L
        coEvery { policyEngine.evaluate(any(), any()) } returns PolicyAction.Monitor

        patrolLogic.executePatrol()

        coVerify { policyEngine.evaluate("com.app1", any()) }
        coVerify { policyEngine.evaluate("com.app2", any()) }
    }

    @Test
    fun `巡检遇到 Suspend 应暂停应用`() = runTest {
        coEvery { storageRepo.getActivePolicies() } returns listOf(testPolicy)
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Suspend

        patrolLogic.executePatrol()

        coVerify { policyEngine.suspendApp("com.app1") }
    }

    @Test
    fun `巡检遇到 Monitor 不暂停`() = runTest {
        coEvery { storageRepo.getActivePolicies() } returns listOf(testPolicy)
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Monitor

        patrolLogic.executePatrol()

        coVerify(exactly = 0) { policyEngine.suspendApp(any()) }
    }

    @Test
    fun `巡检遇到 Unsuspend 应解除暂停`() = runTest {
        coEvery { storageRepo.getActivePolicies() } returns listOf(testPolicy)
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Unsuspend

        patrolLogic.executePatrol()

        coVerify { policyEngine.unsuspendApp("com.app1") }
    }

    @Test
    fun `NTP 同步失败时进入安全模式`() = runTest {
        every { timeSyncManager.isSystemTimeReliable() } returns false
        coEvery { timeSyncManager.syncWithNtp() } returns SyncResult.Failed

        patrolLogic.executePatrol()

        coVerify { safeModeManager.enterSafeMode(any()) }
    }

    @Test
    fun `安全模式恢复后退出安全模式`() = runTest {
        every { safeModeManager.isInSafeMode() } returns true
        coEvery { timeSyncManager.syncWithNtp() } returns SyncResult.Success(0L)
        coEvery { storageRepo.getActivePolicies() } returns emptyList()

        patrolLogic.executePatrol()

        coVerify { safeModeManager.exitSafeMode() }
    }

    @Test
    fun `安全模式下暂停所有受限应用`() = runTest {
        every { safeModeManager.isInSafeMode() } returns true
        coEvery { storageRepo.getActivePolicies() } returns listOf(testPolicy)

        patrolLogic.executePatrol()

        coVerify { policyEngine.suspendApp("com.app1") }
    }

    @Test
    fun `已解锁的应用跳过巡检`() = runTest {
        coEvery { storageRepo.getActivePolicies() } returns listOf(testPolicy)
        every { policyEngine.isUnlocked("com.app1", any()) } returns true

        patrolLogic.executePatrol()

        coVerify(exactly = 0) { policyEngine.evaluate(any(), any()) }
    }
}
