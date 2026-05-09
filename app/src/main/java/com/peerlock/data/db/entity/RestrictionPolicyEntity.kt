package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restriction_policies")
data class RestrictionPolicyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetPackage: String,
    val dailyLimitMinutes: Int?,
    val allowedTimeStart: String?,
    val allowedTimeEnd: String?,
    val isBlacklist: Boolean,
    val isActive: Boolean = true,
    val createdAt: Long,
    val lastModified: Long
)
