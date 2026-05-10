package com.peerlock.domain.security

import com.peerlock.data.prefs.SecurePrefs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimeSyncManagerImplTest {

    private lateinit var securePrefs: SecurePrefs
    private lateinit var manager: TimeSyncManagerImpl

    @BeforeEach
    fun setup() {
        securePrefs = mockk(relaxed = true)
        manager = TimeSyncManagerImpl(securePrefs)
    }

    @Test
    fun `从未同步时 isSystemTimeReliable 返回 false`() {
        every { securePrefs.lastNtpSyncTimestamp } returns 0L
        assertFalse(manager.isSystemTimeReliable())
    }

    @Test
    fun `6小时内同步过则返回 true`() {
        every { securePrefs.lastNtpSyncTimestamp } returns System.currentTimeMillis() - 3600_000L
        assertTrue(manager.isSystemTimeReliable())
    }

    @Test
    fun `超过6小时未同步返回 false`() {
        every { securePrefs.lastNtpSyncTimestamp } returns System.currentTimeMillis() - 7 * 3600_000L
        assertFalse(manager.isSystemTimeReliable())
    }

    @Test
    fun `getStoredOffset 返回持久化的偏移量`() {
        every { securePrefs.timeOffsetMs } returns 5000L
        assertEquals(5000L, manager.getStoredOffset())
    }

    @Test
    fun `getCurrentRealTime 等于系统时间加偏移量`() = runTest {
        every { securePrefs.timeOffsetMs } returns 3000L
        val before = System.currentTimeMillis()
        val result = manager.getCurrentRealTime()
        val after = System.currentTimeMillis()
        assertTrue(result >= before + 3000L)
        assertTrue(result <= after + 3000L)
    }
}
