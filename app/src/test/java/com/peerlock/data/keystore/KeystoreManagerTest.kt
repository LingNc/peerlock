package com.peerlock.data.keystore

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KeystoreManagerTest {

    @Test
    fun `KeyAlias 常量唯一`() {
        val aliases = listOf(
            KeyAlias.ECC_KEY_PAIR,
            KeyAlias.TOTP_SEED_ENCRYPTOR,
            KeyAlias.DB_PASSPHRASE_KEY,
            KeyAlias.PREFS_ENCRYPTOR
        )
        assertEquals(aliases.size, aliases.toSet().size)
    }

    @Test
    fun `KeyAlias 常量非空`() {
        assertTrue(KeyAlias.ECC_KEY_PAIR.isNotBlank())
        assertTrue(KeyAlias.TOTP_SEED_ENCRYPTOR.isNotBlank())
        assertTrue(KeyAlias.DB_PASSPHRASE_KEY.isNotBlank())
        assertTrue(KeyAlias.PREFS_ENCRYPTOR.isNotBlank())
    }
}
