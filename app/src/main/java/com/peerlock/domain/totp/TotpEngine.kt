package com.peerlock.domain.totp

interface TotpEngine {
    fun generateCode(seed: ByteArray, timeStep: Long = currentStep()): String
    fun verifyCode(seed: ByteArray, input: String, tolerance: Int = 1): Boolean
    fun generateEnvelope(
        type: KeyType,
        sessionId: String,
        code: String,
        config: EnvelopeConfig? = null
    ): TotpEnvelope
    fun verifyEnvelope(
        envelope: TotpEnvelope,
        seed: ByteArray,
        expectedSessionId: String,
        maxAgeSeconds: Long = 300
    ): EnvelopeResult
    fun currentStep(): Long
}
