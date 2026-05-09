package com.peerlock.domain.security

interface TimeSyncManager {
    suspend fun getCurrentRealTime(): Long
    suspend fun syncWithNtp(): SyncResult
    fun getStoredOffset(): Long
    fun isSystemTimeReliable(): Boolean
}

sealed class SyncResult {
    data class Success(val offsetMs: Long) : SyncResult()
    data object Failed : SyncResult()
}
