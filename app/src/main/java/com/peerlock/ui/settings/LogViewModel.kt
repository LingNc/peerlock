package com.peerlock.ui.settings

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
    val enabled: Boolean = false,
    val advanced: Boolean = false,
    val l2Unlocked: Boolean = false,
    val filterLevel: LogLevel? = null, // null = ALL
    val logs: List<LogEntry> = emptyList(),
)

@HiltViewModel
class LogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    init {
        refreshLogs()
    }

    fun setEnabled(v: Boolean) {
        PeerLockLogger.setEnabled(v)
        _uiState.value = _uiState.value.copy(enabled = v)
        if (v) refreshLogs()
    }

    fun setAdvanced(v: Boolean) {
        PeerLockLogger.setAdvanced(v)
        _uiState.value = _uiState.value.copy(advanced = v)
        refreshLogs()
    }

    fun setL2Unlocked(v: Boolean) {
        _uiState.value = _uiState.value.copy(l2Unlocked = v)
    }

    fun setFilterLevel(level: LogLevel?) {
        _uiState.value = _uiState.value.copy(filterLevel = level)
        refreshLogs()
    }

    fun refreshLogs() {
        val all = PeerLockLogger.getLogs()
        val filter = _uiState.value.filterLevel
        val filtered = if (filter == null) all else all.filter { it.level == filter }
        // 高级调试关闭时隐藏高级日志
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
