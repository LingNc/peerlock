package com.peerlock.domain.totp

import kotlinx.serialization.Serializable

@Serializable
data class TotpEnvelope(
    val v: Int = 1,
    val type: String,
    val code: String,
    val sessionId: String,
    val timestamp: Long,
    val config: EnvelopeConfig? = null,
)

@Serializable
data class EnvelopeConfig(
    val durationMinutes: Int? = null,
    val durationMode: String? = null,      // "cumulative" | "absolute"
    val absoluteEndTime: Long? = null,
    val targetPackage: String? = null,
    val requestDataJson: String? = null,   // 序列化的请求/响应数据
    val deviceInfoJson: String? = null,    // 序列化的 DeviceInfo
    val approved: Boolean? = null,
    val rejectReason: String? = null,
)

sealed class EnvelopeResult {
    data class Valid(val envelope: TotpEnvelope) : EnvelopeResult()
    data class Invalid(val reason: String) : EnvelopeResult()
}
