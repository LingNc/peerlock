package com.peerlock.domain.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdaptiveCryptoEngineTest {

    @Test
    fun `API 30 应选择 P256 曲线`() = runTest {
        val engine = AdaptiveCryptoEngine(apiLevel = 30)
        val keyPair = engine.generateKeyPair()
        assertEquals(65, keyPair.publicKey.size)
    }

    @Test
    fun `API 33 应选择 X25519 曲线`() = runTest {
        val engine = AdaptiveCryptoEngine(apiLevel = 33)
        val keyPair = engine.generateKeyPair()
        assertEquals(32, keyPair.publicKey.size)
    }

    @Test
    fun `P256 下签名验证正常工作`() = runTest {
        val engine = AdaptiveCryptoEngine(apiLevel = 30)
        val keyPair = engine.generateKeyPair()
        val data = "测试".toByteArray()
        val sig = engine.sign(data)
        assertTrue(engine.verify(data, sig, keyPair.publicKey))
    }

    @Test
    fun `X25519 下签名验证正常工作`() = runTest {
        val x25519 = X25519CryptoEngine()
        x25519.generateKeyPair()
        val data = "测试".toByteArray()
        val sig = x25519.sign(data)
        assertTrue(x25519.verify(data, sig, x25519.ed25519PublicKey()))
    }
}
