package com.peerlock.domain.pairing

import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.crypto.CryptoKeyPair
import com.peerlock.domain.crypto.P256CryptoEngine
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEngineImpl
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PairingProtocolImplTest {

    private lateinit var controlledProtocol: PairingProtocolImpl
    private lateinit var controllerProtocol: PairingProtocolImpl

    private lateinit var controlledCrypto: CryptoEngine
    private lateinit var controllerCrypto: CryptoEngine
    private lateinit var controlledRepo: PairingRepository
    private lateinit var controllerRepo: PairingRepository
    private lateinit var controlledSeeds: SeedManager
    private lateinit var controllerSeeds: SeedManager
    private lateinit var totpEngine: TotpEngine

    @BeforeEach
    fun setup() {
        controlledCrypto = P256CryptoEngine()
        controllerCrypto = P256CryptoEngine()
        controlledRepo = mockk(relaxed = true)
        controllerRepo = mockk(relaxed = true)
        controlledSeeds = mockk(relaxed = true)
        controllerSeeds = mockk(relaxed = true)
        totpEngine = TotpEngineImpl()

        controlledProtocol = PairingProtocolImpl(
            controlledCrypto, totpEngine, controlledSeeds, controlledRepo
        )
        controllerProtocol = PairingProtocolImpl(
            controllerCrypto, totpEngine, controllerSeeds, controllerRepo
        )
    }

    @Test
    fun `生成配对请求应包含有效公钥和会话 ID`() = runTest {
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        assertEquals("pair_req", request.type)
        assertEquals(1, request.v)
        assertTrue(request.id.isNotBlank())
        assertTrue(request.pub.isNotBlank())
        assertEquals("Pixel 7", request.name)
    }

    @Test
    fun `处理配对请求应生成有效响应`() = runTest {
        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )

        val request = controlledProtocol.generatePairRequest("Pixel 7")
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        assertTrue(response.data.isNotBlank())
    }

    @Test
    fun `完整配对流程应成功`() = runTest {
        // 被控端生成请求
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        // 控制端处理请求
        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        // 被控端处理响应
        coEvery { controlledRepo.getSessionId() } returns request.id
        coEvery { controlledRepo.getMyPublicKey() } returns ByteArray(32) { it.toByte() }
        coEvery { controlledRepo.createSession(any(), any(), any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        val result = controlledProtocol.processPairResponse(response)

        assertTrue(result is PairingResult.Success, "配对应成功，实际: $result")
        result as PairingResult.Success
        assertEquals(request.id, result.sessionId)
        assertEquals("controlled", result.role)
    }

    @Test
    fun `篡改密文后配对应失败`() = runTest {
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        // 篡改 data
        val tamperedResponse = PairingResponse(pub = response.pub, signPub = response.signPub, data = response.data + "X")

        coEvery { controlledRepo.getSessionId() } returns request.id
        val result = controlledProtocol.processPairResponse(tamperedResponse)

        assertTrue(result is PairingResult.Error, "篡改后配对应失败")
    }

    @Test
    fun `会话 ID 不匹配时配对应失败`() = runTest {
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        // 返回错误的会话 ID
        coEvery { controlledRepo.getSessionId() } returns "wrong-session-id"
        val result = controlledProtocol.processPairResponse(response)

        assertTrue(result is PairingResult.Error)
        assertTrue((result as PairingResult.Error).reason.contains("会话"))
    }
}
