package com.peerlock.system.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ADB 配对前台服务。
 * 通过通知栏 RemoteInput 输入配对码，无需在应用内输入。
 * 流程：mDNS 发现 → 通知栏提示 → 用户输入配对码 → SPAKE2 配对 → 执行 DO 命令
 */
class AdbPairingService : Service() {

    companion object {
        private const val TAG = "AdbPairingService"
        private const val CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_PAIRING_CODE = "com.peerlock.ACTION_PAIRING_CODE"
        const val ACTION_CANCEL = "com.peerlock.ACTION_CANCEL_ADB_PAIRING"
        const val EXTRA_PAIRING_CODE = "pairing_code"
        const val KEY_PAIRING_CODE_INPUT = "pairing_code_input"

        private val _state = MutableStateFlow(AdbPairingState.IDLE)
        val state: StateFlow<AdbPairingState> = _state.asStateFlow()

        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        private val _pairingPort = MutableStateFlow(0)
        val pairingPort: StateFlow<Int> = _pairingPort.asStateFlow()

        private val _connectPort = MutableStateFlow(0)
        val connectPort: StateFlow<Int> = _connectPort.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AdbPairingService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pairingMdns: AdbMdns? = null
    private var connectMdns: AdbMdns? = null
    private var currentJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildDiscoveryNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelAll()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAIRING_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_PAIRING_CODE_INPUT)?.toString()
                    ?: intent.getStringExtra(EXTRA_PAIRING_CODE)
                if (!code.isNullOrBlank()) {
                    onPairingCodeReceived(code)
                }
                return START_NOT_STICKY
            }
            else -> {
                // 首次启动，开始 mDNS 发现
                startDiscovery()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "ADB配对任务已移除，清理状态")
        cancelAll()
        stopSelf()
    }

    private fun startDiscovery() {
        _state.value = AdbPairingState.DISCOVERING
        _message.value = "正在搜索 ADB 配对服务..."

        updateNotification("正在搜索 ADB 配对服务...", "请确保已开启无线调试")

        pairingMdns?.stop()
        pairingMdns = AdbMdns(this, AdbMdns.TLS_PAIRING) { port ->
            Log.i(TAG, "Pairing service found on port $port")
            _pairingPort.value = port
            _state.value = AdbPairingState.FOUND
            _message.value = "已发现配对服务（端口 $port）"
            showPairingCodeNotification(port)
        }.also { it.start() }

        connectMdns?.stop()
        connectMdns = AdbMdns(this, AdbMdns.TLS_CONNECT) { port ->
            Log.i(TAG, "ADB connect service found on port $port")
            _connectPort.value = port
        }.also { it.start() }
    }

    private fun showPairingCodeNotification(port: Int) {
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE_INPUT)
            .setLabel("输入配对码（端口 $port）")
            .build()

        val replyIntent = Intent(this, AdbPairingService::class.java).apply {
            action = ACTION_PAIRING_CODE
        }
        val replyPendingIntent = PendingIntent.getService(
            this, 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "输入配对码",
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()

        val cancelIntent = Intent(this, AdbPairingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ADB 配对服务已发现")
            .setContentText("端口 $port — 请在通知栏输入配对码")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun onPairingCodeReceived(code: String) {
        val port = _pairingPort.value
        if (port <= 0) {
            _state.value = AdbPairingState.ERROR
            _message.value = "未发现配对端口"
            updateNotification("配对失败", "未发现配对端口")
            return
        }

        _state.value = AdbPairingState.PAIRING
        _message.value = "正在配对..."
        updateNotification("正在配对...", "端口 $port")

        currentJob?.cancel()
        currentJob = serviceScope.launch {
            try {
                val paired = withContext(Dispatchers.IO) { performPairing(code, port) }
                if (paired) {
                    _state.value = AdbPairingState.PAIRED
                    _message.value = "配对成功，正在设置 Device Owner..."
                    updateNotification("配对成功", "正在设置 Device Owner...")

                    val doResult = withContext(Dispatchers.IO) { executeDeviceOwnerCommand() }
                    if (doResult) {
                        pairingMdns?.stop()
                        connectMdns?.stop()
                        _state.value = AdbPairingState.SUCCESS
                        _message.value = "Device Owner 设置成功！"
                        updateNotification("设置成功", "Device Owner 已设置")

                        // 延迟后自动停止服务
                        kotlinx.coroutines.delay(3000)
                        stopSelf()
                    } else {
                        _state.value = AdbPairingState.ERROR
                        _message.value = "DO 命令执行失败，请检查设备是否已添加账户"
                        updateNotification("设置失败", "DO 命令执行失败")
                    }
                } else {
                    _state.value = AdbPairingState.ERROR
                    _message.value = "配对失败，请检查配对码是否正确"
                    updateNotification("配对失败", "配对码错误")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed", e)
                _state.value = AdbPairingState.ERROR
                _message.value = "错误: ${e.message}"
                updateNotification("配对出错", e.message ?: "未知错误")
            }
        }
    }

    private fun performPairing(pairCode: String, port: Int): Boolean {
        val keyStore = PreferenceAdbKeyStore(
            getSharedPreferences("peerlock_adb", MODE_PRIVATE),
        )
        val key = AdbKey(keyStore, "peerlock")
        AdbPairingClient("127.0.0.1", port, pairCode, key).use { client ->
            return client.start()
        }
    }

    private fun executeDeviceOwnerCommand(): Boolean {
        val connectPort = _connectPort.value
        if (connectPort > 0) {
            if (executeOnPort(connectPort)) return true
        }
        for (port in listOf(5555, 37217, 43221)) {
            if (executeOnPort(port)) return true
        }
        return false
    }

    private fun executeOnPort(port: Int): Boolean {
        return try {
            val keyStore = PreferenceAdbKeyStore(
                getSharedPreferences("peerlock_adb", MODE_PRIVATE),
            )
            val key = AdbKey(keyStore, "peerlock")
            AdbClient("127.0.0.1", port, key).use { client ->
                client.connect()
                val result = client.shellCommand(
                    "dpm set-device-owner $packageName/.system.deviceadmin.PeerLockDeviceAdminReceiver",
                )
                Log.i(TAG, "DO command result: $result")
                result.contains("Success")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed on port $port: ${e.message}")
            false
        }
    }

    private fun cancelAll() {
        currentJob?.cancel()
        pairingMdns?.stop()
        connectMdns?.stop()
        pairingMdns = null
        connectMdns = null
        _state.value = AdbPairingState.IDLE
        _message.value = null
        _pairingPort.value = 0
        _connectPort.value = 0
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ADB 配对",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "ADB 无线调试配对通知"
            enableVibration(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildDiscoveryNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PeerLock")
            .setContentText("正在搜索 ADB 配对服务...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val cancelIntent = Intent(this, AdbPairingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPendingIntent)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}

enum class AdbPairingState {
    IDLE,
    DISCOVERING,
    FOUND,
    PAIRING,
    PAIRED,
    SUCCESS,
    ERROR,
}
