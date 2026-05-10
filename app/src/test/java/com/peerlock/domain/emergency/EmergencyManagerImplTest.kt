package com.peerlock.domain.emergency

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmergencyManagerImplTest {

    private lateinit var manager: EmergencyManagerImpl
    private lateinit var securePrefs: SecurePrefs
    private lateinit var seedManager: SeedManager
    private lateinit var totpEngine: TotpEngine
    private lateinit var storageRepository: StorageRepository
    private lateinit var deviceOwnerManager: DeviceOwnerManager

    @BeforeEach
    fun setup() {
        securePrefs = mockk(relaxed = true)
        seedManager = mockk(relaxed = true)
        totpEngine = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        manager = EmergencyManagerImpl(
            securePrefs, seedManager, totpEngine, storageRepository, deviceOwnerManager
        )
    }

    @Test
    fun `verifyDestroyCode 正确码返回 true`() = runTest {
        coEvery { seedManager.retrieveSeed(KeyType.DESTROY) } returns ByteArray(32) { 0x01 }
        every { totpEngine.verifyCode(any(), "123456", any()) } returns true

        val result = manager.verifyDestroyCode("123456")

        assertTrue(result)
        verify { securePrefs.totpErrorCount = 0 }
    }

    @Test
    fun `verifyDestroyCode 错误码返回 false 并增加错误计数`() = runTest {
        coEvery { seedManager.retrieveSeed(KeyType.DESTROY) } returns ByteArray(32) { 0x01 }
        every { totpEngine.verifyCode(any(), "000000", any()) } returns false

        val result = manager.verifyDestroyCode("000000")

        assertFalse(result)
        verify { securePrefs.totpErrorCount = 1 }
    }

    @Test
    fun `verifyDestroyCode 5次错误后锁定30秒`() = runTest {
        coEvery { seedManager.retrieveSeed(KeyType.DESTROY) } returns ByteArray(32) { 0x01 }
        every { totpEngine.verifyCode(any(), "000000", any()) } returns false
        every { securePrefs.totpErrorCount } returns 4

        manager.verifyDestroyCode("000000")

        verify { securePrefs.totpLockedUntil = any() }
    }

    @Test
    fun `verifyDestroyCode 锁定期间直接返回 false`() = runTest {
        every { securePrefs.totpLockedUntil } returns System.currentTimeMillis() + 30_000L

        val result = manager.verifyDestroyCode("123456")

        assertFalse(result)
        coVerify(exactly = 0) { seedManager.retrieveSeed(any()) }
    }

    @Test
    fun `executeDestroy 清除加密材料和配对状态`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true

        val result = manager.executeDestroy()

        assertTrue(result)
        verify {
            securePrefs.clearPairingData()
        }
        coVerify {
            storageRepository.insertAuditLog(match { it.action == "DESTROY" })
        }
    }

    @Test
    fun `executeDestroy 不移除 Device Owner`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true

        manager.executeDestroy()

        verify(exactly = 0) { deviceOwnerManager.setPackagesSuspended(any(), any()) }
    }

    @Test
    fun `generateNonce 返回16位hex字符串`() {
        val nonce = manager.generateNonce()

        assertEquals(16, nonce.length)
        assertTrue(nonce.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `verifyAndExecuteEmergency 有效nonce返回true`() = runTest {
        val nonce = "a1b2c3d4e5f6a7b8"
        every { securePrefs.emergencyNonce } returns nonce
        every { securePrefs.emergencyNonceExpiry } returns System.currentTimeMillis() + 300_000L

        val result = manager.verifyAndExecuteEmergency(nonce)

        assertTrue(result)
        verify {
            securePrefs.emergencyNonce = null
            securePrefs.emergencyNonceExpiry = 0L
        }
        verify { deviceOwnerManager.removeDeviceOwner() }
        coVerify {
            storageRepository.insertAuditLog(match { it.action == "EMERGENCY_L2" })
        }
    }

    @Test
    fun `verifyAndExecuteEmergency 过期nonce返回false`() = runTest {
        val nonce = "a1b2c3d4e5f6a7b8"
        every { securePrefs.emergencyNonce } returns nonce
        every { securePrefs.emergencyNonceExpiry } returns System.currentTimeMillis() - 1000L

        val result = manager.verifyAndExecuteEmergency(nonce)

        assertFalse(result)
    }

    @Test
    fun `verifyAndExecuteEmergency 错误nonce返回false`() = runTest {
        every { securePrefs.emergencyNonce } returns "correct_nonce_16"
        every { securePrefs.emergencyNonceExpiry } returns System.currentTimeMillis() + 300_000L

        val result = manager.verifyAndExecuteEmergency("wrong_nonce_16x")

        assertFalse(result)
    }
}
