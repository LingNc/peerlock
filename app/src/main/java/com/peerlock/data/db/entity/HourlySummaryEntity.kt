package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "usage_hourly_summary",
    primaryKeys = ["packageName", "date", "hour"],
    indices = [Index(value = ["date"])]
)
data class HourlySummaryEntity(
    val packageName: String,
    val date: String,
    val hour: Int,
    val totalMs: Long
)
