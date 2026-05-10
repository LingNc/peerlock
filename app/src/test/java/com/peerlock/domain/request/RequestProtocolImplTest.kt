package com.peerlock.domain.request

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.EnvelopeCrypto
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEnvelope
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequestProtocolImplTest {

    private lateinit var protocol: RequestProtocolImpl
    private lateinit var envelopeCrypto: EnvelopeCrypto
    private lateinit var totpEngine: TotpEngine
    private lateinit var seedManager: SeedManager
    private lateinit var policyEngine: PolicyEngine
    private lateinit var storageRepository: StorageRepository
    private lateinit var requestGuard: RequestGuard
    private lateinit var securePrefs: SecurePrefs

    private val peerPubKey = ByteArray(64) { it.toByte() }
    private val peerPubKeyBase64 = java.util.Base64.getEncoder().encodeToString(peerPubKey)

    @BeforeEach
    fun setup() {
        envelopeCrypto = mockk(relaxed = true)
        totpEngine = mockk(relaxed = true)
        seedManager = mockk(relaxed = true)
        policyEngine = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        securePrefs = mockk(relaxed = true)
        requestGuard = RequestGuard(securePrefs)

        coEvery { seedManager.retrieveSeed(any()) } returns ByteArray(32) { 0x01 }
        every { totpEngine.generateCode(any()) } returns "123456"
        every { securePrefs.peerPublicKey } returns peerPubKeyBase64
        coEvery { envelopeCrypto.seal(any(), any()) } returns "sealed-base64"

        protocol = RequestProtocolImpl(
            envelopeCrypto, totpEngine, seedManager, policyEngine,
            storageRepository, requestGuard, securePrefs
        )
    }

    @Test
    fun `generateUnlockRequest 频率限制内返回非空`() = runTest {
        val result = protocol.generateUnlockRequest(
            sessionId = "s1", targetPackage = "com.app1",
            requestedDuration = 30, durationMode = "cumulative",
            deviceInfo = DeviceInfo(0, emptyList(), false)
        )

        assertNotNull(result)
        coVerify { envelopeCrypto.seal(any(), peerPubKey) }
    }

    @Test
    fun `generateUnlockRequest 超频返回 null`() = runTest {
        requestGuard.recordRequest("unlock")

        val result = protocol.generateUnlockRequest(
            sessionId = "s1", targetPackage = "com.app1",
            requestedDuration = 30, durationMode = "cumulative",
            deviceInfo = DeviceInfo(0, emptyList(), false)
        )

        assertNull(result)
    }

    @Test
    fun `processRequest 信封过期返回 Error`() = runTest {
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 400
        coEvery { envelopeCrypto.open(any(), any()) } returns TotpEnvelope(
            type = "unlock", code = "123456",
            sessionId = "s1", timestamp = expiredTimestamp
        )

        val result = protocol.processRequest("sealed", peerPubKey)

        assertTrue(result is ProcessResult.Error)
    }

    @Test
    fun `processResponse 信封过期返回 Error`() = runTest {
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 400
        coEvery { envelopeCrypto.open(any(), any()) } returns TotpEnvelope(
            type = "unlock", code = "123456",
            sessionId = "s1", timestamp = expiredTimestamp
        )

        val result = protocol.processResponse("sealed", peerPubKey)

        assertTrue(result is ResponseResult.Error)
    }

    @Test
    fun `executeResponse UnlockResponse 调用 recordUnlock 和 unsuspendApp`() = runTest {
        val response = ResponseEnvelope(
            type = "unlock_resp", sessionId = "s1", requestId = "r1",
            timestamp = 1000L, approved = true,
            payload = ResponsePayload.UnlockResponse("com.app1", 30, "cumulative")
        )

        val result = protocol.executeResponse(response)

        assertTrue(result)
        coVerify { policyEngine.recordUnlock("com.app1", 30) }
        coVerify { policyEngine.unsuspendApp("com.app1") }
    }

    @Test
    fun `executeResponse Rejected 返回 false`() = runTest {
        val response = ResponseEnvelope(
            type = "unlock_resp", sessionId = "s1", requestId = "r1",
            timestamp = 1000L, approved = false,
            payload = ResponsePayload.Rejected("拒绝")
        )

        val result = protocol.executeResponse(response)

        assertFalse(result)
    }

    @Test
    fun `processResponse 已消费信封返回 Error`() = runTest {
        val nowSeconds = System.currentTimeMillis() / 1000
        coEvery { envelopeCrypto.open(any(), any()) } returns TotpEnvelope(
            type = "unlock", code = "123456",
            sessionId = "s1", timestamp = nowSeconds
        )
        every { securePrefs.consumedEnvelopes } returns setOf("s1$nowSeconds")

        val result = protocol.processResponse("sealed", peerPubKey)

        assertTrue(result is ResponseResult.Error)
    }
}
