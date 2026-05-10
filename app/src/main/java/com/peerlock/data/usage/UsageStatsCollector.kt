package com.peerlock.data.usage

/**
 * 使用统计采集接口。
 * 封装 UsageStatsManager 操作。
 */
interface UsageStatsCollector {
    /** 获取指定时间段某个应用的总使用时长（毫秒） */
    fun getUsageTimeMs(packageName: String, startMs: Long, endMs: Long): Long

    /** 获取指定时间段内所有应用的使用统计 */
    fun queryUsageStats(startMs: Long, endMs: Long): List<AppUsageInfo>

    /** 获取当前前台应用包名（null = 无前台应用） */
    fun getCurrentForegroundPackage(): String?

    /** 基于 UsageEvents 计算各应用在指定时间段内的前台时长（30 秒粒度） */
    fun queryForegroundDurations(startMs: Long, endMs: Long): List<AppUsageInfo>
}

data class AppUsageInfo(
    val packageName: String,
    val totalTimeMs: Long,
    val lastUsedMs: Long,
)
