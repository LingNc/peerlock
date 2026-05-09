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
     * 加密种子并存储。
     */
    suspend fun storeSeeds(seeds: Map<KeyType, ByteArray>)

    /**
     * 读取并解密指定类型的种子。
     * 未配对时返回 null。
     */
    suspend fun retrieveSeed(keyType: KeyType): ByteArray?

    /**
     * 清除所有存储的种子。
     */
    suspend fun clearSeeds()
}
