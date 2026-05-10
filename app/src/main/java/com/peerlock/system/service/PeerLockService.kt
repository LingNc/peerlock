package com.peerlock.system.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.usage.UsageAggregator
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.security.SafeModeManager
import com.peerlock.domain.security.SyncResult
import com.peerlock.domain.security.TimeSyncManager
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class PeerLockService : Service() {

    companion object {
        private const val TAG = "PeerLockService"
        private const val CHANNEL_ID = "peerlock_service"
        private const val NOTIFICATION_ID = 1001
        private const val PATROL_INTERVAL_MS = 30_000L

        const val ACTION_STOP = "com.peerlock.STOP_SERVICE"
        const val ACTION_PATROL = "com.peerlock.PATROL"
    }

    @Inject lateinit var policyEngine: PolicyEngine
    @Inject lateinit var usageCollector: UsageStatsCollector
    @Inject lateinit var storageRepository: StorageRepository
    @Inject lateinit var deviceOwnerManager: DeviceOwnerManager
    @Inject lateinit var timeSyncManager: TimeSyncManager
    @Inject lateinit var safeModeManager: SafeModeManager
    @Inject lateinit var usageAggregator: UsageAggregator
    @Inject lateinit var securePrefs: SecurePrefs

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var patrolLogic: PatrolLogic

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (safeModeManager.isInSafeMode()) {
                Log.i(TAG, "网络恢复，尝试 NTP 校验退出安全模式")
                serviceScope.launch {
                    try {
                        val result = timeSyncManager.syncWithNtp()
                        if (result is SyncResult.Success) {
                            safeModeManager.exitSafeMode()
                            Log.i(TAG, "NTP 校验成功，已退出安全模式")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "网络恢复后 NTP 校验失败", e)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerNetworkCallback()
        Log.i(TAG, "前台服务已创建")
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "注册网络回调失败", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!::patrolLogic.isInitialized) {
            patrolLogic = PatrolLogic(
                policyEngine, usageCollector, storageRepository, deviceOwnerManager,
                timeSyncManager, safeModeManager, usageAggregator, securePrefs
            )
        }

        serviceScope.launch {
            try {
                patrolLogic.executePatrol()
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "巡检异常: ${e.message}", e)
            }
            scheduleNextPatrol()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        cancelAlarm()
        serviceScope.cancel()
        Log.i(TAG, "前台服务已停止")
        super.onDestroy()
    }

    private fun scheduleNextPatrol() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, PeerLockService::class.java).apply {
            action = ACTION_PATROL
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = SystemClock.elapsedRealtime() + PATROL_INTERVAL_MS
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
        )
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, PeerLockService::class.java).apply {
            action = ACTION_PATROL
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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

    private fun buildNotification(title: String = "PeerLock 运行中", text: String = "策略巡检服务正在运行"): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun updateNotification() {
        val policies = storageRepository.getActivePolicies()
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val stats = usageCollector.queryUsageStats(todayStart, now)
        val totalScreenTimeMin = stats.sumOf { it.totalTimeMs } / 60_000

        val title = "PeerLock 运行中"
        val text = "今日屏幕时间: ${totalScreenTimeMin}分钟 | 受限应用: ${policies.size}个"

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }
}
