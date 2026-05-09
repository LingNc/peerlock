package com.peerlock.domain.totp

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * RFC 6238 TOTP 实现。
 * 使用 HmacSHA1、30 秒窗口、6 位数字码。
 */
class TotpEngineImpl : TotpEngine {

    companion object {
        private const val TIME_STEP_SECONDS = 30L
        private const val DIGITS = 6
        private const val HMAC_ALGORITHM = "HmacSHA1"
    }

    override fun currentStep(): Long {
        return System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS
    }

    override fun generateCode(seed: ByteArray, timeStep: Long): String {
        return hotp(seed, timeStep)
    }

    override fun verifyCode(seed: ByteArray, input: String, tolerance: Int): Boolean {
        if (input.length != DIGITS || !input.all { it.isDigit() }) return false
        val currentStep = currentStep()
        for (offset in -tolerance..tolerance) {
            val candidate = generateCode(seed, currentStep + offset)
            if (constantTimeEquals(candidate, input)) return true
        }
        return false
    }

    override fun generateEnvelope(
        type: KeyType,
        sessionId: String,
        code: String,
        config: EnvelopeConfig?
    ): TotpEnvelope {
        return TotpEnvelope(
            v = 1,
            type = type.name.lowercase(),
            code = code,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis() / 1000,
            config = config
        )
    }

    override fun verifyEnvelope(
        envelope: TotpEnvelope,
        seed: ByteArray,
        expectedSessionId: String,
        maxAgeSeconds: Long
    ): EnvelopeResult {
        if (envelope.sessionId != expectedSessionId) {
            return EnvelopeResult.Invalid("会话 ID 不匹配")
        }

        val nowSeconds = System.currentTimeMillis() / 1000
        if (kotlin.math.abs(nowSeconds - envelope.timestamp) > maxAgeSeconds) {
            return EnvelopeResult.Invalid("信封已过期")
        }

        if (!verifyCode(seed, envelope.code, tolerance = 1)) {
            return EnvelopeResult.Invalid("验证码错误")
        }

        return EnvelopeResult.Valid(envelope)
    }

    private fun hotp(key: ByteArray, counter: Long): String {
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        val hash = mac.doFinal(counterBytes)

        val offset = (hash.last() and 0x0F).toInt()
        val truncated = ByteBuffer.wrap(hash, offset, 4).int
        val code = (truncated and 0x7FFFFFFF) % Math.pow(10.0, DIGITS.toDouble()).toInt()

        return code.toString().padStart(DIGITS, '0')
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
