package com.peerlock.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.peerlock.domain.emergency.EmergencyManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L2 紧急解除广播接收器。
 * 接收 ADB broadcast，验证一次性 nonce 后执行解除。
 *
 * 命令格式：
 * adb shell am broadcast \
 *     -a com.peerlock.ACTION_EMERGENCY \
 *     -n com.peerlock/.system.receiver.EmergencyUnlockReceiver \
 *     --es unlock_nonce <16位hex>
 */
@AndroidEntryPoint
class EmergencyUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EmergencyReceiver"
        const val ACTION_EMERGENCY = "com.peerlock.ACTION_EMERGENCY"
        const val EXTRA_NONCE = "unlock_nonce"
    }

    @Inject lateinit var emergencyManager: EmergencyManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EMERGENCY) return

        val nonce = intent.getStringExtra(EXTRA_NONCE)
        if (nonce == null) {
            Log.w(TAG, "缺少 nonce 参数")
            return
        }

        Log.i(TAG, "收到紧急解除请求")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = emergencyManager.verifyAndExecuteEmergency(nonce)
                if (success) {
                    Log.i(TAG, "紧急解除成功")
                } else {
                    Log.w(TAG, "紧急解除失败：nonce 无效或已过期")
                }
            } catch (e: Exception) {
                Log.e(TAG, "紧急解除异常: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
