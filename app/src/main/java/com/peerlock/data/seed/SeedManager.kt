package com.peerlock.data.seed

import com.peerlock.domain.totp.KeyType

/**
 * TOTP 种子管理器。
 * 负责生成随机种子、加密存储、解密读取。
 */
interface SeedManager {
    /**
     * 生成 3 个独立的随机 TOTP 种子（每个 20 字节）。
     * 返回 KeyType -> 种子明文 的映射。
     */
    fun generateSeeds(): Map<KeyType, ByteArray>

    /**
     * 加密种子并存储到当前活跃会话。
     * 若无活跃会话，回退到 SecurePrefs（向后兼容）。
     */
    suspend fun storeSeeds(seeds: Map<KeyType, ByteArray>)

    /**
     * 读取并解密指定类型的种子（从当前活跃会话）。
     * 未配对时返回 null。
     */
    suspend fun retrieveSeed(keyType: KeyType): ByteArray?

    /**
     * 清除当前活跃会话的所有种子。
     */
    suspend fun clearSeeds()

    /**
     * 加密种子并存储到指定会话。
     */
    suspend fun storeSeedsForSession(sessionId: String, seeds: Map<KeyType, ByteArray>)

    /**
     * 从指定会话读取并解密指定类型的种子。
     */
    suspend fun retrieveSeedFromSession(sessionId: String, keyType: KeyType): ByteArray?

    /**
     * 清除指定会话的所有种子。
     */
    suspend fun clearSeedsForSession(sessionId: String)

    /**
     * 将 SecurePrefs 中的旧种子迁移到指定会话的 Room 存储。
     */
    suspend fun migrateSeedsToSession(sessionId: String)
}
