package com.peerlock.domain.security

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SafeModeManagerImplTest {

    private lateinit var securePrefs: SecurePrefs
    private lateinit var storageRepo: StorageRepository
    private lateinit var manager: SafeModeManagerImpl

    @BeforeEach
    fun setup() {
        securePrefs = mockk(relaxed = true)
        storageRepo = mockk(relaxed = true)
        manager = SafeModeManagerImpl(securePrefs, storageRepo)
    }

    @Test
    fun `进入安全模式后 isInSafeMode 返回 true`() {
        every { securePrefs.safeModeActive } returns true
        assertTrue(manager.isInSafeMode())
    }

    @Test
    fun `未进入安全模式时返回 false`() {
        every { securePrefs.safeModeActive } returns false
        assertFalse(manager.isInSafeMode())
    }

    @Test
    fun `enterSafeMode 设置状态并记录审计日志`() = runTest {
        manager.enterSafeMode("测试原因")

        verify { securePrefs.safeModeActive = true }
        verify { securePrefs.safeModeReason = "测试原因" }
        coVerify { storageRepo.insertAuditLog(match { it.action == "SAFE_MODE_ENTER" }) }
    }

    @Test
    fun `exitSafeMode 清除状态并记录审计日志`() = runTest {
        every { securePrefs.safeModeActive } returns true

        manager.exitSafeMode()

        verify { securePrefs.safeModeActive = false }
        verify { securePrefs.safeModeReason = null }
        coVerify { storageRepo.insertAuditLog(match { it.action == "SAFE_MODE_EXIT" }) }
    }

    @Test
    fun `exitSafeMode 非活动状态不记录日志`() = runTest {
        every { securePrefs.safeModeActive } returns false

        manager.exitSafeMode()

        verify { securePrefs.safeModeActive = false }
        coVerify(exactly = 0) { storageRepo.insertAuditLog(any()) }
    }

    @Test
    fun `getSafeModeReason 返回存储的原因`() {
        every { securePrefs.safeModeReason } returns "NTP 同步失败"
        assertEquals("NTP 同步失败", manager.getSafeModeReason())
    }

    @Test
    fun `getSafeModeReason 未设置时返回 null`() {
        every { securePrefs.safeModeReason } returns null
        assertNull(manager.getSafeModeReason())
    }
}
