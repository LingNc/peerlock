package com.peerlock.domain.request

import com.peerlock.data.prefs.SecurePrefs

/**
 * 请求频率限制 + 信封有效性验证。
 * 规范 7.7：信封有效期 5 分钟，请求频率 30 秒 1 次。
 */
class RequestGuard(
    private val securePrefs: SecurePrefs,
) {
    companion object {
        private const val ENVELOPE_MAX_AGE_SECONDS = 300L
        private const val RATE_LIMIT_MS = 30_000L
    }

    private val lastRequestTime = mutableMapOf<String, Long>()

    fun isEnvelopeValid(timestampSeconds: Long): Boolean {
        val nowSeconds = System.currentTimeMillis() / 1000
        return kotlin.math.abs(nowSeconds - timestampSeconds) <= ENVELOPE_MAX_AGE_SECONDS
    }

    fun checkRateLimit(requestType: String): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastRequestTime[requestType] ?: 0L
        return (now - lastTime) >= RATE_LIMIT_MS
    }

    fun recordRequest(requestType: String) {
        lastRequestTime[requestType] = System.currentTimeMillis()
    }

    fun isConsumed(requestId: String): Boolean {
        return securePrefs.consumedEnvelopes.contains(requestId)
    }

    fun markConsumed(requestId: String) {
        securePrefs.addConsumedEnvelope(requestId)
    }
}
