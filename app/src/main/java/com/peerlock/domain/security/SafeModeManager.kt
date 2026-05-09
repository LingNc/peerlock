package com.peerlock.domain.security

interface SafeModeManager {
    fun isInSafeMode(): Boolean
    suspend fun enterSafeMode(reason: String)
    suspend fun exitSafeMode()
    fun getSafeModeReason(): String?
}
