package com.peerlock.domain.request

import kotlinx.serialization.Serializable

@Serializable
data class RequestEnvelope(
    val v: Int = 1,
    val type: String,
    val sessionId: String,
    val requestId: String,
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val payload: RequestPayload,
)

@Serializable
data class DeviceInfo(
    val todayScreenTimeMs: Long,
    val suspendedApps: List<String>,
    val isInSafeMode: Boolean,
    val targetAppDetail: AppUsageDetail? = null,
    val installedApps: List<InstalledApp> = emptyList(),
)

@Serializable
data class InstalledApp(
    val packageName: String,
    val appName: String,
)

@Serializable
data class AppUsageDetail(
    val packageName: String,
    val appName: String,
    val todayTotalMs: Long,
    val sessions: List<UsageSession>,
    val currentPolicy: PolicySummary? = null,
    val todayRemainingMs: Long? = null,
)

@Serializable
data class UsageSession(
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
)

@Serializable
data class PolicySummary(
    val dailyLimitMinutes: Int? = null,
    val allowedTimeStart: String? = null,
    val allowedTimeEnd: String? = null,
    val isBlacklist: Boolean = true,
)

@Serializable
sealed class RequestPayload {
    @Serializable
    data class UnlockRequest(
        val targetPackage: String,
        val requestedDuration: Int,
        val durationMode: String,
        val absoluteEndTime: Long? = null,
        val reason: String? = null,
    ) : RequestPayload()

    @Serializable
    data class ConfigRequest(
        val changes: List<PolicyChange>,
        val reason: String? = null,
    ) : RequestPayload()
}

@Serializable
data class PolicyChange(
    val targetPackage: String,
    val dailyLimitMinutes: Int? = null,
    val allowedTimeStart: String? = null,
    val allowedTimeEnd: String? = null,
    val isBlacklist: Boolean? = null,
)

@Serializable
data class ResponseEnvelope(
    val v: Int = 1,
    val type: String,
    val sessionId: String,
    val requestId: String,
    val timestamp: Long,
    val approved: Boolean,
    val payload: ResponsePayload,
)

@Serializable
sealed class ResponsePayload {
    @Serializable
    data class UnlockResponse(
        val targetPackage: String,
        val duration: Int,
        val durationMode: String,
        val absoluteEndTime: Long? = null,
    ) : ResponsePayload()

    @Serializable
    data class ConfigResponse(
        val changes: List<PolicyChange>,
    ) : ResponsePayload()

    @Serializable
    data class Rejected(
        val reason: String? = null,
    ) : ResponsePayload()
}
