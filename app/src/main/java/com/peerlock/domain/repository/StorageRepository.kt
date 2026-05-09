package com.peerlock.domain.repository

import com.peerlock.domain.policy.RestrictionPolicy
import kotlinx.coroutines.flow.Flow

interface StorageRepository {
    suspend fun insertUsageRecord(record: UsageRecord)
    suspend fun getUsageByDate(date: String): List<UsageRecord>
    fun observeUsageByDate(date: String): Flow<List<UsageRecord>>

    suspend fun insertHourlySummary(summary: HourlySummary)
    suspend fun getHourlySummary(date: String): List<HourlySummary>

    suspend fun insertDailySummary(summary: DailySummary)
    suspend fun getDailySummary(startDate: String, endDate: String): List<DailySummary>
    suspend fun getDailySummaryByPackage(packageName: String, startDate: String, endDate: String): List<DailySummary>

    suspend fun getAllPolicies(): List<RestrictionPolicy>
    suspend fun getActivePolicies(): List<RestrictionPolicy>
    suspend fun upsertPolicy(policy: RestrictionPolicy)
    suspend fun deletePolicy(id: Long)

    suspend fun insertAuditLog(log: AuditLog)
    suspend fun getRecentAuditLogs(limit: Int): List<AuditLog>
}

data class UsageRecord(
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val date: String
)

data class HourlySummary(
    val packageName: String,
    val date: String,
    val hour: Int,
    val totalMs: Long
)

data class DailySummary(
    val packageName: String,
    val date: String,
    val totalMs: Long,
    val launchCount: Int
)

data class AuditLog(
    val timestamp: Long,
    val action: String,
    val targetPackage: String?,
    val detail: String?
)
