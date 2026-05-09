package com.peerlock.data.seed

import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.totp.KeyType
import java.security.SecureRandom
import java.util.Base64

class SeedManagerImpl(
    private val keystoreManager: KeystoreManager,
    private val securePrefs: SecurePrefs,
) : SeedManager {

    companion object {
        private const val SEED_LENGTH = 20 // 160 bit
    }

    override fun generateSeeds(): Map<KeyType, ByteArray> {
        val random = SecureRandom()
        return KeyType.entries.associateWith {
            ByteArray(SEED_LENGTH).also { random.nextBytes(it) }
        }
    }

    override suspend fun storeSeeds(seeds: Map<KeyType, ByteArray>) {
        for ((keyType, seed) in seeds) {
            val encrypted = keystoreManager.encryptSeed(seed)
            val encoded = Base64.getEncoder().withoutPadding().encodeToString(encrypted)
            when (keyType) {
                KeyType.SETTING -> securePrefs.encryptedSeedSetting = encoded
                KeyType.UNLOCK -> securePrefs.encryptedSeedUnlock = encoded
                KeyType.DESTROY -> securePrefs.encryptedSeedDestroy = encoded
            }
        }
    }

    override suspend fun retrieveSeed(keyType: KeyType): ByteArray? {
        val encoded = when (keyType) {
            KeyType.SETTING -> securePrefs.encryptedSeedSetting
            KeyType.UNLOCK -> securePrefs.encryptedSeedUnlock
            KeyType.DESTROY -> securePrefs.encryptedSeedDestroy
        } ?: return null

        val encrypted = Base64.getDecoder().decode(encoded)
        return keystoreManager.decryptSeed(encrypted)
    }

    override suspend fun clearSeeds() {
        securePrefs.encryptedSeedSetting = null
        securePrefs.encryptedSeedUnlock = null
        securePrefs.encryptedSeedDestroy = null
    }
}
