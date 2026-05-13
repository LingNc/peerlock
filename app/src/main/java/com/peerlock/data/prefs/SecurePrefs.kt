package com.peerlock.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 加密 SharedPreferences，用于非敏感配置。
 * 敏感数据（密钥、种子）存储在 Keystore 中。
 */
class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "peerlock_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var sessionId: String?
        get() = prefs.getString("session_id", null)
        set(value) = prefs.edit().putString("session_id", value).apply()

    var role: String?
        get() = prefs.getString("role", null)
        set(value) = prefs.edit().putString("role", value).apply()

    var isPaired: Boolean
        get() = prefs.getBoolean("is_paired", false)
        set(value) = prefs.edit().putBoolean("is_paired", value).apply()

    var isDeviceOwner: Boolean
        get() = prefs.getBoolean("is_device_owner", false)
        set(value) = prefs.edit().putBoolean("is_device_owner", value).apply()

    var timeOffsetMs: Long
        get() = prefs.getLong("time_offset_ms", 0L)
        set(value) = prefs.edit().putLong("time_offset_ms", value).apply()

    var lastNtpSyncTimestamp: Long
        get() = prefs.getLong("last_ntp_sync", 0L)
        set(value) = prefs.edit().putLong("last_ntp_sync", value).apply()

    var totpErrorCount: Int
        get() = prefs.getInt("totp_error_count", 0)
        set(value) = prefs.edit().putInt("totp_error_count", value).apply()

    var totpLockedUntil: Long
        get() = prefs.getLong("totp_locked_until", 0L)
        set(value) = prefs.edit().putLong("totp_locked_until", value).apply()

    var rawRecordsRetentionDays: Int
        get() = prefs.getInt("raw_retention_days", 30)
        set(value) = prefs.edit().putInt("raw_retention_days", value).apply()

    var consumedEnvelopes: Set<String>
        get() = prefs.getStringSet("consumed_envelopes", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("consumed_envelopes", value).apply()

    fun addConsumedEnvelope(id: String) {
        val current = consumedEnvelopes.toMutableSet()
        current.add(id)
        consumedEnvelopes = current
    }

    // L2 紧急解除 nonce
    var emergencyNonce: String?
        get() = prefs.getString("emergency_nonce", null)
        set(value) = prefs.edit().putString("emergency_nonce", value).apply()

    var emergencyNonceExpiry: Long
        get() = prefs.getLong("emergency_nonce_expiry", 0L)
        set(value) = prefs.edit().putLong("emergency_nonce_expiry", value).apply()

    // 配对材料
    var peerPublicKey: String?
        get() = prefs.getString("peer_public_key", null)
        set(value) = prefs.edit().putString("peer_public_key", value).apply()

    var encryptedSeedSetting: String?
        get() = prefs.getString("enc_seed_setting", null)
        set(value) = prefs.edit().putString("enc_seed_setting", value).apply()

    var encryptedSeedUnlock: String?
        get() = prefs.getString("enc_seed_unlock", null)
        set(value) = prefs.edit().putString("enc_seed_unlock", value).apply()

    var encryptedSeedDestroy: String?
        get() = prefs.getString("enc_seed_destroy", null)
        set(value) = prefs.edit().putString("enc_seed_destroy", value).apply()

    var myPublicKey: String?
        get() = prefs.getString("my_public_key", null)
        set(value) = prefs.edit().putString("my_public_key", value).apply()

    // 安全模式状态
    var safeModeActive: Boolean
        get() = prefs.getBoolean("safe_mode_active", false)
        set(value) = prefs.edit().putBoolean("safe_mode_active", value).apply()

    var safeModeReason: String?
        get() = prefs.getString("safe_mode_reason", null)
        set(value) = prefs.edit().putString("safe_mode_reason", value).apply()

    var lastDailyAggregationDate: String
        get() = prefs.getString("last_daily_agg_date", "") ?: ""
        set(value) = prefs.edit().putString("last_daily_agg_date", value).apply()

    // 电池优化白名单状态
    var batteryOptimizationDone: Boolean
        get() = prefs.getBoolean("battery_opt_done", false)
        set(value) = prefs.edit().putBoolean("battery_opt_done", value).apply()

    // 管控端缓存的被控端应用列表 (JSON)
    var remoteAppList: String?
        get() = prefs.getString("remote_app_list", null)
        set(value) = prefs.edit().putString("remote_app_list", value).apply()

    /** 选择性清除配对和加密材料，保留 DO 状态、使用数据配置、聚合状态等 */
    fun clearPairingData() {
        prefs.edit()
            .remove("session_id")
            .remove("role")
            .remove("is_paired")
            .remove("peer_public_key")
            .remove("my_public_key")
            .remove("enc_seed_setting")
            .remove("enc_seed_unlock")
            .remove("enc_seed_destroy")
            .remove("consumed_envelopes")
            .remove("totp_error_count")
            .remove("totp_locked_until")
            .remove("safe_mode_active")
            .remove("safe_mode_reason")
            .remove("emergency_nonce")
            .remove("emergency_nonce_expiry")
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
