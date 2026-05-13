package com.peerlock.domain.crypto

interface CryptoEngine {
    val curveName: String
    suspend fun generateKeyPair(): CryptoKeyPair
    suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray
    suspend fun decrypt(data: ByteArray): ByteArray
    suspend fun sign(data: ByteArray): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean
    suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray
    suspend fun decryptWithPeer(data: ByteArray, peerPublicKey: ByteArray): ByteArray
}

data class CryptoKeyPair(
    val publicKey: ByteArray,
    val signingPublicKey: ByteArray = publicKey, // P256: same as publicKey; X25519: Ed25519 key
    val privateKeyAlias: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CryptoKeyPair) return false
        return publicKey.contentEquals(other.publicKey) &&
            signingPublicKey.contentEquals(other.signingPublicKey) &&
            privateKeyAlias == other.privateKeyAlias
    }
    override fun hashCode(): Int =
        31 * (31 * publicKey.contentHashCode() + signingPublicKey.contentHashCode()) + privateKeyAlias.hashCode()
}

fun createCryptoEngineForCurve(curve: String): CryptoEngine = when (curve) {
    "X25519" -> X25519CryptoEngine()
    else -> P256CryptoEngine()
}
