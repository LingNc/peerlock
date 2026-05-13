package com.peerlock.data.pairing

import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.db.entity.PairingSessionEntity
import com.peerlock.data.prefs.SecurePrefs
import java.util.Base64

class PairingRepositoryImpl(
    private val securePrefs: SecurePrefs,
    private val pairingSessionDao: PairingSessionDao,
) : PairingRepository {

    // === 旧接口（向后兼容，读写 SecurePrefs + 同步活跃会话）===

    override suspend fun storeMyPublicKey(publicKey: ByteArray) {
        securePrefs.myPublicKey = Base64.getEncoder().withoutPadding().encodeToString(publicKey)
    }

    override suspend fun getMyPublicKey(): ByteArray? {
        return securePrefs.myPublicKey?.let { Base64.getDecoder().decode(it) }
    }

    override suspend fun storePeerPublicKey(publicKey: ByteArray) {
        securePrefs.peerPublicKey = Base64.getEncoder().withoutPadding().encodeToString(publicKey)
    }

    override suspend fun getPeerPublicKey(): ByteArray? {
        return securePrefs.peerPublicKey?.let { Base64.getDecoder().decode(it) }
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

    // === 多会话接口 ===

    override suspend fun createSession(
        sessionId: String,
        role: String,
        peerDeviceName: String,
        peerPublicKey: ByteArray,
        myPublicKey: ByteArray,
        signingPublicKey: ByteArray?,
        identityFingerprint: String,
        peerCurve: String,
    ): PairingSessionEntity {
        val entity = PairingSessionEntity(
            sessionId = sessionId,
            role = role,
            peerDeviceName = peerDeviceName,
            peerPublicKey = Base64.getEncoder().withoutPadding().encodeToString(peerPublicKey),
            myPublicKey = Base64.getEncoder().withoutPadding().encodeToString(myPublicKey),
            signingPublicKey = signingPublicKey?.let { Base64.getEncoder().withoutPadding().encodeToString(it) },
            encryptedSeeds = "{}",
            status = "ACTIVE",
            identityFingerprint = identityFingerprint,
            createdAt = System.currentTimeMillis(),
            peerCurve = peerCurve,
        )
        pairingSessionDao.insert(entity)
        return entity
    }

    override suspend fun updateSessionStatus(sessionId: String, status: String) {
        val session = pairingSessionDao.getById(sessionId) ?: return
        val updated = session.copy(
            status = status,
            revokedAt = if (status == "REVOKED") System.currentTimeMillis() else session.revokedAt,
            archivedAt = if (status == "ARCHIVED") System.currentTimeMillis() else session.archivedAt,
        )
        pairingSessionDao.update(updated)
    }

    override suspend fun updateSessionSeeds(sessionId: String, encryptedSeeds: String) {
        val session = pairingSessionDao.getById(sessionId) ?: return
        pairingSessionDao.update(session.copy(encryptedSeeds = encryptedSeeds))
    }

    override suspend fun getSession(sessionId: String): PairingSessionEntity? =
        pairingSessionDao.getById(sessionId)

    override suspend fun getActiveSession(): PairingSessionEntity? =
        pairingSessionDao.getActiveSession()

    override suspend fun getActiveByRole(role: String): PairingSessionEntity? =
        pairingSessionDao.getActiveByRole(role)

    override suspend fun getAllSessions(): List<PairingSessionEntity> =
        pairingSessionDao.getAll()

    override suspend fun getNonArchived(): List<PairingSessionEntity> =
        pairingSessionDao.getNonArchived()

    override suspend fun hasActiveController(): Boolean =
        pairingSessionDao.getActiveByRole("controller") != null

    override suspend fun hasActiveControlled(): Boolean =
        pairingSessionDao.getActiveByRole("controlled") != null

    override suspend fun revokeSession(sessionId: String) {
        pairingSessionDao.markRevoked(sessionId)
    }

    override suspend fun archiveSession(sessionId: String) {
        pairingSessionDao.archive(sessionId)
    }

    override suspend fun deleteSession(sessionId: String) {
        pairingSessionDao.delete(sessionId)
    }
}
