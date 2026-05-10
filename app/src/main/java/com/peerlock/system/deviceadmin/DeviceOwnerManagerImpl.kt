package com.peerlock.system.deviceadmin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

class DeviceOwnerManagerImpl(
    private val context: Context,
) : DeviceOwnerManager {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent = ComponentName(context, PeerLockDeviceAdminReceiver::class.java)

    override fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    override fun isAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    override fun setPackagesSuspended(packages: List<String>, suspended: Boolean): List<String> {
        return dpm.setPackagesSuspended(adminComponent, packages.toTypedArray(), suspended).toList()
    }

    override fun getAdminComponentName(): ComponentName = adminComponent

    override fun removeDeviceOwner(): Boolean {
        return try {
            dpm.clearDeviceOwnerApp(context.packageName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "移除 DO 失败", e)
            false
        }
    }

    override fun setUninstallBlocked(blocked: Boolean) {
        dpm.setUninstallBlocked(adminComponent, context.packageName, blocked)
    }

    companion object {
        private const val TAG = "DeviceOwnerManager"
    }
}
