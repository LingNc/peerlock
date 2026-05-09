package com.peerlock.domain.repository

import com.peerlock.data.db.dao.*
import com.peerlock.data.db.entity.*
import com.peerlock.domain.policy.RestrictionPolicy
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class StorageRepositoryImplTest {

    private lateinit var repo: StorageRepositoryImpl
    private lateinit var usageRecordDao: UsageRecordDao
    private lateinit var hourlySummaryDao: HourlySummaryDao
    private lateinit var dailySummaryDao: DailySummaryDao
    private lateinit var policyDao: RestrictionPolicyDao
    private lateinit var auditLogDao: AuditLogDao

    @BeforeEach
    fun setup() {
        usageRecordDao = mockk(relaxed = true)
        hourlySummaryDao = mockk(relaxed = true)
        dailySummaryDao = mockk(relaxed = true)
        policyDao = mockk(relaxed = true)
        auditLogDao = mockk(relaxed = true)
        repo = StorageRepositoryImpl(
            usageRecordDao, hourlySummaryDao, dailySummaryDao, policyDao, auditLogDao
        )
    }

    @Test
    fun `getAllPolicies 映射正确`() = runTest {
        val entity = RestrictionPolicyEntity(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = 60,
            allowedTimeStart = "08:00", allowedTimeEnd = "22:00",
            isBlacklist = true, isActive = true, createdAt = 1000L, lastModified = 2000L
        )
        coEvery { policyDao.getAll() } returns listOf(entity)

        val result = repo.getAllPolicies()

        assertEquals(1, result.size)
        assertEquals("com.test", result[0].targetPackage)
        assertEquals(60, result[0].dailyLimitMinutes)
        assertEquals(LocalTime.of(8, 0), result[0].allowedTimeStart)
        assertEquals(LocalTime.of(22, 0), result[0].allowedTimeEnd)
    }

    @Test
    fun `upsertPolicy 映射到 Entity`() = runTest {
        val policy = RestrictionPolicy(
            id = 0, targetPackage = "com.test", dailyLimitMinutes = 30,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = false, isActive = true, createdAt = 1000L, lastModified = 2000L
        )

        repo.upsertPolicy(policy)

        coVerify {
            policyDao.upsert(withArg { entity ->
                assertEquals("com.test", entity.targetPackage)
                assertEquals(30, entity.dailyLimitMinutes)
                assertNull(entity.allowedTimeStart)
            })
        }
    }

    @Test
    fun `insertUsageRecord 映射到 Entity`() = runTest {
        val record = UsageRecord(
            packageName = "com.test", startTime = 1000L, endTime = 2000L,
            durationMs = 1000L, date = "2026-05-10"
        )

        repo.insertUsageRecord(record)

        coVerify {
            usageRecordDao.insert(withArg { entity ->
                assertEquals("com.test", entity.packageName)
                assertEquals(1000L, entity.durationMs)
            })
        }
    }

    @Test
    fun `insertAuditLog 映射到 Entity`() = runTest {
        val log = AuditLog(
            timestamp = 1000L, action = "SUSPEND", targetPackage = "com.test", detail = "超时"
        )

        repo.insertAuditLog(log)

        coVerify {
            auditLogDao.insert(withArg { entity ->
                assertEquals("SUSPEND", entity.action)
                assertEquals("com.test", entity.targetPackage)
            })
        }
    }
}
