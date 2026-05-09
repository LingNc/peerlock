package com.peerlock.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.peerlock.data.db.converter.Converters
import com.peerlock.data.db.dao.*
import com.peerlock.data.db.entity.*

@Database(
    entities = [
        UsageRecordEntity::class,
        HourlySummaryEntity::class,
        DailySummaryEntity::class,
        RestrictionPolicyEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PeerLockDatabase : RoomDatabase() {
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun hourlySummaryDao(): HourlySummaryDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun restrictionPolicyDao(): RestrictionPolicyDao
    abstract fun auditLogDao(): AuditLogDao
}
