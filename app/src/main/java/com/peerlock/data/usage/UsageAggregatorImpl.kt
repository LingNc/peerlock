package com.peerlock.data.usage

import com.peerlock.data.db.dao.DailySummaryDao
import com.peerlock.data.db.dao.HourlySummaryDao
import com.peerlock.data.db.dao.UsageRecordDao
import com.peerlock.data.db.entity.DailySummaryEntity
import com.peerlock.data.db.entity.HourlySummaryEntity
import com.peerlock.data.prefs.SecurePrefs
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UsageAggregatorImpl(
    private val usageRecordDao: UsageRecordDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val dailySummaryDao: DailySummaryDao,
    private val securePrefs: SecurePrefs,
) : UsageAggregator {

    override suspend fun aggregateHourly(date: String, hour: Int) {
        val records = usageRecordDao.getByDate(date)
        val hourStartMs = dateHourToEpochMs(date, hour)
        val hourEndMs = hourStartMs + 3_600_000L

        // 按包名分组，计算每条记录与该小时的重叠时长
        val hourlyByPackage = records
            .filter { it.endTime > hourStartMs && it.startTime < hourEndMs }
            .groupBy { it.packageName }
            .mapValues { (_, recs) ->
                recs.sumOf { rec ->
                    val overlapStart = maxOf(rec.startTime, hourStartMs)
                    val overlapEnd = minOf(rec.endTime, hourEndMs)
                    maxOf(0L, overlapEnd - overlapStart)
                }
            }

        for ((pkg, totalMs) in hourlyByPackage) {
            if (totalMs > 0) {
                hourlySummaryDao.upsert(
                    HourlySummaryEntity(
                        packageName = pkg,
                        date = date,
                        hour = hour,
                        totalMs = totalMs
                    )
                )
            }
        }
    }

    override suspend fun aggregateDaily(date: String) {
        val hourlySummaries = hourlySummaryDao.getByDate(date)
        val dailyByPackage = hourlySummaries
            .groupBy { it.packageName }
            .mapValues { (_, summaries) -> summaries.sumOf { it.totalMs } }

        for ((pkg, totalMs) in dailyByPackage) {
            dailySummaryDao.upsert(
                DailySummaryEntity(
                    packageName = pkg,
                    date = date,
                    totalMs = totalMs,
                    launchCount = 0
                )
            )
        }
    }

    override suspend fun cleanupOldRecords() {
        val retentionDays = securePrefs.rawRecordsRetentionDays
        val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        usageRecordDao.deleteOlderThan(cutoffDate)
    }

    private fun dateHourToEpochMs(date: String, hour: Int): Long {
        val dt = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault())
        return dt.toInstant().toEpochMilli() + hour * 3_600_000L
    }
}
