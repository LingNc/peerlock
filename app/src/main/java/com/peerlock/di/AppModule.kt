package com.peerlock.di

import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.pairing.PairingRepositoryImpl
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.data.seed.SeedManagerImpl
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.data.usage.UsageStatsCollectorImpl
import com.peerlock.domain.crypto.AdaptiveCryptoEngine
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.emergency.EmergencyManager
import com.peerlock.domain.emergency.EmergencyManagerImpl
import com.peerlock.domain.pairing.PairingProtocol
import com.peerlock.domain.pairing.PairingProtocolImpl
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.PolicyEngineImpl
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.security.SafeModeManager
import com.peerlock.domain.security.SafeModeManagerImpl
import com.peerlock.domain.security.TimeSyncManager
import com.peerlock.domain.security.TimeSyncManagerImpl
import com.peerlock.domain.totp.EnvelopeCrypto
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEngineImpl
import com.peerlock.domain.request.RequestGuard
import com.peerlock.domain.request.RequestProtocol
import com.peerlock.domain.request.RequestProtocolImpl
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import com.peerlock.system.deviceadmin.DeviceOwnerManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideCryptoEngine(): CryptoEngine = AdaptiveCryptoEngine()

    @Provides @Singleton
    fun provideTotpEngine(): TotpEngine = TotpEngineImpl()

    @Provides @Singleton
    fun provideKeystoreManager(app: android.app.Application): KeystoreManager =
        KeystoreManager(app.applicationContext)

    @Provides @Singleton
    fun provideSecurePrefs(app: android.app.Application): SecurePrefs =
        SecurePrefs(app.applicationContext)

    @Provides @Singleton
    fun provideSeedManager(
        keystoreManager: KeystoreManager,
        securePrefs: SecurePrefs,
        pairingSessionDao: com.peerlock.data.db.dao.PairingSessionDao,
    ): SeedManager = SeedManagerImpl(keystoreManager, securePrefs, pairingSessionDao)

    @Provides @Singleton
    fun providePairingRepository(
        securePrefs: SecurePrefs,
        pairingSessionDao: com.peerlock.data.db.dao.PairingSessionDao,
    ): PairingRepository = PairingRepositoryImpl(securePrefs, pairingSessionDao)

    @Provides @Singleton
    fun providePairingProtocol(
        cryptoEngine: CryptoEngine,
        totpEngine: TotpEngine,
        seedManager: SeedManager,
        pairingRepository: PairingRepository,
        pairingSessionDao: com.peerlock.data.db.dao.PairingSessionDao,
    ): PairingProtocol = PairingProtocolImpl(
        cryptoEngine, totpEngine, seedManager, pairingRepository, pairingSessionDao
    )

    @Provides @Singleton
    fun provideDeviceOwnerManager(app: android.app.Application): DeviceOwnerManager =
        DeviceOwnerManagerImpl(app.applicationContext)

    @Provides @Singleton
    fun provideUsageStatsCollector(app: android.app.Application): UsageStatsCollector =
        UsageStatsCollectorImpl(app.applicationContext)

    @Provides @Singleton
    fun providePolicyEngine(
        storageRepository: StorageRepository,
        usageStatsCollector: UsageStatsCollector,
        deviceOwnerManager: DeviceOwnerManager,
        seedManager: SeedManager,
        totpEngine: TotpEngine,
        securePrefs: SecurePrefs,
    ): PolicyEngine = PolicyEngineImpl(
        storageRepository, usageStatsCollector, deviceOwnerManager,
        seedManager, totpEngine, securePrefs
    )

    @Provides @Singleton
    fun provideTimeSyncManager(securePrefs: SecurePrefs): TimeSyncManager =
        TimeSyncManagerImpl(securePrefs)

    @Provides @Singleton
    fun provideSafeModeManager(
        securePrefs: SecurePrefs,
        storageRepository: StorageRepository,
    ): SafeModeManager = SafeModeManagerImpl(securePrefs, storageRepository)

    @Provides @Singleton
    fun provideEnvelopeCrypto(cryptoEngine: CryptoEngine): EnvelopeCrypto =
        EnvelopeCrypto(cryptoEngine)

    @Provides @Singleton
    fun provideRequestGuard(securePrefs: SecurePrefs): RequestGuard =
        RequestGuard(securePrefs)

    @Provides @Singleton
    fun provideRequestProtocol(
        envelopeCrypto: EnvelopeCrypto,
        totpEngine: TotpEngine,
        seedManager: SeedManager,
        policyEngine: PolicyEngine,
        storageRepository: StorageRepository,
        requestGuard: RequestGuard,
        securePrefs: SecurePrefs,
    ): RequestProtocol = RequestProtocolImpl(
        envelopeCrypto, totpEngine, seedManager, policyEngine,
        storageRepository, requestGuard, securePrefs
    )

    @Provides @Singleton
    fun provideEmergencyManager(
        securePrefs: SecurePrefs,
        seedManager: SeedManager,
        totpEngine: TotpEngine,
        storageRepository: StorageRepository,
        deviceOwnerManager: DeviceOwnerManager,
    ): EmergencyManager = EmergencyManagerImpl(
        securePrefs, seedManager, totpEngine, storageRepository, deviceOwnerManager
    )
}
