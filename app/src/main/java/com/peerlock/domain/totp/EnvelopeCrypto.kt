package com.peerlock.domain.totp

import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.pairing.EnvelopeCodec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP 签名信封加密/解密。
 * 规范 6.2：ECDH + AES-256-GCM + ECDSA，二进制打包 + Base64。
 */
class EnvelopeCrypto(
    private val cryptoEngine: CryptoEngine,
) {
    companion object {
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12
        private const val AES_KEY_SIZE = 32
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 加密封装：TotpEnvelope → Base64 字符串。
     */
    suspend fun seal(envelope: TotpEnvelope, peerPublicKey: ByteArray): String {
        val plaintext = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }

        val sharedSecret = cryptoEngine.deriveSharedSecret(peerPublicKey)
        val aesKey = sharedSecret.copyOf(AES_KEY_SIZE)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_LENGTH, iv)
        )
        val ciphertext = cipher.doFinal(plaintext)

        val signature = cryptoEngine.sign(iv + ciphertext)

        val binary = EnvelopeCodec.encode(ciphertext, iv, signature)
        return java.util.Base64.getEncoder().withoutPadding().encodeToString(binary)
    }

    /**
     * 解密拆封：Base64 字符串 → TotpEnvelope。
     * @return 解密后的 TotpEnvelope，或 null（签名验证失败/解密失败）
     */
    suspend fun open(sealedBase64: String, peerPublicKey: ByteArray): TotpEnvelope? {
        return try {
            val binary = java.util.Base64.getDecoder().decode(sealedBase64)
            val (ciphertext, iv, signature) = EnvelopeCodec.decode(binary)

            val valid = cryptoEngine.verify(iv + ciphertext, signature, peerPublicKey)
            if (!valid) return null

            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPublicKey)
            val aesKey = sharedSecret.copyOf(AES_KEY_SIZE)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
            val decrypted = cipher.doFinal(ciphertext)
            json.decodeFromString<TotpEnvelope>(String(decrypted, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
