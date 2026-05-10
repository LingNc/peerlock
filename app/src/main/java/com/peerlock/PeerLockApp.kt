package com.peerlock

import android.app.Application
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PeerLockApp : Application() {

    @Inject lateinit var deviceOwnerManager: DeviceOwnerManager

    override fun onCreate() {
        super.onCreate()
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.setUninstallBlocked(true)
        }
    }
}
