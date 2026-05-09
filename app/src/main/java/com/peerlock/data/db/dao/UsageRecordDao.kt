package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.UsageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {
    @Insert
    suspend fun insert(record: UsageRecordEntity)

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY startTime")
    suspend fun getByDate(date: String): List<UsageRecordEntity>

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY startTime")
    fun observeByDate(date: String): Flow<List<UsageRecordEntity>>

    @Query("DELETE FROM usage_records WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("SELECT COUNT(*) FROM usage_records")
    suspend fun count(): Int
}
