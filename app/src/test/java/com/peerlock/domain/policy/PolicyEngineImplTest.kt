package com.peerlock.domain.policy

import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class PolicyEngineImplTest {

    private lateinit var engine: PolicyEngineImpl
    private lateinit var storageRepo: StorageRepository
    private lateinit var usageCollector: UsageStatsCollector
    private lateinit var deviceOwnerManager: DeviceOwnerManager

    @BeforeEach
    fun setup() {
        storageRepo = mockk(relaxed = true)
        usageCollector = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        engine = PolicyEngineImpl(storageRepo, usageCollector, deviceOwnerManager)
    }

    @Test
    fun `无策略时返回 Monitor`() = runTest {
        coEvery { storageRepo.getActivePolicies() } returns emptyList()

        val action = engine.evaluate("com.test", System.currentTimeMillis())

        assertTrue(action is PolicyAction.Monitor)
    }

    @Test
    fun `超过每日时长限制返回 Suspend`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = 60,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)

        val now = System.currentTimeMillis()
        val dayStart = now - (now % 86_400_000)
        every { usageCollector.getUsageTimeMs("com.test", dayStart, now) } returns 61 * 60 * 1000L

        val action = engine.evaluate("com.test", now)

        assertTrue(action is PolicyAction.Suspend)
    }

    @Test
    fun `未超时返回 Monitor`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = 60,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)

        val now = System.currentTimeMillis()
        val dayStart = now - (now % 86_400_000)
        every { usageCollector.getUsageTimeMs("com.test", dayStart, now) } returns 30 * 60 * 1000L

        val action = engine.evaluate("com.test", now)

        assertTrue(action is PolicyAction.Monitor)
    }

    @Test
    fun `非允许时段返回 Suspend`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = null,
            allowedTimeStart = LocalTime.of(8, 0), allowedTimeEnd = LocalTime.of(22, 0),
            isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)

        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 3)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        val now = cal.timeInMillis

        val action = engine.evaluate("com.test", now)

        assertTrue(action is PolicyAction.Suspend)
    }

    @Test
    fun `suspendApp 调用 DeviceOwnerManager`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true
        every { deviceOwnerManager.setPackagesSuspended(any(), true) } returns emptyList()

        engine.suspendApp("com.test")

        verify { deviceOwnerManager.setPackagesSuspended(listOf("com.test"), true) }
        coVerify { storageRepo.insertAuditLog(any()) }
    }

    @Test
    fun `unsuspendApp 调用 DeviceOwnerManager`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true
        every { deviceOwnerManager.setPackagesSuspended(any(), false) } returns emptyList()

        engine.unsuspendApp("com.test")

        verify { deviceOwnerManager.setPackagesSuspended(listOf("com.test"), false) }
    }
}
