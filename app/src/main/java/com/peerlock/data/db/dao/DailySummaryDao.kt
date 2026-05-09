package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.DailySummaryEntity

@Dao
interface DailySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM usage_daily_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getByDateRange(startDate: String, endDate: String): List<DailySummaryEntity>

    @Query("SELECT * FROM usage_daily_summary WHERE packageName = :pkg AND date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getByPackageAndDateRange(pkg: String, startDate: String, endDate: String): List<DailySummaryEntity>
}
