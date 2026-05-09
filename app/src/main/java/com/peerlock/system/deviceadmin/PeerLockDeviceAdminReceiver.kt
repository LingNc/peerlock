package com.peerlock.system.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * PeerLock 设备管理员接收器。
 * 处理设备管理员生命周期事件和策略回调。
 */
class PeerLockDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "PeerLockAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已启用（Device Owner）")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已禁用，清除配对状态")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "PeerLock 需要设备管理员权限才能执行应用限制。禁用后所有限制将失效。"
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.i(TAG, "应用进入锁定任务模式: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.i(TAG, "退出锁定任务模式")
    }
}
