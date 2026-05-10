package com.peerlock.domain.request

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.EnvelopeCrypto
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEnvelope
import java.time.LocalTime
import java.util.UUID

class RequestProtocolImpl(
    private val envelopeCrypto: EnvelopeCrypto,
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val policyEngine: PolicyEngine,
    private val storageRepository: StorageRepository,
    private val requestGuard: RequestGuard,
    private val securePrefs: SecurePrefs,
) : RequestProtocol {

    override suspend fun generateUnlockRequest(
        sessionId: String,
        targetPackage: String,
        requestedDuration: Int,
        durationMode: String,
        reason: String?,
        deviceInfo: DeviceInfo,
    ): String? {
        if (!requestGuard.checkRateLimit("unlock")) return null
        val seed = seedManager.retrieveSeed(KeyType.UNLOCK) ?: return null

        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "unlock",
            code = code,
            sessionId = sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        requestGuard.recordRequest("unlock")
        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun generateConfigRequest(
        sessionId: String,
        changes: List<PolicyChange>,
        reason: String?,
        deviceInfo: DeviceInfo,
    ): String? {
        if (!requestGuard.checkRateLimit("config")) return null
        val seed = seedManager.retrieveSeed(KeyType.SETTING) ?: return null

        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "setting",
            code = code,
            sessionId = sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        requestGuard.recordRequest("config")
        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun processRequest(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ProcessResult {
        val envelope = envelopeCrypto.open(sealedBase64, peerPublicKey)
            ?: return ProcessResult.Error("信封解密或签名验证失败")

        if (!requestGuard.isEnvelopeValid(envelope.timestamp)) {
            return ProcessResult.Error("信封已过期")
        }

        val request = RequestEnvelope(
            type = envelope.type,
            sessionId = envelope.sessionId,
            requestId = UUID.randomUUID().toString(),
            timestamp = envelope.timestamp,
            deviceInfo = DeviceInfo(
                todayScreenTimeMs = 0,
                suspendedApps = emptyList(),
                isInSafeMode = false,
            ),
            payload = RequestPayload.UnlockRequest(
                targetPackage = "",
                requestedDuration = 0,
                durationMode = "cumulative",
            )
        )

        return ProcessResult.Success(request)
    }

    override suspend fun generateUnlockResponse(
        request: RequestEnvelope,
        approved: Boolean,
        duration: Int?,
        durationMode: String?,
    ): String? {
        val seed = seedManager.retrieveSeed(KeyType.UNLOCK) ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "unlock",
            code = code,
            sessionId = request.sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun generateConfigResponse(
        request: RequestEnvelope,
        approved: Boolean,
        changes: List<PolicyChange>?,
        rejectReason: String?,
    ): String? {
        val seed = seedManager.retrieveSeed(KeyType.SETTING) ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "setting",
            code = code,
            sessionId = request.sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun processResponse(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ResponseResult {
        val envelope = envelopeCrypto.open(sealedBase64, peerPublicKey)
            ?: return ResponseResult.Error("信封解密或签名验证失败")

        if (!requestGuard.isEnvelopeValid(envelope.timestamp)) {
            return ResponseResult.Error("信封已过期")
        }

        if (requestGuard.isConsumed(envelope.sessionId + envelope.timestamp)) {
            return ResponseResult.Error("此信封已被使用")
        }

        requestGuard.markConsumed(envelope.sessionId + envelope.timestamp)

        val response = ResponseEnvelope(
            type = envelope.type,
            sessionId = envelope.sessionId,
            requestId = "",
            timestamp = envelope.timestamp,
            approved = true,
            payload = ResponsePayload.UnlockResponse(
                targetPackage = "",
                duration = 30,
                durationMode = "cumulative",
            )
        )

        return ResponseResult.Success(response)
    }

    override suspend fun executeResponse(response: ResponseEnvelope): Boolean {
        if (!response.approved) return false

        return when (val payload = response.payload) {
            is ResponsePayload.UnlockResponse -> {
                policyEngine.recordUnlock(payload.targetPackage, payload.duration)
                policyEngine.unsuspendApp(payload.targetPackage)
                true
            }
            is ResponsePayload.ConfigResponse -> {
                for (change in payload.changes) {
                    storageRepository.upsertPolicy(
                        RestrictionPolicy(
                            targetPackage = change.targetPackage,
                            dailyLimitMinutes = change.dailyLimitMinutes,
                            allowedTimeStart = change.allowedTimeStart?.let { LocalTime.parse(it) },
                            allowedTimeEnd = change.allowedTimeEnd?.let { LocalTime.parse(it) },
                            isBlacklist = change.isBlacklist ?: true,
                            isActive = true,
                            createdAt = System.currentTimeMillis(),
                            lastModified = System.currentTimeMillis(),
                        )
                    )
                }
                true
            }
            is ResponsePayload.Rejected -> false
        }
    }
}
