package com.peerlock.data.seed

import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.totp.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64

@Serializable
private data class EncryptedSeeds(val seeds: Map<String, String> = emptyMap())

class SeedManagerImpl(
    private val keystoreManager: KeystoreManager,
    private val securePrefs: SecurePrefs,
    private val pairingSessionDao: PairingSessionDao,
) : SeedManager {

    companion object {
        private const val SEED_LENGTH = 20 // 160 bit
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun generateSeeds(): Map<KeyType, ByteArray> {
        val random = SecureRandom()
        return KeyType.entries.associateWith {
            ByteArray(SEED_LENGTH).also { random.nextBytes(it) }
        }
    }

    override suspend fun storeSeeds(seeds: Map<KeyType, ByteArray>) {
        val activeSession = pairingSessionDao.getActiveSession()
        if (activeSession != null) {
            storeSeedsForSession(activeSession.sessionId, seeds)
        } else {
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
    }

    override suspend fun retrieveSeed(keyType: KeyType): ByteArray? {
        val activeSession = pairingSessionDao.getActiveSession()
        if (activeSession != null) {
            return retrieveSeedFromSession(activeSession.sessionId, keyType)
        }
        val encoded = when (keyType) {
            KeyType.SETTING -> securePrefs.encryptedSeedSetting
            KeyType.UNLOCK -> securePrefs.encryptedSeedUnlock
            KeyType.DESTROY -> securePrefs.encryptedSeedDestroy
        } ?: return null
        val encrypted = Base64.getDecoder().decode(encoded)
        return keystoreManager.decryptSeed(encrypted)
    }

    override suspend fun clearSeeds() {
        val activeSession = pairingSessionDao.getActiveSession()
        if (activeSession != null) {
            clearSeedsForSession(activeSession.sessionId)
        }
        securePrefs.encryptedSeedSetting = null
        securePrefs.encryptedSeedUnlock = null
        securePrefs.encryptedSeedDestroy = null
    }

    // === 多会话接口 ===

    override suspend fun storeSeedsForSession(sessionId: String, seeds: Map<KeyType, ByteArray>) {
        val session = pairingSessionDao.getById(sessionId) ?: return
        val seedMap = seeds.entries.associate { (keyType, seed) ->
            val encrypted = keystoreManager.encryptSeedForSession(sessionId, seed)
            keyType.name to Base64.getEncoder().withoutPadding().encodeToString(encrypted)
        }
        val jsonStr = json.encodeToString(EncryptedSeeds.serializer(), EncryptedSeeds(seedMap))
        pairingSessionDao.update(session.copy(encryptedSeeds = jsonStr))
    }

    override suspend fun retrieveSeedFromSession(sessionId: String, keyType: KeyType): ByteArray? {
        val session = pairingSessionDao.getById(sessionId) ?: return null
        val wrapper = try {
            json.decodeFromString(EncryptedSeeds.serializer(), session.encryptedSeeds)
        } catch (_: Exception) {
            return null
        }
        val encoded = wrapper.seeds[keyType.name] ?: return null
        val encrypted = Base64.getDecoder().decode(encoded)
        return keystoreManager.decryptSeedForSession(sessionId, encrypted)
    }

    override suspend fun clearSeedsForSession(sessionId: String) {
        val session = pairingSessionDao.getById(sessionId) ?: return
        pairingSessionDao.update(session.copy(encryptedSeeds = "{}"))
    }

    override suspend fun migrateSeedsToSession(sessionId: String) {
        val seeds = mutableMapOf<KeyType, ByteArray>()
        for (keyType in KeyType.entries) {
            val encoded = when (keyType) {
                KeyType.SETTING -> securePrefs.encryptedSeedSetting
                KeyType.UNLOCK -> securePrefs.encryptedSeedUnlock
                KeyType.DESTROY -> securePrefs.encryptedSeedDestroy
            } ?: continue
            val encrypted = Base64.getDecoder().decode(encoded)
            seeds[keyType] = keystoreManager.decryptSeed(encrypted)
        }
        if (seeds.isNotEmpty()) {
            storeSeedsForSession(sessionId, seeds)
            seeds.values.forEach { it.fill(0) }
            securePrefs.encryptedSeedSetting = null
            securePrefs.encryptedSeedUnlock = null
            securePrefs.encryptedSeedDestroy = null
        }
    }
}
