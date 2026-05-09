package com.peerlock.domain.policy

interface PolicyEngine {
    suspend fun evaluate(packageName: String, currentTimeMillis: Long): PolicyAction
    suspend fun suspendApp(packageName: String)
    suspend fun unsuspendApp(packageName: String)
    suspend fun resetDailyUsage()
}
