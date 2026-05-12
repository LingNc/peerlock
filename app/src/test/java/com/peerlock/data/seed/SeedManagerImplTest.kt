package com.peerlock.data.seed

import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.totp.KeyType
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SeedManagerImplTest {

    private lateinit var seedManager: SeedManagerImpl
    private lateinit var mockKeystore: KeystoreManager
    private lateinit var mockPrefs: SecurePrefs
    private lateinit var mockSessionDao: PairingSessionDao

    @BeforeEach
    fun setup() {
        mockKeystore = mockk()
        mockPrefs = mockk(relaxed = true)
        mockSessionDao = mockk(relaxed = true)
        // 默认无活跃会话，走 SecurePrefs 回退路径
        coEvery { mockSessionDao.getActiveSession() } returns null
        seedManager = SeedManagerImpl(mockKeystore, mockPrefs, mockSessionDao)
    }

    @Test
    fun `生成 3 个种子且各为 20 字节`() {
        val seeds = seedManager.generateSeeds()
        assertEquals(3, seeds.size)
        assertTrue(seeds.containsKey(KeyType.SETTING))
        assertTrue(seeds.containsKey(KeyType.UNLOCK))
        assertTrue(seeds.containsKey(KeyType.DESTROY))
        seeds.values.forEach { seed ->
            assertEquals(20, seed.size)
        }
    }

    @Test
    fun `每次生成的种子不同`() {
        val seeds1 = seedManager.generateSeeds()
        val seeds2 = seedManager.generateSeeds()
        KeyType.entries.forEach { keyType ->
            assertFalse(
                seeds1[keyType]!!.contentEquals(seeds2[keyType]!!),
                "$keyType 种子不应相同"
            )
        }
    }

    @Test
    fun `存储种子应调用 KeystoreManager 加密`() {
        val seeds = mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val encrypted = ByteArray(50)

        every { mockKeystore.encryptSeed(any()) } returns encrypted

        kotlinx.coroutines.runBlocking {
            seedManager.storeSeeds(seeds)
        }

        verify(exactly = 3) { mockKeystore.encryptSeed(any()) }
        verify { mockPrefs.encryptedSeedSetting = any() }
        verify { mockPrefs.encryptedSeedUnlock = any() }
        verify { mockPrefs.encryptedSeedDestroy = any() }
    }

    @Test
    fun `读取种子应调用 KeystoreManager 解密`() {
        val encryptedBase64 = "ZW5jcnlwdGVk"

        every { mockPrefs.encryptedSeedSetting } returns encryptedBase64
        every { mockKeystore.decryptSeed(any()) } returns ByteArray(20) { 42 }

        val result = kotlinx.coroutines.runBlocking {
            seedManager.retrieveSeed(KeyType.SETTING)
        }

        assertNotNull(result)
        assertEquals(20, result!!.size)
        verify { mockKeystore.decryptSeed(any()) }
    }

    @Test
    fun `未配对时读取种子返回 null`() {
        every { mockPrefs.encryptedSeedSetting } returns null

        val result = kotlinx.coroutines.runBlocking {
            seedManager.retrieveSeed(KeyType.SETTING)
        }

        assertNull(result)
    }
}
