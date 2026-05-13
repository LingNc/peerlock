package com.peerlock

import android.app.Application
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import com.peerlock.system.log.PeerLockLogger
import dagger.hilt.android.HiltAndroidApp
import java.util.Base64
import javax.inject.Inject

@HiltAndroidApp
class PeerLockApp : Application() {

    @Inject lateinit var deviceOwnerManager: DeviceOwnerManager
    @Inject lateinit var cryptoEngine: CryptoEngine
    @Inject lateinit var securePrefs: SecurePrefs

    override fun onCreate() {
        super.onCreate()
        // 日志默认启用，用户可在日志页手动关闭
        val debugPrefs = getSharedPreferences("peerlock_debug", 0)
        PeerLockLogger.setEnabled(debugPrefs.getBoolean("log_enabled", true))
        PeerLockLogger.setAdvanced(debugPrefs.getBoolean("log_advanced", false))
        // 自动生成密钥对并存储公钥（首次启动时）
        if (securePrefs.myPublicKey.isNullOrBlank()) {
            kotlinx.coroutines.runBlocking {
                val keyPair = cryptoEngine.generateKeyPair()
                securePrefs.myPublicKey = Base64.getEncoder().withoutPadding().encodeToString(keyPair.publicKey)
            }
        }
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.setUninstallBlocked(true)
        }
    }
}
