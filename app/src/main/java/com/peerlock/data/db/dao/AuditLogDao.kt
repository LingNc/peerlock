package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.AuditLogEntity

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AuditLogEntity>

    @Query("DELETE FROM audit_log WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
