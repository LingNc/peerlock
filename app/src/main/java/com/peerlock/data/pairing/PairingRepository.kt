package com.peerlock.data.pairing

import com.peerlock.data.db.entity.PairingSessionEntity

/**
 * 配对状态存储接口。
 * 管理密钥对、对方公钥、会话状态（多设备支持）。
 */
interface PairingRepository {
    // === 旧接口（向后兼容，操作当前活跃会话）===
    suspend fun storeMyPublicKey(publicKey: ByteArray)
    suspend fun getMyPublicKey(): ByteArray?
    suspend fun storePeerPublicKey(publicKey: ByteArray)
    suspend fun getPeerPublicKey(): ByteArray?
    suspend fun storeSessionId(sessionId: String)
    suspend fun getSessionId(): String?
    suspend fun storeRole(role: String)
    suspend fun getRole(): String?
    suspend fun markPaired()
    suspend fun isPaired(): Boolean
    suspend fun clearPairing()

    // === 多会话接口 ===
    suspend fun createSession(
        sessionId: String,
        role: String,
        peerDeviceName: String,
        peerPublicKey: ByteArray,
        myPublicKey: ByteArray,
        signingPublicKey: ByteArray?,
        identityFingerprint: String,
    ): PairingSessionEntity

    suspend fun updateSessionStatus(sessionId: String, status: String)
    suspend fun updateSessionSeeds(sessionId: String, encryptedSeeds: String)
    suspend fun getSession(sessionId: String): PairingSessionEntity?
    suspend fun getActiveSession(): PairingSessionEntity?
    suspend fun getActiveByRole(role: String): PairingSessionEntity?
    suspend fun getAllSessions(): List<PairingSessionEntity>
    suspend fun getNonArchived(): List<PairingSessionEntity>
    suspend fun hasActiveController(): Boolean
    suspend fun hasActiveControlled(): Boolean
    suspend fun revokeSession(sessionId: String)
    suspend fun archiveSession(sessionId: String)
    suspend fun deleteSession(sessionId: String)
}
