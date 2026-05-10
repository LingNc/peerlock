package com.peerlock.domain.policy

interface PolicyEngine {
    suspend fun evaluate(packageName: String, currentTimeMillis: Long): PolicyAction
    suspend fun suspendApp(packageName: String)
    suspend fun unsuspendApp(packageName: String)
    suspend fun resetDailyUsage()
    suspend fun verifyUnlockCode(code: String): Boolean
    suspend fun recordUnlock(packageName: String, durationMinutes: Int)
    fun isUnlocked(packageName: String, currentTimeMillis: Long): Boolean
}
