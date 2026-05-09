package com.peerlock.domain.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class P256CryptoEngineTest {

    private lateinit var engine: P256CryptoEngine

    @BeforeEach
    fun setup() {
        engine = P256CryptoEngine()
    }

    @Test
    fun `生成密钥对应产生有效公钥`() = runTest {
        val keyPair = engine.generateKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKeyAlias)
        assertEquals(65, keyPair.publicKey.size)
        assertEquals(0x04.toByte(), keyPair.publicKey[0])
    }

    @Test
    fun `加密后解密应恢复原始数据`() = runTest {
        val keyPair = engine.generateKeyPair()
        val plaintext = "Hello, PeerLock!".toByteArray()

        val encrypted = engine.encrypt(plaintext, keyPair.publicKey)
        assertNotEquals(plaintext.toList(), encrypted.toList())

        val decrypted = engine.decryptWithPeerKey(encrypted, keyPair.publicKey)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `签名和验证往返一致`() = runTest {
        val keyPair = engine.generateKeyPair()
        val data = "重要数据".toByteArray()

        val signature = engine.sign(data)
        assertTrue(engine.verify(data, signature, keyPair.publicKey))
    }

    @Test
    fun `篡改数据后验证应失败`() = runTest {
        val keyPair = engine.generateKeyPair()
        val data = "重要数据".toByteArray()

        val signature = engine.sign(data)
        val tampered = "被篡改的数据".toByteArray()
        assertFalse(engine.verify(tampered, signature, keyPair.publicKey))
    }

    @Test
    fun `双方派生共享密钥应一致`() = runTest {
        val alice = P256CryptoEngine()
        val bob = P256CryptoEngine()

        val aliceKeyPair = alice.generateKeyPair()
        val bobKeyPair = bob.generateKeyPair()

        val secretAB = alice.deriveSharedSecret(bobKeyPair.publicKey)
        val secretBA = bob.deriveSharedSecret(aliceKeyPair.publicKey)

        assertArrayEquals(secretAB, secretBA)
    }
}
