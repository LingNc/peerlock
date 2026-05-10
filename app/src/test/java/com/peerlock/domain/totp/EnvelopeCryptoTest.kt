package com.peerlock.domain.totp

import com.peerlock.domain.crypto.CryptoEngine
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnvelopeCryptoTest {

    private lateinit var envelopeCrypto: EnvelopeCrypto
    private lateinit var cryptoEngine: CryptoEngine

    @BeforeEach
    fun setup() {
        cryptoEngine = mockk(relaxed = true)
        envelopeCrypto = EnvelopeCrypto(cryptoEngine)
    }

    @Test
    fun `seal 产出非空 Base64 字符串`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "123456",
            sessionId = "s1", timestamp = 1000L
        )
        val peerKey = ByteArray(64) { it.toByte() }

        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns ByteArray(32) { 0x42 }
        coEvery { cryptoEngine.sign(any()) } returns ByteArray(64) { 0x01 }

        val result = envelopeCrypto.seal(envelope, peerKey)

        assertTrue(result.isNotEmpty())
        assertDoesNotThrow { java.util.Base64.getDecoder().decode(result) }
    }

    @Test
    fun `round-trip seal 然后 open 返回原始信封`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "654321",
            sessionId = "s2", timestamp = 2000L
        )
        val peerKey = ByteArray(64) { it.toByte() }
        val sharedSecret = ByteArray(32) { 0x42 }
        val signature = ByteArray(64) { 0x01 }

        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns sharedSecret
        coEvery { cryptoEngine.sign(any()) } returns signature
        coEvery { cryptoEngine.verify(any(), any(), any()) } returns true

        val sealed = envelopeCrypto.seal(envelope, peerKey)
        val opened = envelopeCrypto.open(sealed, peerKey)

        assertNotNull(opened)
        assertEquals(envelope.type, opened!!.type)
        assertEquals(envelope.code, opened.code)
        assertEquals(envelope.sessionId, opened.sessionId)
        assertEquals(envelope.timestamp, opened.timestamp)
    }

    @Test
    fun `open 篡改密文返回 null`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "111111",
            sessionId = "s3", timestamp = 3000L
        )
        val peerKey = ByteArray(64) { it.toByte() }
        val sharedSecret = ByteArray(32) { 0x42 }
        val signature = ByteArray(64) { 0x01 }

        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns sharedSecret
        coEvery { cryptoEngine.sign(any()) } returns signature
        coEvery { cryptoEngine.verify(any(), any(), any()) } returns false

        val sealed = envelopeCrypto.seal(envelope, peerKey)
        val opened = envelopeCrypto.open(sealed, peerKey)

        assertNull(opened)
    }

    @Test
    fun `open 错误公钥返回 null`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "222222",
            sessionId = "s4", timestamp = 4000L
        )
        val peerKey = ByteArray(64) { it.toByte() }
        val wrongKey = ByteArray(64) { (it + 100).toByte() }
        val sharedSecret = ByteArray(32) { 0x42 }
        val signature = ByteArray(64) { 0x01 }

        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns sharedSecret
        coEvery { cryptoEngine.sign(any()) } returns signature
        coEvery { cryptoEngine.verify(any(), any(), peerKey) } returns true
        coEvery { cryptoEngine.verify(any(), any(), wrongKey) } returns false

        val sealed = envelopeCrypto.seal(envelope, peerKey)
        val opened = envelopeCrypto.open(sealed, wrongKey)

        assertNull(opened)
    }
}
