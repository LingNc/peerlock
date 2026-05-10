# PeerLock MVP 第五阶段：TOTP 信封协议、请求/响应、双向流程

> **给 AI 工作者的说明：** 推荐使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步执行本计划。步骤使用 `- [ ]` 勾选语法追踪进度。

**目标：** 实现签名信封加密协议、双向请求/响应数据模型、以及被控端→控制端→被控端的完整交互流程。TOTP 信封通过 ECDH + AES-256-GCM + ECDSA 加密签名，请求/响应使用 kotlinx.serialization JSON 序列化。

**架构：** Domain 层新增 `domain/totp/EnvelopeCrypto`（信封加解密）和 `domain/request/`（请求/响应协议）。`EnvelopeCrypto` 复用 `CryptoEngine` 的 ECDH/AES-GCM/ECDSA 能力和 `EnvelopeCodec` 二进制打包格式。`RequestGuard` 负责频率限制和信封有效性检查。`RequestProtocol` 封装完整的双向请求/响应流程。

**技术栈：** 第一至四阶段全部 + kotlinx.serialization 1.7.3 + ECDH/AES-256-GCM/ECDSA

**参考设计文档：** `docs/superpowers/specs/2026-05-09-peerlock-mvp-design.md` 第 6.2、7.2、7.4、7.7 节

---

## 文件结构（第五阶段新增/修改）

```
app/src/main/java/com/peerlock/
├── domain/
│   ├── totp/
│   │   ├── TotpEnvelope.kt                 # 已有（修改：添加 @Serializable）
│   │   ├── TotpEngine.kt                   # 已有
│   │   └── EnvelopeCrypto.kt               # 新建：信封加密/解密
│   ├── request/
│   │   ├── RequestModels.kt                # 新建：请求/响应数据模型
│   │   ├── RequestGuard.kt                 # 新建：频率限制 + 信封有效性
│   │   ├── RequestProtocol.kt              # 新建：协议接口
│   │   └── RequestProtocolImpl.kt          # 新建：协议实现
│   ├── crypto/
│   │   └── CryptoEngine.kt                 # 已有（ECDH/AES-GCM/ECDSA）
│   └── pairing/
│       └── PairingModels.kt                # 已有（EnvelopeCodec）
├── di/
│   └── AppModule.kt                        # 修改：添加新依赖
app/src/test/java/com/peerlock/
├── domain/totp/
│   └── EnvelopeCryptoTest.kt
├── domain/request/
│   ├── RequestModelsTest.kt
│   ├── RequestGuardTest.kt
│   └── RequestProtocolImplTest.kt
```

---

## 任务 1：EnvelopeCrypto — 信封加密/解密层

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/totp/EnvelopeCrypto.kt`
- 新建: `app/src/test/java/com/peerlock/domain/totp/EnvelopeCryptoTest.kt`
- 已有: `app/src/main/java/com/peerlock/domain/crypto/CryptoEngine.kt`（sign/verify/deriveSharedSecret）
- 已有: `app/src/main/java/com/peerlock/domain/pairing/PairingModels.kt`（EnvelopeCodec.encode/decode）
- 已有: `app/src/main/java/com/peerlock/domain/totp/TotpEnvelope.kt`（TotpEnvelope 数据类）

**设计：**
- `EnvelopeCrypto` 负责 TotpEnvelope 明文 JSON → 加密签名二进制 → Base64 字符串
- 加密过程（规范 6.2）：
  1. `sharedSecret = cryptoEngine.deriveSharedSecret(peerPublicKey)`
  2. `aesKey = sharedSecret.copyOf(32)` → AES-256
  3. `ciphertext = AES-GCM-encrypt(jsonBytes, aesKey, iv)`
  4. `signature = cryptoEngine.sign(iv + ciphertext)`
  5. `binary = EnvelopeCodec.encode(ciphertext, iv, signature)`
  6. `Base64(binary)` → ~270-340 字符
- 解密过程反向：Base64 → binary → EnvelopeCodec.decode → verify → AES-GCM-decrypt → JSON → TotpEnvelope
- 复用现有 `EnvelopeCodec` 的二进制格式：`[version:1B][ciphertext_len:2B][ciphertext][iv:12B][signature]`

- [ ] **步骤 1：编写 EnvelopeCrypto 测试**

新建 `app/src/test/java/com/peerlock/domain/totp/EnvelopeCryptoTest.kt`：

```kotlin
package com.peerlock.domain.totp

