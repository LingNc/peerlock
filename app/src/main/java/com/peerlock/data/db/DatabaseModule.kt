package com.peerlock.data.db

import android.content.Context
import androidx.room.Room
import com.peerlock.data.db.dao.*
import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.usage.UsageAggregator
import com.peerlock.data.usage.UsageAggregatorImpl
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
    fun provideDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager,
    ): PeerLockDatabase {
        val passphrase = try {
            keystoreManager.generateDbPassphrase()
        } catch (e: Exception) {
            com.peerlock.system.log.PeerLockLogger.w("DatabaseModule", "Keystore 不可用，使用备用密码", e)
            "peerlock_fallback_${context.packageName}".toByteArray()
        }
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            PeerLockDatabase::class.java,
            "peerlock.db"
        )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides fun provideUsageRecordDao(db: PeerLockDatabase) = db.usageRecordDao()
    @Provides fun provideHourlySummaryDao(db: PeerLockDatabase) = db.hourlySummaryDao()
    @Provides fun provideDailySummaryDao(db: PeerLockDatabase) = db.dailySummaryDao()
    @Provides fun provideRestrictionPolicyDao(db: PeerLockDatabase) = db.restrictionPolicyDao()
    @Provides fun provideAuditLogDao(db: PeerLockDatabase) = db.auditLogDao()
    @Provides fun providePairingSessionDao(db: PeerLockDatabase) = db.pairingSessionDao()

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

    @Provides
    @Singleton
    fun provideUsageAggregator(
        usageRecordDao: UsageRecordDao,
        hourlySummaryDao: HourlySummaryDao,
        dailySummaryDao: DailySummaryDao,
        auditLogDao: AuditLogDao,
        securePrefs: SecurePrefs,
    ): UsageAggregator = UsageAggregatorImpl(
        usageRecordDao, hourlySummaryDao, dailySummaryDao, auditLogDao, securePrefs
    )
}
