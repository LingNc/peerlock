package com.peerlock.domain.crypto

import android.os.Build

/**
 * 根据 Android API 级别自动选择加密后端：
 * - API 33+：X25519（密钥更短，Keystore 原生支持）
 * - API 30-32：secp256r1（硬件级 Keystore 支持）
 */
class AdaptiveCryptoEngine(
    private val apiLevel: Int = Build.VERSION.SDK_INT
) : CryptoEngine {

    private val delegate: CryptoEngine by lazy {
        if (apiLevel >= 33) X25519CryptoEngine() else P256CryptoEngine()
    }

    val curveName: String
        get() = if (apiLevel >= 33) "X25519" else "secp256r1"

    override suspend fun generateKeyPair(): CryptoKeyPair = delegate.generateKeyPair()
    override suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray =
        delegate.encrypt(data, peerPublicKey)
    override suspend fun decrypt(data: ByteArray): ByteArray = delegate.decrypt(data)
    override suspend fun sign(data: ByteArray): ByteArray = delegate.sign(data)
    override suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean =
        delegate.verify(data, signature, peerPublicKey)
    override suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray =
        delegate.deriveSharedSecret(peerPublicKey)
}
