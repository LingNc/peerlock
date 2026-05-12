package com.peerlock.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.peerlock.data.db.converter.Converters
import com.peerlock.data.db.dao.*
import com.peerlock.data.db.entity.*

@Database(
    entities = [
        UsageRecordEntity::class,
        HourlySummaryEntity::class,
        DailySummaryEntity::class,
        RestrictionPolicyEntity::class,
        AuditLogEntity::class,
        PairingSessionEntity::class,
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PeerLockDatabase : RoomDatabase() {
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun hourlySummaryDao(): HourlySummaryDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun restrictionPolicyDao(): RestrictionPolicyDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun pairingSessionDao(): PairingSessionDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pairing_sessions (
                sessionId TEXT NOT NULL PRIMARY KEY,
                role TEXT NOT NULL,
                peerDeviceName TEXT NOT NULL,
                peerPublicKey TEXT NOT NULL,
                myPublicKey TEXT NOT NULL,
                signingPublicKey TEXT,
                encryptedSeeds TEXT NOT NULL,
                status TEXT NOT NULL,
                identityFingerprint TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                revokedAt INTEGER,
                archivedAt INTEGER
            )
            """.trimIndent()
        )
    }
}
