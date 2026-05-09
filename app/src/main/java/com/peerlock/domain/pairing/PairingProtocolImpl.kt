package com.peerlock.domain.pairing

import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.crypto.CryptoKeyPair
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

class PairingProtocolImpl(
    private val cryptoEngine: CryptoEngine,
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val pairingRepository: PairingRepository,
) : PairingProtocol {

    private val json = Json { ignoreUnknownKeys = true }
    private var myKeyPair: CryptoKeyPair? = null

    override suspend fun generatePairRequest(deviceName: String): PairingRequest {
        val keyPair = cryptoEngine.generateKeyPair()
        myKeyPair = keyPair
        val sessionId = UUID.randomUUID().toString()

        pairingRepository.storeMyPublicKey(keyPair.publicKey)
        pairingRepository.storeSessionId(sessionId)
        pairingRepository.storeRole("controlled")

        return PairingRequest(
            id = sessionId,
            pub = encodeBase64(keyPair.publicKey),
            name = deviceName,
        )
    }

    override suspend fun processPairRequest(
        request: PairingRequest,
        controllerName: String,
    ): PairingResponse {
        val peerPublicKey = decodeBase64(request.pub)

        val seeds = seedManager.generateSeeds()

        val keyPair = cryptoEngine.generateKeyPair()
        myKeyPair = keyPair

        val payload = PairingPayload(
            id = request.id,
            seeds = seeds.mapKeys { it.key.name.lowercase() }
                .mapValues { encodeBase64(it.value) },
            pub = encodeBase64(keyPair.publicKey),
            name = controllerName,
        )

        val plaintext = json.encodeToString(PairingPayload.serializer(), payload).toByteArray()
        val ciphertext = cryptoEngine.encrypt(plaintext, peerPublicKey)

        // 提取 IV（前 12 字节）和实际密文
        val iv = ciphertext.sliceArray(0 until 12)
        val actualCiphertext = ciphertext.sliceArray(12 until ciphertext.size)

        // 签名覆盖 iv + ciphertext
        val toSign = iv + actualCiphertext
        val signature = cryptoEngine.sign(toSign)

        val envelope = EnvelopeCodec.encode(actualCiphertext, iv, signature)
        val envelopeBase64 = encodeBase64(envelope)

        // 存储本地状态
        pairingRepository.storeMyPublicKey(keyPair.publicKey)
        pairingRepository.storePeerPublicKey(peerPublicKey)
        pairingRepository.storeSessionId(request.id)
        pairingRepository.storeRole("controller")
        seedManager.storeSeeds(seeds)

        return PairingResponse(
            pub = encodeBase64(keyPair.publicKey),
            signPub = encodeBase64(keyPair.signingPublicKey),
            data = envelopeBase64,
        )
    }

    override suspend fun processPairResponse(response: PairingResponse): PairingResult {
        val storedSessionId = pairingRepository.getSessionId()

        return try {
            val controllerEcdhPubKey = decodeBase64(response.pub)
            val controllerSignPubKey = decodeBase64(response.signPub)

            val envelope = decodeBase64(response.data)
            val (actualCiphertext, iv, signature) = EnvelopeCodec.decode(envelope)

            // 验证签名（覆盖 iv + ciphertext）
            val toVerify = iv + actualCiphertext
            if (!cryptoEngine.verify(toVerify, signature, controllerSignPubKey)) {
                return PairingResult.Error("签名验证失败，数据可能被篡改")
            }

            // 解密：还原为 encrypt() 的输出格式（iv + ciphertext）
            val encryptedData = iv + actualCiphertext
            val plaintext = cryptoEngine.decryptWithPeer(encryptedData, controllerEcdhPubKey)

            val payloadString = plaintext.toString(Charsets.UTF_8)
            val payload = json.decodeFromString(PairingPayload.serializer(), payloadString)

            // 验证会话 ID
            if (!storedSessionId.isNullOrBlank() && payload.id != storedSessionId) {
                return PairingResult.Error("会话 ID 不匹配")
            }

            // 存储种子
            val seeds = payload.seeds.mapKeys { KeyType.fromTotpId(it.key) }
                .mapValues { decodeBase64(it.value) }
            seedManager.storeSeeds(seeds)

            // 存储对方公钥
            pairingRepository.storePeerPublicKey(controllerEcdhPubKey)

            // 标记配对完成
            pairingRepository.markPaired()

            // 清除内存中的明文种子
            seeds.values.forEach { it.fill(0) }

            PairingResult.Success(
                sessionId = payload.id,
                role = "controlled",
            )
        } catch (e: Exception) {
            PairingResult.Error("配对失败: ${e.message}")
        }
    }

    override suspend fun isPaired(): Boolean = pairingRepository.isPaired()
    override suspend fun getSessionId(): String? = pairingRepository.getSessionId()
    override suspend fun getRole(): String? = pairingRepository.getRole()

    private fun encodeBase64(data: ByteArray): String =
        Base64.getEncoder().withoutPadding().encodeToString(data)

    private fun decodeBase64(encoded: String): ByteArray =
        Base64.getDecoder().decode(encoded)
}
