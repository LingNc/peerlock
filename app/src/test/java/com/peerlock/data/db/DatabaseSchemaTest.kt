package com.peerlock.data.db

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DatabaseSchemaTest {

    @Test
    fun `数据库类存在且注解正确`() {
        val dbClass = PeerLockDatabase::class.java
        assertNotNull(dbClass)
        assertTrue(dbClass.interfaces.any { it == androidx.room.RoomDatabase::class.java })
    }

    @Test
    fun `所有 DAO 接口存在`() {
        assertNotNull(com.peerlock.data.db.dao.UsageRecordDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.HourlySummaryDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.DailySummaryDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.RestrictionPolicyDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.AuditLogDao::class.java)
    }
}
