package com.peerlock.domain.security

import com.peerlock.data.prefs.SecurePrefs
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

class TimeSyncManagerImpl(
    private val securePrefs: SecurePrefs,
) : TimeSyncManager {

    companion object {
        private val NTP_SERVERS = listOf("time.google.com", "ntp.aliyun.com")
        private const val NTP_PORT = 123
        private const val TIMEOUT_MS = 3000
        private const val SYNC_VALIDITY_MS = 6 * 3600 * 1000L // 6小时
        private const val TIME_MUTATION_THRESHOLD_MS = 30_000L // 30秒
        // NTP纪元偏移：1900-01-01 到 1970-01-01 的秒数
        private const val NTP_EPOCH_OFFSET = 2208988800L
    }

    override suspend fun getCurrentRealTime(): Long {
        return System.currentTimeMillis() + getStoredOffset()
    }

    override suspend fun syncWithNtp(): SyncResult {
        for (server in NTP_SERVERS) {
            val result = queryNtpServer(server)
            if (result != null) {
                securePrefs.timeOffsetMs = result
                securePrefs.lastNtpSyncTimestamp = System.currentTimeMillis()
                return SyncResult.Success(result)
            }
        }
        return SyncResult.Failed
    }

    override fun getStoredOffset(): Long = securePrefs.timeOffsetMs

    override fun isSystemTimeReliable(): Boolean {
        val lastSync = securePrefs.lastNtpSyncTimestamp
        if (lastSync == 0L) return false
        return (System.currentTimeMillis() - lastSync) < SYNC_VALIDITY_MS
    }

    fun detectTimeMutation(): Boolean {
        val storedOffset = securePrefs.timeOffsetMs
        val newOffset = queryNtpServer(NTP_SERVERS.first()) ?: return false
        return abs(newOffset - storedOffset) > TIME_MUTATION_THRESHOLD_MS
    }

    private fun queryNtpServer(server: String): Long? {
        return try {
            val address = InetAddress.getByName(server)
            val ntpData = ByteArray(48)
            ntpData[0] = 0x1B // NTP version 3, client mode

            val socket = DatagramSocket()
            socket.soTimeout = TIMEOUT_MS
            val queryStart = System.currentTimeMillis()

            val packet = DatagramPacket(ntpData, ntpData.size, address, NTP_PORT)
            socket.send(packet)

            val response = DatagramPacket(ByteArray(48), 48)
            socket.receive(response)
            val queryEnd = System.currentTimeMillis()
            socket.close()

            // 从响应字节 40-47 提取NTP时间戳
            val responseBytes = response.data
            val seconds = ((responseBytes[40].toLong() and 0xFF) shl 24) or
                    ((responseBytes[41].toLong() and 0xFF) shl 16) or
                    ((responseBytes[42].toLong() and 0xFF) shl 8) or
                    (responseBytes[43].toLong() and 0xFF)
            val fraction = ((responseBytes[44].toLong() and 0xFF) shl 24) or
                    ((responseBytes[45].toLong() and 0xFF) shl 16) or
                    ((responseBytes[46].toLong() and 0xFF) shl 8) or
                    (responseBytes[47].toLong() and 0xFF)

            val ntpTimeMs = (seconds - NTP_EPOCH_OFFSET) * 1000 + fraction * 1000 / 0x100000000L
            val rtt = queryEnd - queryStart
            ntpTimeMs + rtt / 2 - queryEnd
        } catch (_: Exception) {
            null
        }
    }
}
