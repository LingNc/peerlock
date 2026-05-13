package com.peerlock

import android.app.Application
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import com.peerlock.system.log.PeerLockLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PeerLockApp : Application() {

    @Inject lateinit var deviceOwnerManager: DeviceOwnerManager

    override fun onCreate() {
        super.onCreate()
        // 日志默认启用，用户可在日志页手动关闭
        val debugPrefs = getSharedPreferences("peerlock_debug", 0)
        PeerLockLogger.setEnabled(debugPrefs.getBoolean("log_enabled", true))
        PeerLockLogger.setAdvanced(debugPrefs.getBoolean("log_advanced", false))
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.setUninstallBlocked(true)
        }
    }
}
