package com.peerlock.domain.crypto

interface CryptoEngine {
    suspend fun generateKeyPair(): CryptoKeyPair
    suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray
    suspend fun decrypt(data: ByteArray): ByteArray
    suspend fun sign(data: ByteArray): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean
    suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray
}

data class CryptoKeyPair(
    val publicKey: ByteArray,
    val privateKeyAlias: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CryptoKeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKeyAlias == other.privateKeyAlias
    }
    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + privateKeyAlias.hashCode()
}
