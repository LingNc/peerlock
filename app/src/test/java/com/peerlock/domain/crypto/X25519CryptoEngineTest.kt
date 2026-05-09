package com.peerlock.domain.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class X25519CryptoEngineTest {

    private lateinit var engine: X25519CryptoEngine

    @BeforeEach
    fun setup() {
        engine = X25519CryptoEngine()
    }

    @Test
    fun `生成密钥对应产生 32 字节公钥`() = runTest {
        val keyPair = engine.generateKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKeyAlias)
        assertEquals(32, keyPair.publicKey.size)
    }

    @Test
    fun `签名和验证往返一致`() = runTest {
        engine.generateKeyPair()
        val data = "测试数据".toByteArray()
        val signature = engine.sign(data)
        val edPubKey = engine.ed25519PublicKey()
        assertTrue(engine.verify(data, signature, edPubKey))
    }

    @Test
    fun `错误签名应验证失败`() = runTest {
        engine.generateKeyPair()
        val data = "测试".toByteArray()
        val edPubKey = engine.ed25519PublicKey()
        val fakeSignature = ByteArray(64) { it.toByte() }
        assertFalse(engine.verify(data, fakeSignature, edPubKey))
    }

    @Test
    fun `双方派生共享密钥应一致`() = runTest {
        val alice = X25519CryptoEngine()
        val bob = X25519CryptoEngine()

        val aliceKeyPair = alice.generateKeyPair()
        val bobKeyPair = bob.generateKeyPair()

        val secretAB = alice.deriveSharedSecret(bobKeyPair.publicKey)
        val secretBA = bob.deriveSharedSecret(aliceKeyPair.publicKey)

        assertArrayEquals(secretAB, secretBA)
        assertEquals(32, secretAB.size)
    }
}
