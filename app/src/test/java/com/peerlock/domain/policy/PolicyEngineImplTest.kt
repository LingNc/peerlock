package com.peerlock.domain.policy

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
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
    private lateinit var seedManager: SeedManager
    private lateinit var totpEngine: TotpEngine
    private lateinit var securePrefs: SecurePrefs

    @BeforeEach
    fun setup() {
        storageRepo = mockk(relaxed = true)
        usageCollector = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        seedManager = mockk(relaxed = true)
        totpEngine = mockk(relaxed = true)
        securePrefs = mockk(relaxed = true)
        engine = PolicyEngineImpl(
            storageRepo, usageCollector, deviceOwnerManager,
            seedManager, totpEngine, securePrefs
        )
    }

    // --- 原有测试（黑名单模式） ---

    @Test
    fun `黑名单模式：无策略时返回 Monitor`() = runTest {
        coEvery { storageRepo.getActivePolicies() } returns emptyList()
        val action = engine.evaluate("com.test", System.currentTimeMillis())
        assertTrue(action is PolicyAction.Monitor)
    }

    @Test
    fun `黑名单模式：超过每日时长限制返回 Suspend`() = runTest {
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
    fun `黑名单模式：未超时返回 Monitor`() = runTest {
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
    fun `黑名单模式：非允许时段返回 Suspend`() = runTest {
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

    // --- 白名单模式测试 ---

    @Test
    fun `白名单模式：不在列表中的应用返回 Suspend`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.allowed", dailyLimitMinutes = null,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = false, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)
        val action = engine.evaluate("com.other", System.currentTimeMillis())
        assertTrue(action is PolicyAction.Suspend)
    }

    @Test
    fun `白名单模式：在列表中且无限制返回 Monitor`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.allowed", dailyLimitMinutes = null,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = false, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)
        val action = engine.evaluate("com.allowed", System.currentTimeMillis())
        assertTrue(action is PolicyAction.Monitor)
    }

    // --- 解锁状态测试 ---

    @Test
    fun `未解锁时 isUnlocked 返回 false`() {
        assertFalse(engine.isUnlocked("com.test", System.currentTimeMillis()))
    }

    @Test
    fun `recordUnlock 后在窗口内返回 true`() = runTest {
        engine.recordUnlock("com.test", 10) // 10分钟
        val now = System.currentTimeMillis()
        assertTrue(engine.isUnlocked("com.test", now))
        assertTrue(engine.isUnlocked("com.test", now + 5 * 60 * 1000)) // 5分钟后
        assertFalse(engine.isUnlocked("com.test", now + 11 * 60 * 1000)) // 11分钟后
    }

    // --- TOTP 解锁码验证测试 ---

    @Test
    fun `有效解锁码返回 true`() = runTest {
        val seed = ByteArray(20) { it.toByte() }
        coEvery { seedManager.retrieveSeed(KeyType.UNLOCK) } returns seed
        every { totpEngine.verifyCode(seed, "123456", tolerance = 1) } returns true
        every { securePrefs.totpLockedUntil } returns 0L
        every { securePrefs.totpErrorCount } returns 0

        assertTrue(engine.verifyUnlockCode("123456"))

        verify { securePrefs.totpErrorCount = 0 }
    }

    @Test
    fun `无效解锁码递增错误计数`() = runTest {
        val seed = ByteArray(20) { it.toByte() }
        coEvery { seedManager.retrieveSeed(KeyType.UNLOCK) } returns seed
        every { totpEngine.verifyCode(seed, "000000", tolerance = 1) } returns false
        every { securePrefs.totpLockedUntil } returns 0L
        every { securePrefs.totpErrorCount } returns 2

        assertFalse(engine.verifyUnlockCode("000000"))

        verify { securePrefs.totpErrorCount = 3 }
    }

    @Test
    fun `5次错误后锁定30秒`() = runTest {
        val seed = ByteArray(20) { it.toByte() }
        coEvery { seedManager.retrieveSeed(KeyType.UNLOCK) } returns seed
        every { totpEngine.verifyCode(seed, "000000", tolerance = 1) } returns false
        every { securePrefs.totpLockedUntil } returns 0L
        every { securePrefs.totpErrorCount } returns 4 // 第5次错误

        assertFalse(engine.verifyUnlockCode("000000"))

        verify { securePrefs.totpLockedUntil = match { it > System.currentTimeMillis() } }
        verify { securePrefs.totpErrorCount = 0 }
    }

    @Test
    fun `锁定期间直接返回 false`() = runTest {
        every { securePrefs.totpLockedUntil } returns System.currentTimeMillis() + 30_000L

        assertFalse(engine.verifyUnlockCode("123456"))

        coVerify(exactly = 0) { seedManager.retrieveSeed(any()) }
    }
}
