package com.peerlock.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class UsageStatsCollectorImpl(
    context: Context,
) : UsageStatsCollector {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override fun getUsageTimeMs(packageName: String, startMs: Long, endMs: Long): Long {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startMs, endMs
        )
        return stats
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }

    override fun queryUsageStats(startMs: Long, endMs: Long): List<AppUsageInfo> {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startMs, endMs
        )
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .map { stat ->
                AppUsageInfo(
                    packageName = stat.packageName,
                    totalTimeMs = stat.totalTimeInForeground,
                    lastUsedMs = stat.lastTimeUsed,
                )
            }
    }

    override fun getCurrentForegroundPackage(): String? {
        val endMs = System.currentTimeMillis()
        val startMs = endMs - 5_000
        val events = usageStatsManager.queryEvents(startMs, endMs)

        var lastForegroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundPackage = event.packageName
            }
        }
        return lastForegroundPackage
    }
}
