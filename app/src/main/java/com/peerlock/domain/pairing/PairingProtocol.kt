package com.peerlock.domain.pairing

/**
 * 配对协议接口。
 * 纯 Kotlin，可在 JVM 上测试。
 */
interface PairingProtocol {
    /**
     * 被控端：生成配对请求。
     * 生成 ECC 密钥对，返回包含公钥的请求对象。
     */
    suspend fun generatePairRequest(deviceName: String): PairingRequest

    /**
     * 控制端：处理配对请求，生成配对响应。
     * 生成自己的密钥对和 3 个 TOTP 种子，用对方公钥加密后返回。
     */
    suspend fun processPairRequest(
        request: PairingRequest,
        controllerName: String,
    ): PairingResponse

    /**
     * 被控端：处理配对响应，完成配对。
     * 解密信封，验证签名，存储种子和对方公钥。
     */
    suspend fun processPairResponse(
        response: PairingResponse,
    ): PairingResult

    /**
     * 获取当前配对状态。
     */
    suspend fun isPaired(): Boolean

    /**
     * 获取当前会话 ID。
     */
    suspend fun getSessionId(): String?

    /**
     * 获取当前角色。
     */
    suspend fun getRole(): String?
}
