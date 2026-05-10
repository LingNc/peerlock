package com.peerlock.domain.emergency

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import java.security.SecureRandom

class EmergencyManagerImpl(
    private val securePrefs: SecurePrefs,
    private val seedManager: SeedManager,
    private val totpEngine: TotpEngine,
    private val storageRepository: StorageRepository,
    private val deviceOwnerManager: DeviceOwnerManager,
) : EmergencyManager {

    override suspend fun verifyDestroyCode(code: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < securePrefs.totpLockedUntil) return false

        val seed = seedManager.retrieveSeed(KeyType.DESTROY) ?: return false
        val valid = totpEngine.verifyCode(seed, code, tolerance = 1)
        if (valid) {
            securePrefs.totpErrorCount = 0
            return true
        } else {
            val errors = securePrefs.totpErrorCount + 1
            securePrefs.totpErrorCount = errors
            if (errors >= 5) {
                securePrefs.totpLockedUntil = now + 30_000L
                securePrefs.totpErrorCount = 0
            }
            return false
        }
    }

    override suspend fun executeDestroy(): Boolean {
        clearCryptoMaterial()
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "DESTROY",
                targetPackage = null,
                detail = "L1 终止码执行：清除加密材料，解除配对"
            )
        )
        return true
    }

    override fun generateNonce(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        val nonce = bytes.joinToString("") { "%02x".format(it) }
        securePrefs.emergencyNonce = nonce
        securePrefs.emergencyNonceExpiry = System.currentTimeMillis() + NONCE_EXPIRY_MS
        return nonce
    }

    override suspend fun verifyAndExecuteEmergency(nonce: String): Boolean {
        val storedNonce = securePrefs.emergencyNonce ?: return false
        val expiry = securePrefs.emergencyNonceExpiry
        if (System.currentTimeMillis() > expiry) return false
        if (nonce != storedNonce) return false

        securePrefs.emergencyNonce = null
        securePrefs.emergencyNonceExpiry = 0L

        clearCryptoMaterial()
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "EMERGENCY_L2",
                targetPackage = null,
                detail = "L2 高级解除执行：清除加密材料，标记移除 DO"
            )
        )
        return true
    }

    private fun clearCryptoMaterial() {
        securePrefs.peerPublicKey = null
        securePrefs.myPublicKey = null
        securePrefs.encryptedSeedSetting = null
        securePrefs.encryptedSeedUnlock = null
        securePrefs.encryptedSeedDestroy = null
        securePrefs.sessionId = null
        securePrefs.isPaired = false
        securePrefs.consumedEnvelopes = emptySet()
        securePrefs.totpErrorCount = 0
        securePrefs.totpLockedUntil = 0L
        securePrefs.safeModeActive = false
        securePrefs.safeModeReason = null
    }

    companion object {
        private const val NONCE_EXPIRY_MS = 300_000L
    }
}