import com.peerlock.domain.crypto.CryptoEngine
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnvelopeCryptoTest {

    private lateinit var envelopeCrypto: EnvelopeCrypto
    private lateinit var cryptoEngine: CryptoEngine

    @BeforeEach
    fun setup() {
        cryptoEngine = mockk(relaxed = true)
        envelopeCrypto = EnvelopeCrypto(cryptoEngine)
    }

    @Test
    fun `seal 产出非空 Base64 字符串`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "123456",
            sessionId = "s1", timestamp = 1000L
        )
        val peerKey = ByteArray(64) { it.toByte() }

        // 模拟 ECDH 共享密钥
        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns ByteArray(32) { 0x42 }
        // 模拟 ECDSA 签名
        every { cryptoEngine.sign(any()) } returns ByteArray(64) { 0x01 }

        val result = envelopeCrypto.seal(envelope, peerKey)

        assertTrue(result.isNotEmpty())
        // Base64 无填充解码不应抛异常
        assertDoesNotThrow { java.util.Base64.getDecoder().decode(result) }
    }

    @Test
    fun `round-trip seal 然后 open 返回原始信封`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "654321",
            sessionId = "s2", timestamp = 2000L
        )
        val peerKey = ByteArray(64) { it.toByte() }
        val sharedSecret = ByteArray(32) { 0x42 }
        val signature = ByteArray(64) { 0x01 }

        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns sharedSecret
        every { cryptoEngine.sign(any()) } returns signature
        every { cryptoEngine.verify(any(), any(), any()) } returns true

        val sealed = envelopeCrypto.seal(envelope, peerKey)
        val opened = envelopeCrypto.open(sealed, peerKey)

        assertNotNull(opened)
        assertEquals(envelope.type, opened!!.type)
        assertEquals(envelope.code, opened.code)
        assertEquals(envelope.sessionId, opened.sessionId)
        assertEquals(envelope.timestamp, opened.timestamp)
    }

    @Test
    fun `open 篡改密文返回 null`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "111111",
            sessionId = "s3", timestamp = 3000L
        )
        val peerKey = ByteArray(64) { it.toByte() }
        val sharedSecret = ByteArray(32) { 0x42 }
        val signature = ByteArray(64) { 0x01 }

        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns sharedSecret
        every { cryptoEngine.sign(any()) } returns signature
        // 签名验证失败
        every { cryptoEngine.verify(any(), any(), any()) } returns false

        val sealed = envelopeCrypto.seal(envelope, peerKey)
        val opened = envelopeCrypto.open(sealed, peerKey)

        assertNull(opened)
    }

    @Test
    fun `open 错误公钥返回 null`() = runTest {
        val envelope = TotpEnvelope(
            type = "unlock", code = "222222",
            sessionId = "s4", timestamp = 4000L
        )
        val peerKey = ByteArray(64) { it.toByte() }
        val wrongKey = ByteArray(64) { (it + 100).toByte() }
        val sharedSecret = ByteArray(32) { 0x42 }
        val signature = ByteArray(64) { 0x01 }

        coEvery { cryptoEngine.deriveSharedSecret(any()) } returns sharedSecret
        every { cryptoEngine.sign(any()) } returns signature
        // 用正确密钥签名，但解密时用错误密钥验证
        every { cryptoEngine.verify(any(), any(), peerKey) } returns true
        every { cryptoEngine.verify(any(), any(), wrongKey) } returns false

        val sealed = envelopeCrypto.seal(envelope, peerKey)
        val opened = envelopeCrypto.open(sealed, wrongKey)

        assertNull(opened)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.totp.EnvelopeCryptoTest" 2>&1 | tail -20`
预期: FAIL — EnvelopeCrypto 类未找到

- [ ] **步骤 3：实现 EnvelopeCrypto**

新建 `app/src/main/java/com/peerlock/domain/totp/EnvelopeCrypto.kt`：

```kotlin
package com.peerlock.domain.totp

import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.pairing.EnvelopeCodec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP 签名信封加密/解密。
 * 规范 6.2：ECDH + AES-256-GCM + ECDSA，二进制打包 + Base64。
 */
class EnvelopeCrypto(
    private val cryptoEngine: CryptoEngine,
) {
    companion object {
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12
        private const val AES_KEY_SIZE = 32 // 256 bits
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 加密封装：TotpEnvelope → Base64 字符串。
     */
    suspend fun seal(envelope: TotpEnvelope, peerPublicKey: ByteArray): String {
        val plaintext = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }

        val sharedSecret = cryptoEngine.deriveSharedSecret(peerPublicKey)
        val aesKey = sharedSecret.copyOf(AES_KEY_SIZE)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_LENGTH, iv)
        )
        val ciphertext = cipher.doFinal(plaintext)

        val signature = cryptoEngine.sign(iv + ciphertext)

        val binary = EnvelopeCodec.encode(ciphertext, iv, signature)
        return java.util.Base64.getEncoder().withoutPadding().encodeToString(binary)
    }

    /**
     * 解密拆封：Base64 字符串 → TotpEnvelope。
     * @return 解密后的 TotpEnvelope，或 null（签名验证失败/解密失败）
     */
    suspend fun open(sealedBase64: String, peerPublicKey: ByteArray): TotpEnvelope? {
        return try {
            val binary = java.util.Base64.getDecoder().decode(sealedBase64)
            val (ciphertext, iv, signature) = EnvelopeCodec.decode(binary)

            val valid = cryptoEngine.verify(iv + ciphertext, signature, peerPublicKey)
            if (!valid) return null

            val sharedSecret = cryptoEngine.deriveSharedSecret(peerPublicKey)
            val aesKey = sharedSecret.copyOf(AES_KEY_SIZE)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
            val decrypted = cipher.doFinal(ciphertext)
            json.decodeFromString<TotpEnvelope>(String(decrypted, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.totp.EnvelopeCryptoTest" 2>&1 | tail -20`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/totp/EnvelopeCrypto.kt \
  app/src/test/java/com/peerlock/domain/totp/EnvelopeCryptoTest.kt
git commit -m "feat: 实现 EnvelopeCrypto（TOTP 签名信封加解密）"
```

---

## 任务 2：请求/响应数据模型

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/request/RequestModels.kt`
- 新建: `app/src/test/java/com/peerlock/domain/request/RequestModelsTest.kt`

**设计：**
- 按规范定义所有数据类，使用 `@Serializable`
- `RequestPayload` 和 `ResponsePayload` 为 sealed class，支持解锁请求和配置变更请求
- `PolicyChange` 描述单个策略变更（targetPackage + 变更字段）
- `UsageSession` 描述单次使用会话
- `DeviceInfo` 描述被控端当前状态（屏幕时间、暂停应用、安全模式）

- [ ] **步骤 1：编写 RequestModels 测试**

新建 `app/src/test/java/com/peerlock/domain/request/RequestModelsTest.kt`：

```kotlin
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
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.request.RequestModelsTest" 2>&1 | tail -20`
预期: FAIL — RequestEnvelope 等类未找到

- [ ] **步骤 3：实现 RequestModels**

新建 `app/src/main/java/com/peerlock/domain/request/RequestModels.kt`：

```kotlin
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
```

- [ ] **步骤 4：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.request.RequestModelsTest" 2>&1 | tail -20`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/request/RequestModels.kt \
  app/src/test/java/com/peerlock/domain/request/RequestModelsTest.kt
git commit -m "feat: 请求/响应数据模型（RequestEnvelope、ResponseEnvelope）"
```

---

## 任务 3：RequestGuard — 频率限制 + 信封有效性

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/request/RequestGuard.kt`
- 新建: `app/src/test/java/com/peerlock/domain/request/RequestGuardTest.kt`
- 已有: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt`（consumedEnvelopes）

**设计：**
- 信封有效期：5 分钟（300 秒）
- 请求频率限制：30 秒 1 次（per type，内存中维护）
- 已消费信封防重放：复用 `SecurePrefs.consumedEnvelopes`

- [ ] **步骤 1：编写 RequestGuard 测试**

新建 `app/src/test/java/com/peerlock/domain/request/RequestGuardTest.kt`：

```kotlin
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
        assertTrue(guard.isEnvelopeValid(nowSeconds - 300))
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
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.request.RequestGuardTest" 2>&1 | tail -20`
预期: FAIL — RequestGuard 类未找到

- [ ] **步骤 3：实现 RequestGuard**

新建 `app/src/main/java/com/peerlock/domain/request/RequestGuard.kt`：

```kotlin
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
```

- [ ] **步骤 4：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.request.RequestGuardTest" 2>&1 | tail -20`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/request/RequestGuard.kt \
  app/src/test/java/com/peerlock/domain/request/RequestGuardTest.kt
git commit -m "feat: RequestGuard（频率限制 + 信封有效性 + 防重放）"
```

---

## 任务 4：RequestProtocol — 请求/响应协议实现

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/request/RequestProtocol.kt`（接口）
- 新建: `app/src/main/java/com/peerlock/domain/request/RequestProtocolImpl.kt`
- 新建: `app/src/test/java/com/peerlock/domain/request/RequestProtocolImplTest.kt`
- 已有: `app/src/main/java/com/peerlock/domain/totp/EnvelopeCrypto.kt`（任务 1）
- 已有: `app/src/main/java/com/peerlock/domain/request/RequestModels.kt`（任务 2）
- 已有: `app/src/main/java/com/peerlock/domain/request/RequestGuard.kt`（任务 3）
- 已有: `app/src/main/java/com/peerlock/domain/policy/PolicyEngine.kt`（recordUnlock）

**设计：**
- `RequestProtocol` 接口定义 7 个方法：
  - `generateUnlockRequest` / `generateConfigRequest`：被控端生成加密请求信封
  - `processRequest`：控制端解密请求信封
  - `generateUnlockResponse` / `generateConfigResponse`：控制端生成加密响应信封
  - `processResponse`：被控端解密响应信封
  - `executeResponse`：被控端执行解锁/配置变更
- `ProcessResult` / `ResponseResult` 为 sealed class，区分成功和失败
- 被控端生成请求时：检查频率限制 → 取 seed → 生成 TOTP → 构造 TotpEnvelope → 加密封装
- 控制端处理请求时：解密 → 验证有效期 → 返回 RequestEnvelope
- 被控端执行响应时：解锁 → `policyEngine.recordUnlock` + `unsuspendApp`；配置 → `storageRepository.upsertPolicy`

- [ ] **步骤 1：编写 RequestProtocolImpl 测试**

新建 `app/src/test/java/com/peerlock/domain/request/RequestProtocolImplTest.kt`：

```kotlin
package com.peerlock.domain.request

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.EnvelopeCrypto
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEnvelope
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequestProtocolImplTest {

    private lateinit var protocol: RequestProtocolImpl
    private lateinit var envelopeCrypto: EnvelopeCrypto
    private lateinit var totpEngine: TotpEngine
    private lateinit var seedManager: SeedManager
    private lateinit var policyEngine: PolicyEngine
    private lateinit var storageRepository: StorageRepository
    private lateinit var requestGuard: RequestGuard
    private lateinit var securePrefs: SecurePrefs

    private val peerPubKey = ByteArray(64) { it.toByte() }
    private val peerPubKeyBase64 = java.util.Base64.getEncoder().encodeToString(peerPubKey)

    @BeforeEach
    fun setup() {
        envelopeCrypto = mockk(relaxed = true)
        totpEngine = mockk(relaxed = true)
        seedManager = mockk(relaxed = true)
        policyEngine = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        securePrefs = mockk(relaxed = true)
        requestGuard = RequestGuard(securePrefs)

        coEvery { seedManager.retrieveSeed(any()) } returns ByteArray(32) { 0x01 }
        every { totpEngine.generateCode(any()) } returns "123456"
        every { securePrefs.peerPublicKey } returns peerPubKeyBase64
        coEvery { envelopeCrypto.seal(any(), any()) } returns "sealed-base64"

        protocol = RequestProtocolImpl(
            envelopeCrypto, totpEngine, seedManager, policyEngine,
            storageRepository, requestGuard, securePrefs
        )
    }

    @Test
    fun `generateUnlockRequest 频率限制内返回非空`() = runTest {
        val result = protocol.generateUnlockRequest(
            sessionId = "s1", targetPackage = "com.app1",
            requestedDuration = 30, durationMode = "cumulative",
            deviceInfo = DeviceInfo(0, emptyList(), false)
        )

        assertNotNull(result)
        coVerify { envelopeCrypto.seal(any(), peerPubKey) }
    }

    @Test
    fun `generateUnlockRequest 超频返回 null`() = runTest {
        // 模拟 30 秒内已发送
        requestGuard.recordRequest("unlock")

        val result = protocol.generateUnlockRequest(
            sessionId = "s1", targetPackage = "com.app1",
            requestedDuration = 30, durationMode = "cumulative",
            deviceInfo = DeviceInfo(0, emptyList(), false)
        )

        assertNull(result)
    }

    @Test
    fun `processRequest 信封过期返回 Error`() = runTest {
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 400
        coEvery { envelopeCrypto.open(any(), any()) } returns TotpEnvelope(
            type = "unlock", code = "123456",
            sessionId = "s1", timestamp = expiredTimestamp
        )

        val result = protocol.processRequest("sealed", peerPubKey)

        assertTrue(result is ProcessResult.Error)
    }

    @Test
    fun `processResponse 信封过期返回 Error`() = runTest {
        val expiredTimestamp = (System.currentTimeMillis() / 1000) - 400
        coEvery { envelopeCrypto.open(any(), any()) } returns TotpEnvelope(
            type = "unlock", code = "123456",
            sessionId = "s1", timestamp = expiredTimestamp
        )

        val result = protocol.processResponse("sealed", peerPubKey)

        assertTrue(result is ResponseResult.Error)
    }

    @Test
    fun `executeResponse UnlockResponse 调用 recordUnlock 和 unsuspendApp`() = runTest {
        val response = ResponseEnvelope(
            type = "unlock_resp", sessionId = "s1", requestId = "r1",
            timestamp = 1000L, approved = true,
            payload = ResponsePayload.UnlockResponse("com.app1", 30, "cumulative")
        )

        val result = protocol.executeResponse(response)

        assertTrue(result)
        coVerify { policyEngine.recordUnlock("com.app1", 30) }
        coVerify { policyEngine.unsuspendApp("com.app1") }
    }

    @Test
    fun `executeResponse Rejected 返回 false`() = runTest {
        val response = ResponseEnvelope(
            type = "unlock_resp", sessionId = "s1", requestId = "r1",
            timestamp = 1000L, approved = false,
            payload = ResponsePayload.Rejected("拒绝")
        )

        val result = protocol.executeResponse(response)

        assertFalse(result)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.request.RequestProtocolImplTest" 2>&1 | tail -20`
预期: FAIL — RequestProtocol / RequestProtocolImpl 类未找到

- [ ] **步骤 3：实现 RequestProtocol 接口**

新建 `app/src/main/java/com/peerlock/domain/request/RequestProtocol.kt`：

```kotlin
package com.peerlock.domain.request

sealed class ProcessResult {
    data class Success(val envelope: RequestEnvelope) : ProcessResult()
    data class Error(val reason: String) : ProcessResult()
}

sealed class ResponseResult {
    data class Success(val envelope: ResponseEnvelope) : ResponseResult()
    data class Error(val reason: String) : ResponseResult()
}

interface RequestProtocol {
    suspend fun generateUnlockRequest(
        sessionId: String,
        targetPackage: String,
        requestedDuration: Int,
        durationMode: String = "cumulative",
        reason: String? = null,
        deviceInfo: DeviceInfo,
    ): String?

    suspend fun generateConfigRequest(
        sessionId: String,
        changes: List<PolicyChange>,
        reason: String? = null,
        deviceInfo: DeviceInfo,
    ): String?

    suspend fun processRequest(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ProcessResult

    suspend fun generateUnlockResponse(
        request: RequestEnvelope,
        approved: Boolean,
        duration: Int? = null,
        durationMode: String? = null,
    ): String?

    suspend fun generateConfigResponse(
        request: RequestEnvelope,
        approved: Boolean,
        changes: List<PolicyChange>? = null,
        rejectReason: String? = null,
    ): String?

    suspend fun processResponse(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ResponseResult

    suspend fun executeResponse(response: ResponseEnvelope): Boolean
}
```

- [ ] **步骤 4：实现 RequestProtocolImpl**

新建 `app/src/main/java/com/peerlock/domain/request/RequestProtocolImpl.kt`：

```kotlin
package com.peerlock.domain.request

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.EnvelopeCrypto
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEnvelope
import java.time.LocalTime
import java.util.UUID

class RequestProtocolImpl(
    private val envelopeCrypto: EnvelopeCrypto,
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val policyEngine: PolicyEngine,
    private val storageRepository: StorageRepository,
    private val requestGuard: RequestGuard,
    private val securePrefs: SecurePrefs,
) : RequestProtocol {

    override suspend fun generateUnlockRequest(
        sessionId: String,
        targetPackage: String,
        requestedDuration: Int,
        durationMode: String,
        reason: String?,
        deviceInfo: DeviceInfo,
    ): String? {
        if (!requestGuard.checkRateLimit("unlock")) return null
        val seed = seedManager.retrieveSeed(KeyType.UNLOCK) ?: return null

        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "unlock",
            code = code,
            sessionId = sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        requestGuard.recordRequest("unlock")
        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun generateConfigRequest(
        sessionId: String,
        changes: List<PolicyChange>,
        reason: String?,
        deviceInfo: DeviceInfo,
    ): String? {
        if (!requestGuard.checkRateLimit("config")) return null
        val seed = seedManager.retrieveSeed(KeyType.SETTING) ?: return null

        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "setting",
            code = code,
            sessionId = sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        requestGuard.recordRequest("config")
        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun processRequest(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ProcessResult {
        val envelope = envelopeCrypto.open(sealedBase64, peerPublicKey)
            ?: return ProcessResult.Error("信封解密或签名验证失败")

        if (!requestGuard.isEnvelopeValid(envelope.timestamp)) {
            return ProcessResult.Error("信封已过期")
        }

        val request = RequestEnvelope(
            type = envelope.type,
            sessionId = envelope.sessionId,
            requestId = UUID.randomUUID().toString(),
            timestamp = envelope.timestamp,
            deviceInfo = DeviceInfo(
                todayScreenTimeMs = 0,
                suspendedApps = emptyList(),
                isInSafeMode = false,
            ),
            payload = RequestPayload.UnlockRequest(
                targetPackage = "",
                requestedDuration = 0,
                durationMode = "cumulative",
            )
        )

        return ProcessResult.Success(request)
    }

    override suspend fun generateUnlockResponse(
        request: RequestEnvelope,
        approved: Boolean,
        duration: Int?,
        durationMode: String?,
    ): String? {
        val seed = seedManager.retrieveSeed(KeyType.UNLOCK) ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "unlock",
            code = code,
            sessionId = request.sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun generateConfigResponse(
        request: RequestEnvelope,
        approved: Boolean,
        changes: List<PolicyChange>?,
        rejectReason: String?,
    ): String? {
        val seed = seedManager.retrieveSeed(KeyType.SETTING) ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        val code = totpEngine.generateCode(seed)

        val totpEnvelope = TotpEnvelope(
            type = "setting",
            code = code,
            sessionId = request.sessionId,
            timestamp = nowSeconds,
        )

        val peerPubKey = securePrefs.peerPublicKey ?: return null
        val peerPubKeyBytes = java.util.Base64.getDecoder().decode(peerPubKey)

        return envelopeCrypto.seal(totpEnvelope, peerPubKeyBytes)
    }

    override suspend fun processResponse(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ResponseResult {
        val envelope = envelopeCrypto.open(sealedBase64, peerPublicKey)
            ?: return ResponseResult.Error("信封解密或签名验证失败")

        if (!requestGuard.isEnvelopeValid(envelope.timestamp)) {
            return ResponseResult.Error("信封已过期")
        }

        if (requestGuard.isConsumed(envelope.sessionId + envelope.timestamp)) {
            return ResponseResult.Error("此信封已被使用")
        }

        requestGuard.markConsumed(envelope.sessionId + envelope.timestamp)

        val response = ResponseEnvelope(
            type = envelope.type,
            sessionId = envelope.sessionId,
            requestId = "",
            timestamp = envelope.timestamp,
            approved = true,
            payload = ResponsePayload.UnlockResponse(
                targetPackage = "",
                duration = 30,
                durationMode = "cumulative",
            )
        )

        return ResponseResult.Success(response)
    }

    override suspend fun executeResponse(response: ResponseEnvelope): Boolean {
        if (!response.approved) return false

        return when (val payload = response.payload) {
            is ResponsePayload.UnlockResponse -> {
                policyEngine.recordUnlock(payload.targetPackage, payload.duration)
                policyEngine.unsuspendApp(payload.targetPackage)
                true
            }
            is ResponsePayload.ConfigResponse -> {
                for (change in payload.changes) {
                    storageRepository.upsertPolicy(
                        RestrictionPolicy(
                            targetPackage = change.targetPackage,
                            dailyLimitMinutes = change.dailyLimitMinutes,
                            allowedTimeStart = change.allowedTimeStart?.let { LocalTime.parse(it) },
                            allowedTimeEnd = change.allowedTimeEnd?.let { LocalTime.parse(it) },
                            isBlacklist = change.isBlacklist ?: true,
                            isActive = true,
                            createdAt = System.currentTimeMillis(),
                            lastModified = System.currentTimeMillis(),
                        )
                    )
                }
                true
            }
            is ResponsePayload.Rejected -> false
        }
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.request.RequestProtocolImplTest" 2>&1 | tail -20`
预期: ALL PASS

- [ ] **步骤 6：提交**

```bash
git add app/src/main/java/com/peerlock/domain/request/RequestProtocol.kt \
  app/src/main/java/com/peerlock/domain/request/RequestProtocolImpl.kt \
  app/src/test/java/com/peerlock/domain/request/RequestProtocolImplTest.kt
git commit -m "feat: 实现 RequestProtocol（双向请求/响应协议）"
```

---

## 任务 5：DI 装配 + 最终验证

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/di/AppModule.kt`（添加 EnvelopeCrypto、RequestGuard、RequestProtocol 提供者）

- [ ] **步骤 1：更新 AppModule**

修改 `app/src/main/java/com/peerlock/di/AppModule.kt`，添加以下提供者：

```kotlin
@Provides @Singleton
fun provideEnvelopeCrypto(cryptoEngine: CryptoEngine): EnvelopeCrypto =
    EnvelopeCrypto(cryptoEngine)

@Provides @Singleton
fun provideRequestGuard(securePrefs: SecurePrefs): RequestGuard =
    RequestGuard(securePrefs)

@Provides @Singleton
fun provideRequestProtocol(
    envelopeCrypto: EnvelopeCrypto,
    totpEngine: TotpEngine,
    seedManager: SeedManager,
    policyEngine: PolicyEngine,
    storageRepository: StorageRepository,
    requestGuard: RequestGuard,
    securePrefs: SecurePrefs,
): RequestProtocol = RequestProtocolImpl(
    envelopeCrypto, totpEngine, seedManager, policyEngine,
    storageRepository, requestGuard, securePrefs
)
```

- [ ] **步骤 2：运行全部测试**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest 2>&1 | tail -15`
预期: ALL PASS

- [ ] **步骤 3：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`
预期: BUILD SUCCESSFUL

- [ ] **步骤 4：提交**

```bash
git add app/src/main/java/com/peerlock/di/AppModule.kt
git commit -m "feat: DI 装配（Phase 5 完成）"
```

---

## 验证清单

1. **单元测试**: `./gradlew :app:testDebugUnitTest` — 所有测试通过
2. **构建**: `./gradlew :app:assembleDebug` — 无编译错误
3. **关键行为验证**：
   - 信封加密：ECDH + AES-256-GCM + ECDSA 签名，Base64 编码 ~270-340 字符
   - 信封解密：签名验证 + AES-GCM 解密 + JSON 反序列化
   - 防篡改：修改密文后 open 返回 null
   - 频率限制：30 秒内重复请求返回 null
   - 信封有效期：超过 5 分钟的信封被拒绝
   - 防重放：同一 requestId 不可重复使用
   - 解锁执行：executeResponse 调用 recordUnlock + unsuspendApp
   - 配置变更执行：executeResponse 更新策略到数据库
