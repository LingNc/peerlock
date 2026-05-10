package com.peerlock.domain.security

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository

class SafeModeManagerImpl(
    private val securePrefs: SecurePrefs,
    private val storageRepository: StorageRepository,
) : SafeModeManager {

    override fun isInSafeMode(): Boolean = securePrefs.safeModeActive

    override suspend fun enterSafeMode(reason: String) {
        securePrefs.safeModeActive = true
        securePrefs.safeModeReason = reason
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "SAFE_MODE_ENTER",
                targetPackage = null,
                detail = reason
            )
        )
    }

    override suspend fun exitSafeMode() {
        val wasActive = securePrefs.safeModeActive
        securePrefs.safeModeActive = false
        securePrefs.safeModeReason = null
        if (wasActive) {
            storageRepository.insertAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    action = "SAFE_MODE_EXIT",
                    targetPackage = null,
                    detail = "安全模式退出"
                )
            )
        }
    }

    override fun getSafeModeReason(): String? = securePrefs.safeModeReason
}
