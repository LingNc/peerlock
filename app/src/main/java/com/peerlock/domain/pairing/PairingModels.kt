package com.peerlock.domain.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 配对请求（被控端 -> 控制端）。
 * 被控端生成 ECC 密钥对后，将公钥编码为二维码展示给控制端。
 */
@Serializable
data class PairingRequest(
    val v: Int = 1,
    val type: String = "pair_req",
    val id: String,              // 配对会话 ID（UUID）
    val pub: String,             // Base64 编码的 ECC 公钥
    val name: String,            // 设备标识（MANUFACTURER + MODEL）
)

/**
 * 配对响应（控制端 -> 被控端）。
 * 控制端生成种子和密钥对后，用被控端公钥加密，展示回传二维码。
 * data 字段为 Base64 编码的加密信封。
 */
@Serializable
data class PairingResponse(
    val v: Int = 1,
    val data: String,            // Base64(加密信封)
)

/**
 * 加密信封内部的明文 payload（加密前）。
 */
@Serializable
data class PairingPayload(
    val v: Int = 1,
    val type: String = "pair_resp",
    val id: String,              // 配对会话 ID（与请求匹配）
    val seeds: Map<String, String>,  // KeyType.name -> Base64(种子)
    val pub: String,             // Base64(控制端 ECC 公钥)
    val name: String,            // 控制端设备标识
)

/**
 * 配对结果。
 */
sealed class PairingResult {
    data class Success(
        val sessionId: String,
        val role: String,
    ) : PairingResult()

    data class Error(val reason: String) : PairingResult()
}

/**
 * 加密信封的二进制格式：
 * [version:1B][ciphertext_len:2B][ciphertext][iv:12B][signature]
 *
 * - version: 固定 0x01
 * - ciphertext_len: 大端序 uint16
 * - ciphertext: AES-256-GCM 密文（含 16 字节 tag）
 * - iv: 12 字节随机 IV
 * - signature: ECDSA/Ed25519 签名（覆盖 iv + ciphertext）
 */
object EnvelopeCodec {
    private const val VERSION = 1
    private const val IV_SIZE = 12

    fun encode(ciphertext: ByteArray, iv: ByteArray, signature: ByteArray): ByteArray {
        require(iv.size == IV_SIZE) { "IV 必须为 $IV_SIZE 字节" }
        val version = byteArrayOf(VERSION.toByte())
        val len = byteArrayOf(
            (ciphertext.size shr 8).toByte(),
            (ciphertext.size and 0xFF).toByte()
        )
        return version + len + ciphertext + iv + signature
    }

    fun decode(envelope: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        require(envelope.size > 1 + 2 + IV_SIZE) { "信封数据过短" }
        require(envelope[0].toInt() == VERSION) { "不支持的信封版本: ${envelope[0]}" }
        val ciphertextLen = ((envelope[1].toInt() and 0xFF) shl 8) or (envelope[2].toInt() and 0xFF)
        val expectedSize = 1 + 2 + ciphertextLen + IV_SIZE
        require(envelope.size > expectedSize) { "信封数据不完整" }

        val ciphertext = envelope.sliceArray(3 until 3 + ciphertextLen)
        val iv = envelope.sliceArray(3 + ciphertextLen until 3 + ciphertextLen + IV_SIZE)
        val signature = envelope.sliceArray(3 + ciphertextLen + IV_SIZE until envelope.size)
        return Triple(ciphertext, iv, signature)
    }
}
