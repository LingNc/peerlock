package com.peerlock.domain.request

import com.peerlock.data.prefs.SecurePrefs
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequestGuardTest {

    private lateinit var guard: RequestGuard
    private lateinit var securePrefs: SecurePrefs

    @BeforeEach
    fun setup() {
        securePrefs = mockk(relaxed = true)
        guard = RequestGuard(securePrefs)
    }

    @Test
    fun `当前时间戳信封有效`() {
        val nowSeconds = System.currentTimeMillis() / 1000
        assertTrue(guard.isEnvelopeValid(nowSeconds))
    }

    @Test
    fun `5分钟前信封有效`() {
        val nowSeconds = System.currentTimeMillis() / 1000
        assertTrue(guard.isEnvelopeValid(nowSeconds - 290))
    }

    @Test
    fun `6分钟前信封无效`() {
        val nowSeconds = System.currentTimeMillis() / 1000
        assertFalse(guard.isEnvelopeValid(nowSeconds - 360))
    }

    @Test
    fun `首次请求满足频率限制`() {
        assertTrue(guard.checkRateLimit("unlock"))
    }

    @Test
    fun `30秒内重复请求被拒绝`() {
        guard.recordRequest("unlock")
        assertFalse(guard.checkRateLimit("unlock"))
    }

    @Test
    fun `不同类型独立频率限制`() {
        guard.recordRequest("unlock")
        assertTrue(guard.checkRateLimit("config"))
    }

    @Test
    fun `未消费 requestId 返回 false`() {
        every { securePrefs.consumedEnvelopes } returns setOf("other-id")
        assertFalse(guard.isConsumed("test-id"))
    }

    @Test
    fun `已消费 requestId 返回 true`() {
        every { securePrefs.consumedEnvelopes } returns setOf("test-id")
        assertTrue(guard.isConsumed("test-id"))
    }

    @Test
    fun `markConsumed 调用 addConsumedEnvelope`() {
        guard.markConsumed("test-id")
        verify { securePrefs.addConsumedEnvelope("test-id") }
    }
}
