package com.peerlock.domain.emergency

/**
 * 紧急逃生管理接口。
 * L1：终止码（应用内 TOTP 验证，解除配对，保留 DO + 数据）
 * L2：高级解除（一次性 ADB broadcast + nonce，移除 DO + 密钥，保留数据）
 */
interface EmergencyManager {
    /** 验证终止码（L1），TOTP ±1 窗口，5次错误→30秒锁定 */
    suspend fun verifyDestroyCode(code: String): Boolean

    /** 执行 L1 终止：清除加密材料和配对状态，保留 DO + 用户数据 */
    suspend fun executeDestroy(): Boolean

    /** 生成 L2 一次性 nonce（16位 hex，5分钟过期） */
    fun generateNonce(): String

    /** 验证 L2 nonce 并执行解除（移除 DO + 清除密钥，保留数据） */
    suspend fun verifyAndExecuteEmergency(nonce: String): Boolean
}
