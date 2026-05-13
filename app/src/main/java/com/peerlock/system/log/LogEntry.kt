package com.peerlock.system.log

enum class LogLevel { V, D, I, W, E }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val isAdvanced: Boolean = false,
)
