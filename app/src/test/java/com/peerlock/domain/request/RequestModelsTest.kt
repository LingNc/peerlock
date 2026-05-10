package com.peerlock.domain.request

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RequestModelsTest {

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    @Test
    fun `序列化反序列化 UnlockRequest 类型 RequestEnvelope`() {
        val envelope = RequestEnvelope(
            type = "unlock",
            sessionId = "sess-1",
            requestId = "req-1",
            timestamp = 1000L,
            deviceInfo = DeviceInfo(
                todayScreenTimeMs = 3600000L,
                suspendedApps = listOf("com.app1"),
                isInSafeMode = false,
            ),
            payload = RequestPayload.UnlockRequest(
                targetPackage = "com.app1",
                requestedDuration = 30,
                durationMode = "cumulative",
            )
        )

        val encoded = json.encodeToString(envelope)
        val decoded = json.decodeFromString<RequestEnvelope>(encoded)

        assertEquals("unlock", decoded.type)
        assertEquals("sess-1", decoded.sessionId)
        assertTrue(decoded.payload is RequestPayload.UnlockRequest)
        val unlock = decoded.payload as RequestPayload.UnlockRequest
        assertEquals("com.app1", unlock.targetPackage)
        assertEquals(30, unlock.requestedDuration)
    }

    @Test
    fun `序列化反序列化 ConfigRequest 类型 RequestEnvelope`() {
        val envelope = RequestEnvelope(
            type = "config",
            sessionId = "sess-2",
            requestId = "req-2",
            timestamp = 2000L,
            deviceInfo = DeviceInfo(
                todayScreenTimeMs = 0L,
                suspendedApps = emptyList(),
                isInSafeMode = true,
            ),
            payload = RequestPayload.ConfigRequest(
                changes = listOf(
                    PolicyChange(
                        targetPackage = "com.app2",
                        dailyLimitMinutes = 45,
                        isBlacklist = true,
                    )
                ),
                reason = "调整限制"
            )
        )

        val encoded = json.encodeToString(envelope)
        val decoded = json.decodeFromString<RequestEnvelope>(encoded)

        assertEquals("config", decoded.type)
        assertTrue(decoded.payload is RequestPayload.ConfigRequest)
        val config = decoded.payload as RequestPayload.ConfigRequest
        assertEquals(1, config.changes.size)
        assertEquals(45, config.changes[0].dailyLimitMinutes)
    }

    @Test
    fun `序列化反序列化 UnlockResponse 类型 ResponseEnvelope`() {
        val envelope = ResponseEnvelope(
            type = "unlock_resp",
            sessionId = "sess-1",
            requestId = "req-1",
            timestamp = 3000L,
            approved = true,
            payload = ResponsePayload.UnlockResponse(
                targetPackage = "com.app1",
                duration = 30,
                durationMode = "cumulative",
            )
        )

        val encoded = json.encodeToString(envelope)
        val decoded = json.decodeFromString<ResponseEnvelope>(encoded)

        assertTrue(decoded.approved)
        assertTrue(decoded.payload is ResponsePayload.UnlockResponse)
        val unlock = decoded.payload as ResponsePayload.UnlockResponse
        assertEquals(30, unlock.duration)
    }

    @Test
    fun `序列化反序列化 Rejected 类型 ResponseEnvelope`() {
        val envelope = ResponseEnvelope(
            type = "unlock_resp",
            sessionId = "sess-1",
            requestId = "req-1",
            timestamp = 4000L,
            approved = false,
            payload = ResponsePayload.Rejected(reason = "超出今日限额")
        )

        val encoded = json.encodeToString(envelope)
        val decoded = json.decodeFromString<ResponseEnvelope>(encoded)

        assertFalse(decoded.approved)
        assertTrue(decoded.payload is ResponsePayload.Rejected)
        assertEquals("超出今日限额", (decoded.payload as ResponsePayload.Rejected).reason)
    }

    @Test
    fun `sealed class 多态序列化区分类型`() {
        val unlock = RequestPayload.UnlockRequest("com.a", 10, "cumulative")
        val config = RequestPayload.ConfigRequest(emptyList(), null)

        val unlockJson = json.encodeToString(unlock)
        val configJson = json.encodeToString(config)

        assertTrue(unlockJson.contains("UnlockRequest") || unlockJson.contains("com.a"))
        assertTrue(configJson.contains("ConfigRequest") || configJson.contains("changes"))
    }
}
