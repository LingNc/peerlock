package com.peerlock.data.usage

interface UsageAggregator {
    suspend fun aggregateHourly(date: String, hour: Int)
    suspend fun aggregateDaily(date: String)
    suspend fun cleanupOldRecords()
}
