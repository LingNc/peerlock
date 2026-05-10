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
import com.peerlock.domain.pairing.PairingProtocol
import com.peerlock.domain.pairing.PairingProtocolImpl
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.PolicyEngineImpl
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEngineImpl
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
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides @Singleton
    fun provideSecurePrefs(app: android.app.Application): SecurePrefs =
        SecurePrefs(app.applicationContext)

    @Provides @Singleton
    fun provideSeedManager(
        keystoreManager: KeystoreManager,
        securePrefs: SecurePrefs,
    ): SeedManager = SeedManagerImpl(keystoreManager, securePrefs)

    @Provides @Singleton
    fun providePairingRepository(
        securePrefs: SecurePrefs,
    ): PairingRepository = PairingRepositoryImpl(securePrefs)

    @Provides @Singleton
    fun providePairingProtocol(
        cryptoEngine: CryptoEngine,
        totpEngine: TotpEngine,
        seedManager: SeedManager,
        pairingRepository: PairingRepository,
    ): PairingProtocol = PairingProtocolImpl(
        cryptoEngine, totpEngine, seedManager, pairingRepository
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
}
