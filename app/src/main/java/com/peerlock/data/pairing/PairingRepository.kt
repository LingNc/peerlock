package com.peerlock.data.pairing

/**
 * 配对状态存储接口。
 * 管理密钥对、对方公钥、会话状态。
 */
interface PairingRepository {
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
}
