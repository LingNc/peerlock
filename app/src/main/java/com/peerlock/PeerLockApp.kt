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
        if (BuildConfig.DEV_DEBUG) {
            PeerLockLogger.setEnabled(true)
        }
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.setUninstallBlocked(true)
        }
    }
}
