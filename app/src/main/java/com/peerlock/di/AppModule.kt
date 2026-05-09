package com.peerlock.di

import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.pairing.PairingRepositoryImpl
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.data.seed.SeedManagerImpl
import com.peerlock.domain.crypto.AdaptiveCryptoEngine
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.pairing.PairingProtocol
import com.peerlock.domain.pairing.PairingProtocolImpl
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEngineImpl
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
}
