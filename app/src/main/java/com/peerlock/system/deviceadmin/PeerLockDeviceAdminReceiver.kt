package com.peerlock.system.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * PeerLock 设备管理员接收器。
 * 处理设备管理员生命周期事件。
 * 第三阶段将添加策略执行逻辑。
 */
class PeerLockDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "PeerLockAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已启用")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已禁用")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "PeerLock 需要设备管理员权限才能执行应用限制。"
    }
}
