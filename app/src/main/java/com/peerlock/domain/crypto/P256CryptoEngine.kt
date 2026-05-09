package com.peerlock.domain.crypto

import java.math.BigInteger
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * secp256r1（NIST P-256）加密引擎。
 * 可在 JVM 上测试，使用内存密钥存储。
 *
 * 公钥格式：65 字节未压缩 EC 点（0x04 + 32 字节 X + 32 字节 Y）。
 */
class P256CryptoEngine : CryptoEngine {

    private var keyPair: KeyPair? = null
    private var ecParamSpec: ECParameterSpec? = null

    companion object {
        private const val EC_CURVE = "secp256r1"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val RAW_KEY_SIZE = 65
        private const val COORD_SIZE = 32
    }

    override suspend fun generateKeyPair(): CryptoKeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE))
        val kp = kpg.generateKeyPair()
        this.keyPair = kp
        this.ecParamSpec = (kp.public as java.security.interfaces.ECPublicKey).params

        return CryptoKeyPair(
            publicKey = toRawPublicKey(kp.public),
            privateKeyAlias = "p256_${System.nanoTime()}"
        )
    }

    override suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")
        val sharedSecret = ecdh(currentKeyPair.private, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return iv + cipher.doFinal(data)
    }

    override suspend fun decrypt(data: ByteArray): ByteArray =
        throw NotImplementedError("请使用 decryptWithPeer")

    override suspend fun decryptWithPeer(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")
        val sharedSecret = ecdh(currentKeyPair.private, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val iv = data.sliceArray(0 until GCM_IV_LENGTH)
        val ciphertext = data.sliceArray(GCM_IV_LENGTH until data.size)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    override suspend fun sign(data: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initSign(currentKeyPair.private)
        sig.update(data)
        return sig.sign()
    }

    override suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean {
        return try {
            val pubKey = fromRawPublicKey(peerPublicKey)
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(pubKey)
            sig.update(data)
            sig.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")
        return ecdh(currentKeyPair.private, peerPublicKey)
    }

    // ---- 内部辅助 ----

    private fun ecdh(privateKey: PrivateKey, peerRawPublicKey: ByteArray): ByteArray {
        val peerKey = fromRawPublicKey(peerRawPublicKey)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(peerKey, true)
        return ka.generateSecret()
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(sharedSecret)

    private fun toRawPublicKey(publicKey: PublicKey): ByteArray {
        val ecKey = publicKey as java.security.interfaces.ECPublicKey
        val x = bigIntToFixedBytes(ecKey.w.affineX, COORD_SIZE)
        val y = bigIntToFixedBytes(ecKey.w.affineY, COORD_SIZE)
        return byteArrayOf(0x04) + x + y
    }

    private fun fromRawPublicKey(raw: ByteArray): PublicKey {
        require(raw.size == RAW_KEY_SIZE && raw[0] == 0x04.toByte()) {
            "无效的 EC 公钥格式：期望 $RAW_KEY_SIZE 字节，实际 ${raw.size}"
        }
        val x = BigInteger(1, raw.sliceArray(1 until 1 + COORD_SIZE))
        val y = BigInteger(1, raw.sliceArray(1 + COORD_SIZE until RAW_KEY_SIZE))
        val params = ecParamSpec ?: throw IllegalStateException("尚未生成密钥对")
        return KeyFactory.getInstance("EC")
            .generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
    }

    private fun bigIntToFixedBytes(value: BigInteger, length: Int): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == length -> bytes
            bytes.size == length + 1 && bytes[0] == 0.toByte() ->
                bytes.sliceArray(1 until bytes.size)
            bytes.size < length ->
                ByteArray(length - bytes.size) + bytes
            else -> bytes.sliceArray(bytes.size - length until bytes.size)
        }
    }
}
