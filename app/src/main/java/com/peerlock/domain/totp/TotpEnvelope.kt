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
)

sealed class EnvelopeResult {
    data class Valid(val envelope: TotpEnvelope) : EnvelopeResult()
    data class Invalid(val reason: String) : EnvelopeResult()
}
