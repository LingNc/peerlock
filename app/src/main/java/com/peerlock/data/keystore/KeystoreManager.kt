package com.peerlock.data.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 管理 Android Keystore 操作。
 * 所有密钥由硬件安全模块保护，不可导出。
 */
class KeystoreManager {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    fun generateEccKeyPair(alias: String = KeyAlias.ECC_KEY_PAIR): PublicKey {
        if (keyStore.containsAlias(alias)) {
            return keyStore.getCertificate(alias).publicKey
        }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT or
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(
                java.security.spec.ECGenParameterSpec("secp256r1")
            )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(false)
            .build()

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kpg.initialize(spec)
        val kp = kpg.generateKeyPair()
        return kp.public
    }

    fun getPrivateKey(alias: String = KeyAlias.ECC_KEY_PAIR): PrivateKey {
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey
    }

    fun generateAesKey(alias: String): SecretKey {
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(spec)
        return kg.generateKey()
    }

    fun encrypt(data: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    fun decrypt(data: ByteArray, secretKey: SecretKey): ByteArray {
        val iv = data.sliceArray(0 until 12)
        val ciphertext = data.sliceArray(12 until data.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            secretKey,
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        return cipher.doFinal(ciphertext)
    }

    fun encryptSeed(seed: ByteArray): ByteArray {
        val key = generateAesKey(KeyAlias.TOTP_SEED_ENCRYPTOR)
        return encrypt(seed, key)
    }

    fun decryptSeed(encryptedSeed: ByteArray): ByteArray {
        val key = generateAesKey(KeyAlias.TOTP_SEED_ENCRYPTOR)
        return decrypt(encryptedSeed, key)
    }

    fun generateDbPassphrase(): ByteArray {
        val key = generateAesKey(KeyAlias.DB_PASSPHRASE_KEY)
        val knownValue = "peerlock_db_v1".toByteArray()
        return encrypt(knownValue, key)
    }

    fun hasKey(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
