package com.peerlock.data.usage

import com.peerlock.data.db.dao.AuditLogDao
import com.peerlock.data.db.dao.DailySummaryDao
import com.peerlock.data.db.dao.HourlySummaryDao
import com.peerlock.data.db.dao.UsageRecordDao
import com.peerlock.data.db.entity.DailySummaryEntity
import com.peerlock.data.db.entity.HourlySummaryEntity
import com.peerlock.data.db.entity.UsageRecordEntity
import com.peerlock.data.prefs.SecurePrefs
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UsageAggregatorImplTest {

    private lateinit var usageRecordDao: UsageRecordDao
    private lateinit var hourlySummaryDao: HourlySummaryDao
    private lateinit var dailySummaryDao: DailySummaryDao
    private lateinit var auditLogDao: AuditLogDao
    private lateinit var securePrefs: SecurePrefs
    private lateinit var aggregator: UsageAggregatorImpl

    @BeforeEach
    fun setup() {
        usageRecordDao = mockk(relaxed = true)
        hourlySummaryDao = mockk(relaxed = true)
        dailySummaryDao = mockk(relaxed = true)
        auditLogDao = mockk(relaxed = true)
        securePrefs = mockk(relaxed = true)
        aggregator = UsageAggregatorImpl(usageRecordDao, hourlySummaryDao, dailySummaryDao, auditLogDao, securePrefs)
    }

    @Test
    fun `aggregateHourly 按包名分组并累加重叠时长`() = runTest {
        // 2026-05-10 01:00 的 epoch ms
        val hourStartMs = java.time.LocalDate.parse("2026-05-10")
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli() + 3_600_000L
        val hourEndMs = hourStartMs + 3_600_000L

        // 记录完全在该小时内
        val record1 = UsageRecordEntity(
            id = 1, packageName = "com.app1",
            startTime = hourStartMs + 1000, endTime = hourStartMs + 61_000,
            durationMs = 60_000, date = "2026-05-10"
        )
        // 记录跨越小时边界
        val record2 = UsageRecordEntity(
            id = 2, packageName = "com.app1",
            startTime = hourStartMs - 30_000, endTime = hourStartMs + 30_000,
            durationMs = 60_000, date = "2026-05-10"
        )
        val record3 = UsageRecordEntity(
            id = 3, packageName = "com.app2",
            startTime = hourStartMs + 100_000, endTime = hourStartMs + 200_000,
            durationMs = 100_000, date = "2026-05-10"
        )

        coEvery { usageRecordDao.getByDate("2026-05-10") } returns listOf(record1, record2, record3)

        aggregator.aggregateHourly("2026-05-10", 1)

        // app1: 60000 + 30000 = 90000
        coVerify {
            hourlySummaryDao.upsert(match {
                it.packageName == "com.app1" && it.hour == 1 && it.totalMs == 90_000L
            })
        }
        // app2: 100000
        coVerify {
            hourlySummaryDao.upsert(match {
                it.packageName == "com.app2" && it.hour == 1 && it.totalMs == 100_000L
            })
        }
    }

    @Test
    fun `aggregateHourly 不聚合该小时外的记录`() = runTest {
        val hourStartMs = java.time.LocalDate.parse("2026-05-10")
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli() + 3_600_000L

        // 记录完全在 00:00-01:00 之外
        val record = UsageRecordEntity(
            id = 1, packageName = "com.app1",
            startTime = hourStartMs + 3_700_000, endTime = hourStartMs + 3_800_000,
            durationMs = 100_000, date = "2026-05-10"
        )

        coEvery { usageRecordDao.getByDate("2026-05-10") } returns listOf(record)

        aggregator.aggregateHourly("2026-05-10", 1)

        coVerify(exactly = 0) { hourlySummaryDao.upsert(any()) }
    }

    @Test
    fun `aggregateDaily 汇总所有小时数据`() = runTest {
        val hourlyData = listOf(
            HourlySummaryEntity("com.app1", "2026-05-10", 0, 1000L),
            HourlySummaryEntity("com.app1", "2026-05-10", 1, 2000L),
            HourlySummaryEntity("com.app2", "2026-05-10", 0, 3000L),
        )

        coEvery { hourlySummaryDao.getByDate("2026-05-10") } returns hourlyData

        aggregator.aggregateDaily("2026-05-10")

        coVerify {
            dailySummaryDao.upsert(match {
                it.packageName == "com.app1" && it.date == "2026-05-10" && it.totalMs == 3000L
            })
        }
        coVerify {
            dailySummaryDao.upsert(match {
                it.packageName == "com.app2" && it.date == "2026-05-10" && it.totalMs == 3000L
            })
        }
    }

    @Test
    fun `cleanupOldRecords 删除过期记录`() = runTest {
        every { securePrefs.rawRecordsRetentionDays } returns 30

        aggregator.cleanupOldRecords()

        coVerify { usageRecordDao.deleteOlderThan(match { it.isNotBlank() }) }
    }

    @Test
    fun `aggregateDaily 无小时数据时不写入`() = runTest {
        coEvery { hourlySummaryDao.getByDate("2026-05-10") } returns emptyList()

        aggregator.aggregateDaily("2026-05-10")

        coVerify(exactly = 0) { dailySummaryDao.upsert(any()) }
    }
}
