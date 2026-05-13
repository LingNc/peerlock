package com.peerlock.domain.pairing

import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.db.entity.PairingSessionEntity
import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.crypto.CryptoKeyPair
import com.peerlock.domain.crypto.createCryptoEngineForCurve
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

class PairingProtocolImpl(
    private val cryptoEngine: CryptoEngine,
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val pairingRepository: PairingRepository,
    private val pairingSessionDao: PairingSessionDao,
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

        // 归档所有旧的 WAITING 会话
        val existing = pairingSessionDao.getByStatus("WAITING")
        for (old in existing) {
            pairingSessionDao.archive(old.sessionId)
        }

        // 创建 WAITING 会话记录
        val fingerprint = computeFingerprint(keyPair.publicKey)
        val entity = PairingSessionEntity(
            sessionId = sessionId,
            role = "controlled",
            peerDeviceName = "(等待配对)",
            peerPublicKey = "",
            myPublicKey = encodeBase64(keyPair.publicKey),
            signingPublicKey = null,
            encryptedSeeds = "{}",
            status = "WAITING",
            identityFingerprint = fingerprint,
            createdAt = System.currentTimeMillis(),
            peerCurve = cryptoEngine.curveName,
        )
        pairingSessionDao.insert(entity)

        return PairingRequest(
            id = sessionId,
            pub = encodeBase64(keyPair.publicKey),
            name = deviceName,
            exp = System.currentTimeMillis() / 1000 + 300L,
            curve = cryptoEngine.curveName,
        )
    }

    override suspend fun processPairRequest(
        request: PairingRequest,
        controllerName: String,
    ): PairingResponse {
        if (request.exp > 0) {
            val nowSeconds = System.currentTimeMillis() / 1000
            if (nowSeconds > request.exp) {
                throw IllegalStateException("配对请求已过期")
            }
        }

        val peerPublicKey = decodeBase64(request.pub)

        val myExistingPub = pairingRepository.getMyPublicKey()
        if (myExistingPub != null && peerPublicKey.contentEquals(myExistingPub)) {
            throw IllegalStateException("不能与自己配对")
        }

        val seeds = seedManager.generateSeeds()

        // 使用对方曲线引擎生成 ECDH 密钥对（用于与对方 ECDH 公钥协商）
        val peerCurve = request.curve
        val peerEngine = createCryptoEngineForCurve(peerCurve)
        val peerCurveKeyPair = peerEngine.generateKeyPair()

        // 用自身引擎生成签名密钥对
        val signKeyPair = cryptoEngine.generateKeyPair()
        myKeyPair = signKeyPair

        val payload = PairingPayload(
            id = request.id,
            seeds = seeds.mapKeys { it.key.name.lowercase() }
                .mapValues { encodeBase64(it.value) },
            pub = encodeBase64(peerCurveKeyPair.publicKey),
            name = controllerName,
        )

        val plaintext = json.encodeToString(PairingPayload.serializer(), payload).toByteArray()
        val ciphertext = peerEngine.encrypt(plaintext, peerPublicKey)

        val iv = ciphertext.sliceArray(0 until 12)
        val actualCiphertext = ciphertext.sliceArray(12 until ciphertext.size)

        val toSign = iv + actualCiphertext
        val signature = cryptoEngine.sign(toSign)

        val envelope = EnvelopeCodec.encode(actualCiphertext, iv, signature)
        val envelopeBase64 = encodeBase64(envelope)

        pairingRepository.storeMyPublicKey(signKeyPair.publicKey)
        pairingRepository.storePeerPublicKey(peerPublicKey)
        pairingRepository.storeSessionId(request.id)
        pairingRepository.storeRole("controller")
        seedManager.storeSeeds(seeds)

        val fingerprint = computeFingerprint(peerPublicKey)
        pairingRepository.createSession(
            sessionId = request.id,
            role = "controller",
            peerDeviceName = request.name,
            peerPublicKey = peerPublicKey,
            myPublicKey = peerCurveKeyPair.publicKey,
            signingPublicKey = signKeyPair.signingPublicKey,
            identityFingerprint = fingerprint,
            peerCurve = peerCurve,
        )

        return PairingResponse(
            pub = encodeBase64(peerCurveKeyPair.publicKey),
            signPub = encodeBase64(signKeyPair.signingPublicKey),
            data = envelopeBase64,
            curve = cryptoEngine.curveName,
        )
    }

    override suspend fun processPairResponse(response: PairingResponse): PairingResult {
        val storedSessionId = pairingRepository.getSessionId()

        return try {
            val controllerEcdhPubKey = decodeBase64(response.pub)
            val controllerSignPubKey = decodeBase64(response.signPub)

            val myExistingPub = pairingRepository.getMyPublicKey()
            if (myExistingPub != null && controllerEcdhPubKey.contentEquals(myExistingPub)) {
                return PairingResult.Error("不能与自己配对")
            }

            // 使用对方曲线引擎进行签名验证
            val peerCurve = response.curve
            val verifyEngine = createCryptoEngineForCurve(peerCurve)

            // 解密使用自身引擎（持有自身曲线的私钥，对方加密时使用了本方曲线）
            val decryptEngine = cryptoEngine

            val envelope = decodeBase64(response.data)
            val (actualCiphertext, iv, signature) = EnvelopeCodec.decode(envelope)

            val toVerify = iv + actualCiphertext
            if (!verifyEngine.verify(toVerify, signature, controllerSignPubKey)) {
                return PairingResult.Error("签名验证失败，数据可能被篡改")
            }

            val encryptedData = iv + actualCiphertext
            val plaintext = decryptEngine.decryptWithPeer(encryptedData, controllerEcdhPubKey)

            val payloadString = plaintext.toString(Charsets.UTF_8)
            val payload = json.decodeFromString(PairingPayload.serializer(), payloadString)

            if (!storedSessionId.isNullOrBlank() && payload.id != storedSessionId) {
                return PairingResult.Error("会话 ID 不匹配")
            }

            val seeds = payload.seeds.mapKeys { KeyType.fromTotpId(it.key) }
                .mapValues { decodeBase64(it.value) }
            seedManager.storeSeeds(seeds)

            pairingRepository.storePeerPublicKey(controllerEcdhPubKey)

            pairingRepository.markPaired()

            val myPub = pairingRepository.getMyPublicKey()
            val fingerprint = computeFingerprint(controllerEcdhPubKey)

            // 更新 WAITING 会话为 ACTIVE，若无则新建
            val waitingSession = pairingSessionDao.getById(payload.id)
            if (waitingSession != null && waitingSession.status == "WAITING") {
                val updated = waitingSession.copy(
                    status = "ACTIVE",
                    peerDeviceName = payload.name,
                    peerPublicKey = encodeBase64(controllerEcdhPubKey),
                    signingPublicKey = encodeBase64(controllerSignPubKey),
                    identityFingerprint = fingerprint,
                    peerCurve = peerCurve,
                )
                pairingSessionDao.update(updated)
            } else {
                pairingRepository.createSession(
                    sessionId = payload.id,
                    role = "controlled",
                    peerDeviceName = payload.name,
                    peerPublicKey = controllerEcdhPubKey,
                    myPublicKey = myPub ?: ByteArray(0),
                    signingPublicKey = controllerSignPubKey,
                    identityFingerprint = fingerprint,
                    peerCurve = peerCurve,
                )
            }

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

    private fun computeFingerprint(publicKey: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
