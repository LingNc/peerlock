package com.peerlock.data.pairing

import com.peerlock.data.prefs.SecurePrefs
import java.util.Base64

class PairingRepositoryImpl(
    private val securePrefs: SecurePrefs,
) : PairingRepository {

    override suspend fun storeMyPublicKey(publicKey: ByteArray) {
        securePrefs.myPublicKey = Base64.getEncoder().withoutPadding().encodeToString(publicKey)
    }

    override suspend fun getMyPublicKey(): ByteArray? {
        return securePrefs.myPublicKey?.let {
            Base64.getDecoder().decode(it)
        }
    }

    override suspend fun storePeerPublicKey(publicKey: ByteArray) {
        securePrefs.peerPublicKey = Base64.getEncoder().withoutPadding().encodeToString(publicKey)
    }

    override suspend fun getPeerPublicKey(): ByteArray? {
        return securePrefs.peerPublicKey?.let {
            Base64.getDecoder().decode(it)
        }
    }

    override suspend fun storeSessionId(sessionId: String) {
        securePrefs.sessionId = sessionId
    }

    override suspend fun getSessionId(): String = securePrefs.sessionId ?: ""

    override suspend fun storeRole(role: String) {
        securePrefs.role = role
    }

    override suspend fun getRole(): String = securePrefs.role ?: ""

    override suspend fun markPaired() {
        securePrefs.isPaired = true
    }

    override suspend fun isPaired(): Boolean = securePrefs.isPaired

    override suspend fun clearPairing() {
        securePrefs.clearPairingData()
    }
}
