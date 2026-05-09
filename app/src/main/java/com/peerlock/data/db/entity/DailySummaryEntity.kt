package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "usage_daily_summary",
    primaryKeys = ["packageName", "date"],
    indices = [Index(value = ["date"])]
)
data class DailySummaryEntity(
    val packageName: String,
    val date: String,
    val totalMs: Long,
    val launchCount: Int = 0
)
