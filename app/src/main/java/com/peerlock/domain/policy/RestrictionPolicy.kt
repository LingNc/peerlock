package com.peerlock.domain.policy

import java.time.LocalTime

data class RestrictionPolicy(
    val id: Long = 0,
    val targetPackage: String,
    val dailyLimitMinutes: Int?,
    val allowedTimeStart: LocalTime?,
    val allowedTimeEnd: LocalTime?,
    val isBlacklist: Boolean,
    val isActive: Boolean = true,
    val createdAt: Long,
    val lastModified: Long
)
