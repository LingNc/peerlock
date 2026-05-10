package com.peerlock.data.db

import android.content.Context
import androidx.room.Room
import com.peerlock.data.db.dao.*
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.repository.StorageRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PeerLockDatabase {
        val passphrase = "peerlock_dev_passphrase".toByteArray()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            PeerLockDatabase::class.java,
            "peerlock.db"
        )
            .openHelperFactory(factory)
            .build()
    }

    @Provides fun provideUsageRecordDao(db: PeerLockDatabase) = db.usageRecordDao()
    @Provides fun provideHourlySummaryDao(db: PeerLockDatabase) = db.hourlySummaryDao()
    @Provides fun provideDailySummaryDao(db: PeerLockDatabase) = db.dailySummaryDao()
    @Provides fun provideRestrictionPolicyDao(db: PeerLockDatabase) = db.restrictionPolicyDao()
    @Provides fun provideAuditLogDao(db: PeerLockDatabase) = db.auditLogDao()

    @Provides
    @Singleton
    fun provideStorageRepository(
        usageRecordDao: UsageRecordDao,
        hourlySummaryDao: HourlySummaryDao,
        dailySummaryDao: DailySummaryDao,
        policyDao: RestrictionPolicyDao,
        auditLogDao: AuditLogDao,
    ): StorageRepository = StorageRepositoryImpl(
        usageRecordDao, hourlySummaryDao, dailySummaryDao, policyDao, auditLogDao
    )
}
