package com.peerlock.system.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * PeerLock 前台服务。
 * 每 30 秒执行一次策略巡检。
 */
@AndroidEntryPoint
class PeerLockService : Service() {

    companion object {
        private const val TAG = "PeerLockService"
        private const val CHANNEL_ID = "peerlock_service"
        private const val NOTIFICATION_ID = 1001
        private const val PATROL_INTERVAL_MS = 30_000L

        const val ACTION_STOP = "com.peerlock.STOP_SERVICE"
    }

    @Inject lateinit var policyEngine: PolicyEngine
    @Inject lateinit var usageCollector: UsageStatsCollector
    @Inject lateinit var storageRepository: StorageRepository
    @Inject lateinit var deviceOwnerManager: DeviceOwnerManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var patrolJob: Job? = null
    private lateinit var patrolLogic: PatrolLogic

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(TAG, "前台服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!::patrolLogic.isInitialized) {
            patrolLogic = PatrolLogic(policyEngine, usageCollector, storageRepository, deviceOwnerManager)
        }

        startPatrolLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        patrolJob?.cancel()
        serviceScope.cancel()
        Log.i(TAG, "前台服务已停止")
        super.onDestroy()
    }

    private fun startPatrolLoop() {
        patrolJob?.cancel()
        patrolJob = serviceScope.launch {
            while (isActive) {
                try {
                    patrolLogic.executePatrol()
                } catch (e: Exception) {
                    Log.e(TAG, "巡检异常: ${e.message}", e)
                }
                delay(PATROL_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PeerLock 服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "PeerLock 后台运行通知"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PeerLock 运行中")
            .setContentText("策略巡检服务正在运行")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}
