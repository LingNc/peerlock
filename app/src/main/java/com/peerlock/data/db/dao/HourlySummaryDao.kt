package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.HourlySummaryEntity

@Dao
interface HourlySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: HourlySummaryEntity)

    @Query("SELECT * FROM usage_hourly_summary WHERE date = :date ORDER BY hour")
    suspend fun getByDate(date: String): List<HourlySummaryEntity>

    @Query("SELECT * FROM usage_hourly_summary WHERE packageName = :pkg AND date = :date")
    suspend fun getByPackageAndDate(pkg: String, date: String): List<HourlySummaryEntity>
}
