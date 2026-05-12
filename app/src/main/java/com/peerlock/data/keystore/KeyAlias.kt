package com.peerlock.data.keystore

/**
 * PeerLock 加密密钥的 Keystore 别名常量。
 * 所有密钥不可导出，由硬件安全模块保护。
 */
object KeyAlias {
    const val ECC_KEY_PAIR = "peerlock_ecc_keypair"
    const val TOTP_SEED_ENCRYPTOR = "peerlock_totp_seed_key"
    const val DB_PASSPHRASE_KEY = "peerlock_db_passphrase"
    const val PREFS_ENCRYPTOR = "peerlock_prefs_key"

    /** 按会话 ID 生成独立的种子加密别名 */
    fun totpSeedForSession(sessionId: String): String =
        "peerlock_seed_${sessionId.take(8)}"
}
