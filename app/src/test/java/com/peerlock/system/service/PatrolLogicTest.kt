package com.peerlock.system.service

import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyAction
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
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
    private lateinit var patrolLogic: PatrolLogic

    @BeforeEach
    fun setup() {
        policyEngine = mockk(relaxed = true)
        usageCollector = mockk(relaxed = true)
        storageRepo = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        patrolLogic = PatrolLogic(policyEngine, usageCollector, storageRepo, deviceOwnerManager)
    }

    @Test
    fun `巡检应检查所有受限应用`() = runTest {
        val policies = listOf(
            RestrictionPolicy(
                id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
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
        val policies = listOf(
            RestrictionPolicy(
                id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
        )
        coEvery { storageRepo.getActivePolicies() } returns policies
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Suspend
        coEvery { policyEngine.suspendApp("com.app1") } just runs

        patrolLogic.executePatrol()

        coVerify { policyEngine.suspendApp("com.app1") }
    }

    @Test
    fun `巡检遇到 Monitor 不暂停`() = runTest {
        val policies = listOf(
            RestrictionPolicy(
                id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
        )
        coEvery { storageRepo.getActivePolicies() } returns policies
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Monitor

        patrolLogic.executePatrol()

        coVerify(exactly = 0) { policyEngine.suspendApp(any()) }
    }

    @Test
    fun `巡检遇到 Unsuspend 应解除暂停`() = runTest {
        val policies = listOf(
            RestrictionPolicy(
                id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
        )
        coEvery { storageRepo.getActivePolicies() } returns policies
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Unsuspend
        coEvery { policyEngine.unsuspendApp("com.app1") } just runs

        patrolLogic.executePatrol()

        coVerify { policyEngine.unsuspendApp("com.app1") }
    }
}
