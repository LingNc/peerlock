package com.peerlock.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.peerlock.system.service.PeerLockService

/**
 * 开机广播接收器。
 * 设备重启后自动启动 PeerLockService。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "设备已启动，启动 PeerLockService")
            val serviceIntent = Intent(context, PeerLockService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
