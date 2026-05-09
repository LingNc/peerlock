package com.peerlock.domain.repository

import com.peerlock.data.db.dao.*
import com.peerlock.data.db.entity.*
import com.peerlock.domain.policy.RestrictionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

class StorageRepositoryImpl(
    private val usageRecordDao: UsageRecordDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val dailySummaryDao: DailySummaryDao,
    private val policyDao: RestrictionPolicyDao,
    private val auditLogDao: AuditLogDao,
) : StorageRepository {

    override suspend fun insertUsageRecord(record: UsageRecord) {
        usageRecordDao.insert(record.toEntity())
    }

    override suspend fun getUsageByDate(date: String): List<UsageRecord> {
        return usageRecordDao.getByDate(date).map { it.toDomain() }
    }

    override fun observeUsageByDate(date: String): Flow<List<UsageRecord>> {
        return usageRecordDao.observeByDate(date).map { records -> records.map { it.toDomain() } }
    }

    override suspend fun insertHourlySummary(summary: HourlySummary) {
        hourlySummaryDao.upsert(summary.toEntity())
    }

    override suspend fun getHourlySummary(date: String): List<HourlySummary> {
        return hourlySummaryDao.getByDate(date).map { it.toDomain() }
    }

    override suspend fun insertDailySummary(summary: DailySummary) {
        dailySummaryDao.upsert(summary.toEntity())
    }

    override suspend fun getDailySummary(startDate: String, endDate: String): List<DailySummary> {
        return dailySummaryDao.getByDateRange(startDate, endDate).map { it.toDomain() }
    }

    override suspend fun getDailySummaryByPackage(
        packageName: String, startDate: String, endDate: String
    ): List<DailySummary> {
        return dailySummaryDao.getByPackageAndDateRange(packageName, startDate, endDate).map { it.toDomain() }
    }

    override suspend fun getAllPolicies(): List<RestrictionPolicy> {
        return policyDao.getAll().map { it.toDomain() }
    }

    override suspend fun getActivePolicies(): List<RestrictionPolicy> {
        return policyDao.getActive().map { it.toDomain() }
    }

    override suspend fun upsertPolicy(policy: RestrictionPolicy) {
        policyDao.upsert(policy.toEntity())
    }

    override suspend fun deletePolicy(id: Long) {
        policyDao.deleteById(id)
    }

    override suspend fun insertAuditLog(log: AuditLog) {
        auditLogDao.insert(log.toEntity())
    }

    override suspend fun getRecentAuditLogs(limit: Int): List<AuditLog> {
        return auditLogDao.getRecent(limit).map { it.toDomain() }
    }

    // ---- 映射函数 ----

    private fun UsageRecordEntity.toDomain() = UsageRecord(
        packageName = packageName, startTime = startTime, endTime = endTime,
        durationMs = durationMs, date = date
    )

    private fun UsageRecord.toEntity() = UsageRecordEntity(
        packageName = packageName, startTime = startTime, endTime = endTime,
        durationMs = durationMs, date = date
    )

    private fun HourlySummaryEntity.toDomain() = HourlySummary(
        packageName = packageName, date = date, hour = hour, totalMs = totalMs
    )

    private fun HourlySummary.toEntity() = HourlySummaryEntity(
        packageName = packageName, date = date, hour = hour, totalMs = totalMs
    )

    private fun DailySummaryEntity.toDomain() = DailySummary(
        packageName = packageName, date = date, totalMs = totalMs, launchCount = launchCount
    )

    private fun DailySummary.toEntity() = DailySummaryEntity(
        packageName = packageName, date = date, totalMs = totalMs, launchCount = launchCount
    )

    private fun RestrictionPolicyEntity.toDomain() = RestrictionPolicy(
        id = id, targetPackage = targetPackage, dailyLimitMinutes = dailyLimitMinutes,
        allowedTimeStart = allowedTimeStart?.let { LocalTime.parse(it) },
        allowedTimeEnd = allowedTimeEnd?.let { LocalTime.parse(it) },
        isBlacklist = isBlacklist, isActive = isActive, createdAt = createdAt, lastModified = lastModified
    )

    private fun RestrictionPolicy.toEntity() = RestrictionPolicyEntity(
        id = id, targetPackage = targetPackage, dailyLimitMinutes = dailyLimitMinutes,
        allowedTimeStart = allowedTimeStart?.toString(), allowedTimeEnd = allowedTimeEnd?.toString(),
        isBlacklist = isBlacklist, isActive = isActive, createdAt = createdAt, lastModified = lastModified
    )

    private fun AuditLogEntity.toDomain() = AuditLog(
        timestamp = timestamp, action = action, targetPackage = targetPackage, detail = detail
    )

    private fun AuditLog.toEntity() = AuditLogEntity(
        timestamp = timestamp, action = action, targetPackage = targetPackage, detail = detail
    )
}
