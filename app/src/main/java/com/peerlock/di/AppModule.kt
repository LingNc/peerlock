package com.peerlock.di

import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.crypto.AdaptiveCryptoEngine
import com.peerlock.domain.crypto.CryptoEngine
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

    @Provides
    @Singleton
    fun provideCryptoEngine(): CryptoEngine = AdaptiveCryptoEngine()

    @Provides
    @Singleton
    fun provideTotpEngine(): TotpEngine = TotpEngineImpl()

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides
    @Singleton
    fun provideSecurePrefs(app: android.app.Application): SecurePrefs =
        SecurePrefs(app.applicationContext)
}
