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

    fun clear() {
        prefs.edit().clear().apply()
    }
}
