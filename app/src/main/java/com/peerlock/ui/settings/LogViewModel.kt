package com.peerlock.ui.settings

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import com.peerlock.system.log.LogEntry
import com.peerlock.system.log.LogLevel
import com.peerlock.system.log.PeerLockLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class LogUiState(
    val enabled: Boolean = true,
    val advanced: Boolean = false,
    val l2Unlocked: Boolean = false,
    val filterLevel: LogLevel? = null,
    val logs: List<LogEntry> = emptyList(),
)

@HiltViewModel
class LogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val L2_WINDOW_MS = 5 * 60 * 1000L
    }

    private val prefs = context.getSharedPreferences("peerlock_debug", 0)

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    init {
        val persistedEnabled = prefs.getBoolean("log_enabled", true)
        val persistedAdvanced = prefs.getBoolean("log_advanced", false)
        val l2Timestamp = prefs.getLong("l2_unlocked_at", 0L)
        val l2Valid = l2Timestamp > 0 && System.currentTimeMillis() - l2Timestamp < L2_WINDOW_MS

        PeerLockLogger.setEnabled(persistedEnabled)
        PeerLockLogger.setAdvanced(persistedAdvanced)

        _uiState.value = LogUiState(
            enabled = persistedEnabled,
            advanced = persistedAdvanced,
            l2Unlocked = l2Valid,
        )
        refreshLogs()

        // l2 过期后自动关闭
        if (l2Valid) {
            val remaining = L2_WINDOW_MS - (System.currentTimeMillis() - l2Timestamp)
            android.os.Handler(context.mainLooper).postDelayed({
                _uiState.value = _uiState.value.copy(l2Unlocked = false)
            }, remaining)
        }
    }

    fun setEnabled(v: Boolean) {
        PeerLockLogger.setEnabled(v)
        prefs.edit().putBoolean("log_enabled", v).apply()
        _uiState.value = _uiState.value.copy(enabled = v)
        if (v) refreshLogs()
    }

    fun setAdvanced(v: Boolean) {
        PeerLockLogger.setAdvanced(v)
        prefs.edit().putBoolean("log_advanced", v).apply()
        _uiState.value = _uiState.value.copy(advanced = v)
        refreshLogs()
    }

    fun setL2Unlocked(v: Boolean) {
        _uiState.value = _uiState.value.copy(l2Unlocked = v)
        if (v) {
            prefs.edit().putLong("l2_unlocked_at", System.currentTimeMillis()).apply()
        }
    }

    fun setFilterLevel(level: LogLevel?) {
        _uiState.value = _uiState.value.copy(filterLevel = level)
        refreshLogs()
    }

    fun refreshLogs() {
        val all = PeerLockLogger.getLogs()
        val filter = _uiState.value.filterLevel
        val filtered = if (filter == null) all else all.filter { it.level == filter }
        val advanced = _uiState.value.advanced
        val visible = if (advanced) filtered else filtered.filter { !it.isAdvanced }
        _uiState.value = _uiState.value.copy(logs = visible.reversed())
    }

    fun clearLogs() {
        PeerLockLogger.clearLogs()
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    fun copyToClipboard() {
        val text = _uiState.value.logs.joinToString("\n") { entry ->
            "${PeerLockLogger.formatTimestamp(entry.timestamp)} [${entry.level}] ${entry.tag}: ${entry.message}"
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PeerLock Logs", text))
    }
}
