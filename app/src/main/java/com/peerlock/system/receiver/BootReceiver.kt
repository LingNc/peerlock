package com.peerlock.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.system.service.PeerLockService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 开机广播接收器。
 * 设备重启后自动启动 PeerLockService（仅在已配对时）。
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    @Inject lateinit var securePrefs: SecurePrefs

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (!securePrefs.isPaired) {
                Log.i(TAG, "未配对，跳过启动服务")
                return
            }
            Log.i(TAG, "设备已启动，启动 PeerLockService")
            val serviceIntent = Intent(context, PeerLockService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
