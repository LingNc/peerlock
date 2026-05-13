package com.peerlock.system.log

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PeerLockLogger {

    private const val MAX_LOGS = 500
    private val logs = mutableListOf<LogEntry>()
    private var enabled = false
    private var advanced = false

    private val sensitiveKeys = listOf(
        "key", "seed", "token", "password", "secret",
        "passphrase", "private", "pub", "signPub",
    )

    fun v(tag: String, msg: String) = log(LogLevel.V, tag, msg)
    fun d(tag: String, msg: String) = log(LogLevel.D, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.I, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.W, tag, msg)
    fun w(tag: String, msg: String, t: Throwable) = log(LogLevel.W, tag, "$msg\n${Log.getStackTraceString(t)}")

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val fullMsg = if (t != null) "$msg\n${Log.getStackTraceString(t)}" else msg
        log(LogLevel.E, tag, fullMsg)
    }

    fun dCrypto(tag: String, msg: String) {
        if (!enabled) return
        val filtered = if (advanced) msg else filterCrypto(msg)
        addEntry(LogLevel.D, tag, filtered, isAdvanced = true)
    }

    fun getLogs(): List<LogEntry> = synchronized(logs) { logs.toList() }

    fun clearLogs() = synchronized(logs) { logs.clear() }

    fun setEnabled(v: Boolean) { enabled = v }

    fun setAdvanced(v: Boolean) { advanced = v }

    fun isEnabled(): Boolean = enabled

    fun isAdvanced(): Boolean = advanced

    private fun log(level: LogLevel, tag: String, msg: String) {
        if (!enabled) return
        val filtered = filterSensitive(msg)
        addEntry(level, tag, filtered)
    }

    private fun addEntry(level: LogLevel, tag: String, msg: String, isAdvanced: Boolean = false) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = msg,
            isAdvanced = isAdvanced,
        )
        synchronized(logs) {
            logs.add(entry)
            while (logs.size > MAX_LOGS) logs.removeFirst()
        }
    }

    private fun filterSensitive(msg: String): String {
        var result = msg
        for (key in sensitiveKeys) {
            // 匹配 "key=xxx" 或 "key: xxx" 或 "key: xxx" 模式
            val pattern = Regex("""(?i)($key\s*[:=]\s*)(\S+)""", RegexOption.IGNORE_CASE)
            result = result.replace(pattern) { match ->
                "${match.groupValues[1]}***"
            }
        }
        // 替换 Base64 长字符串
        val base64Pattern = Regex("""[A-Za-z0-9+/]{50,}={0,2}""")
        result = result.replace(base64Pattern) { "[Base64:${it.value.length} chars]" }
        return result
    }

    private fun filterCrypto(msg: String): String {
        // 加密内容在非高级模式下隐藏
        val base64Pattern = Regex("""[A-Za-z0-9+/]{20,}={0,2}""")
        return base64Pattern.replace(msg) { "[encrypted:${it.value.length} chars]" }
    }

    fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }
}
