package com.peerlock.domain.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * X25519/Ed25519 加密引擎，使用 Bouncy Castle。
 * 用于 API 30-32 设备（Keystore 不原生支持 X25519）。
 *
 * 注意：X25519 仅用于密钥协商，Ed25519 仅用于签名，二者使用不同密钥对。
 * - CryptoKeyPair.publicKey：X25519 公钥（32 字节），用于 ECDH
 * - ed25519PublicKey()：Ed25519 公钥（32 字节），用于签名验证
 */
class X25519CryptoEngine : CryptoEngine {

    private var x25519PrivateKey: X25519PrivateKeyParameters? = null
    private var edPrivateKey: Ed25519PrivateKeyParameters? = null
    private var edPublicKey: Ed25519PublicKeyParameters? = null

    companion object {
        private const val AES_KEY_SIZE = 32
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    override suspend fun generateKeyPair(): CryptoKeyPair {
        val random = SecureRandom()

        // X25519 密钥对（ECDH）
        val x25519Private = X25519PrivateKeyParameters(random)
        val x25519Public = x25519Private.generatePublicKey()

        // Ed25519 密钥对（签名）
        val edPrivate = Ed25519PrivateKeyParameters(random)
        val edPub = edPrivate.generatePublicKey()

        this.x25519PrivateKey = x25519Private
        this.edPrivateKey = edPrivate
        this.edPublicKey = edPub

        return CryptoKeyPair(
            publicKey = x25519Public.encoded,
            privateKeyAlias = "x25519_${System.nanoTime()}"
        )
    }

    /**
     * 获取 Ed25519 公钥（用于对方验证签名）。
     */
    fun ed25519PublicKey(): ByteArray {
        return edPublicKey?.encoded ?: throw IllegalStateException("尚未生成密钥对")
    }

    override suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentPrivate = x25519PrivateKey ?: throw IllegalStateException("尚未生成密钥对")
        val sharedSecret = ecdh(currentPrivate, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return iv + cipher.doFinal(data)
    }

    override suspend fun decrypt(data: ByteArray): ByteArray =
        throw NotImplementedError("请使用 decryptWithPeerKey")

    suspend fun decryptWithPeerKey(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentPrivate = x25519PrivateKey ?: throw IllegalStateException("尚未生成密钥对")
        val sharedSecret = ecdh(currentPrivate, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val iv = data.sliceArray(0 until GCM_IV_LENGTH)
        val ciphertext = data.sliceArray(GCM_IV_LENGTH until data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    override suspend fun sign(data: ByteArray): ByteArray {
        val currentEdPrivate = edPrivateKey ?: throw IllegalStateException("尚未生成密钥对")
        val signer = Ed25519Signer()
        signer.init(true, currentEdPrivate)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean {
        return try {
            val edPubKey = Ed25519PublicKeyParameters(peerPublicKey, 0)
            val signer = Ed25519Signer()
            signer.init(false, edPubKey)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray {
        val currentPrivate = x25519PrivateKey ?: throw IllegalStateException("尚未生成密钥对")
        return ecdh(currentPrivate, peerPublicKey)
    }

    private fun ecdh(privateKey: X25519PrivateKeyParameters, peerPublicKeyBytes: ByteArray): ByteArray {
        val peerPublicKey = X25519PublicKeyParameters(peerPublicKeyBytes, 0)
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(peerPublicKey, sharedSecret, 0)
        return sharedSecret
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(sharedSecret)
}
