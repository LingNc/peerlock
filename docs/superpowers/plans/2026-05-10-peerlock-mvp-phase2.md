# PeerLock MVP 第二阶段：配对协议实现计划

> **给 AI 工作者的说明：** 推荐使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步执行本计划。步骤使用 `- [ ]` 勾选语法追踪进度。

**目标：** 实现完整的配对协议——被控端生成 ECC 密钥对并展示二维码，控制端扫描后生成 TOTP 种子并加密回传，被控端解密存储完成配对。包含数据模型、密钥存储、协议逻辑、二维码编解码。

**架构：** Domain 层定义配对协议接口和数据模型（纯 Kotlin）。Data 层实现密钥存储（KeystoreManager + SecurePrefs）、种子管理和二维码编解码（ZXing）。配对协议通过 CryptoEngine 接口进行加密操作，不直接依赖 Android Keystore。

**技术栈：** 第一阶段全部 + kotlinx-serialization（JSON 序列化）

**参考设计文档：** `docs/superpowers/specs/2026-05-09-peerlock-mvp-design.md` 第 4-7 节

---

## 阶段路线图

| 阶段 | 范围 | 依赖 |
|------|------|------|
| 第一阶段 | 项目脚手架、加密引擎、TOTP 引擎、数据库、DI | — |
| **第二阶段（本计划）** | 配对协议、二维码交换、密钥存储 | 第一阶段 |
| 第三阶段 | Device Owner 设置、应用暂停、前台服务 | 第一阶段 |
| 第四阶段 | 策略引擎、使用统计、时间同步、安全模式 | 第一、三阶段 |
| 第五阶段 | TOTP 信封协议、请求/响应、双向流程 | 第一、二阶段 |
| 第六阶段 | 紧急逃生（终止码、broadcast、固定命令） | 第一、二、三阶段 |
| 第七阶段 | UI（引导、控制端、被控端、通用组件） | 以上全部 |

---

## 文件结构（第二阶段新增）

```
app/src/main/java/com/peerlock/
├── domain/
│   └── pairing/
│       ├── PairingModels.kt              # 配对数据模型（@Serializable）
│       ├── PairingProtocol.kt            # 接口
│       └── PairingProtocolImpl.kt        # 实现
├── data/
│   ├── pairing/
│   │   ├── PairingRepository.kt          # 配对状态存储接口
│   │   └── PairingRepositoryImpl.kt      # 实现（基于 SecurePrefs）
│   └── seed/
│       ├── SeedManager.kt                # 种子生成/加密/存储接口
│       └── SeedManagerImpl.kt            # 实现
├── domain/crypto/
│   └── CryptoEngine.kt                   # 新增 decryptWithPeer 方法
app/src/test/java/com/peerlock/
├── domain/pairing/
│   └── PairingProtocolImplTest.kt
├── data/pairing/
│   └── PairingRepositoryImplTest.kt
└── data/seed/
    └── SeedManagerImplTest.kt
```

---

## 任务 1：配对数据模型 + kotlinx-serialization

**涉及文件：**
- 修改: `gradle/libs.versions.toml`
- 修改: `build.gradle.kts`（根目录）
- 修改: `app/build.gradle.kts`
- 新建: `app/src/main/java/com/peerlock/domain/pairing/PairingModels.kt`
- 新建: `app/src/test/java/com/peerlock/domain/pairing/PairingModelsTest.kt`

- [ ] **步骤 1：添加 kotlinx-serialization 依赖**

修改 `gradle/libs.versions.toml`，在 `[versions]` 中添加：
```toml
serialization = "1.7.3"
```

在 `[libraries]` 中添加：
```toml
# JSON 序列化
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
```

在 `[plugins]` 中添加：
```toml
serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

修改根目录 `build.gradle.kts`，添加：
```kotlin
alias(libs.plugins.serialization) apply false
```

修改 `app/build.gradle.kts`，在 plugins 块添加：
```kotlin
alias(libs.plugins.serialization)
```

在 dependencies 块添加：
```kotlin
// JSON 序列化
implementation(libs.serialization.json)
```

- [ ] **步骤 2：创建配对数据模型**

新建 `app/src/main/java/com/peerlock/domain/pairing/PairingModels.kt`：
```kotlin
package com.peerlock.domain.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 配对请求（被控端 -> 控制端）。
 * 被控端生成 ECC 密钥对后，将公钥编码为二维码展示给控制端。
 */
@Serializable
data class PairingRequest(
    val v: Int = 1,
    val type: String = "pair_req",
    val id: String,              // 配对会话 ID（UUID）
    val pub: String,             // Base64 编码的 ECC 公钥
    val name: String,            // 设备标识（MANUFACTURER + MODEL）
)

/**
 * 配对响应（控制端 -> 被控端）。
 * 控制端生成种子和密钥对后，用被控端公钥加密，展示回传二维码。
 * data 字段为 Base64 编码的加密信封。
 */
@Serializable
data class PairingResponse(
    val v: Int = 1,
    val data: String,            // Base64(加密信封)
)

/**
 * 加密信封内部的明文 payload（加密前）。
 */
@Serializable
data class PairingPayload(
    val v: Int = 1,
    val type: String = "pair_resp",
    val id: String,              // 配对会话 ID（与请求匹配）
    val seeds: Map<String, String>,  // KeyType.name -> Base64(种子)
    val pub: String,             // Base64(控制端 ECC 公钥)
    val name: String,            // 控制端设备标识
)

/**
 * 配对结果。
 */
sealed class PairingResult {
    data class Success(
        val sessionId: String,
        val role: String,
    ) : PairingResult()

    data class Error(val reason: String) : PairingResult()
}

/**
 * 加密信封的二进制格式：
 * [version:1B][ciphertext_len:2B][ciphertext][iv:12B][signature]
 *
 * - version: 固定 0x01
 * - ciphertext_len: 大端序 uint16
 * - ciphertext: AES-256-GCM 密文（含 16 字节 tag）
 * - iv: 12 字节随机 IV
 * - signature: ECDSA/Ed25519 签名（覆盖 iv + ciphertext）
 */
object EnvelopeCodec {
    private const val VERSION = 1
    private const val IV_SIZE = 12

    fun encode(ciphertext: ByteArray, iv: ByteArray, signature: ByteArray): ByteArray {
        require(iv.size == IV_SIZE) { "IV 必须为 $IV_SIZE 字节" }
        val version = byteArrayOf(VERSION.toByte())
        val len = byteArrayOf(
            (ciphertext.size shr 8).toByte(),
            (ciphertext.size and 0xFF).toByte()
        )
        return version + len + ciphertext + iv + signature
    }

    fun decode(envelope: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        require(envelope.size > 1 + 2 + IV_SIZE) { "信封数据过短" }
        require(envelope[0].toInt() == VERSION) { "不支持的信封版本: ${envelope[0]}" }
        val ciphertextLen = ((envelope[1].toInt() and 0xFF) shl 8) or (envelope[2].toInt() and 0xFF)
        val expectedSize = 1 + 2 + ciphertextLen + IV_SIZE
        require(envelope.size > expectedSize) { "信封数据不完整" }

        val ciphertext = envelope.sliceArray(3 until 3 + ciphertextLen)
        val iv = envelope.sliceArray(3 + ciphertextLen until 3 + ciphertextLen + IV_SIZE)
        val signature = envelope.sliceArray(3 + ciphertextLen + IV_SIZE until envelope.size)
        return Triple(ciphertext, iv, signature)
    }
}
```

- [ ] **步骤 3：编写序列化测试**

新建 `app/src/test/java/com/peerlock/domain/pairing/PairingModelsTest.kt`：
```kotlin
package com.peerlock.domain.pairing

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PairingModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `PairingRequest 序列化往返一致`() {
        val request = PairingRequest(
            id = UUID.randomUUID().toString(),
            pub = "BKyDx9example",
            name = "Pixel 7"
        )
        val encoded = json.encodeToString(PairingRequest.serializer(), request)
        val decoded = json.decodeFromString(PairingRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `PairingResponse 序列化往返一致`() {
        val response = PairingResponse(data = "base64encodeddata")
        val encoded = json.encodeToString(PairingResponse.serializer(), response)
        val decoded = json.decodeFromString(PairingResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `PairingPayload 序列化往返一致`() {
        val payload = PairingPayload(
            id = UUID.randomUUID().toString(),
            seeds = mapOf(
                "setting" to "c2VlZDE=",
                "unlock" to "c2VlZDI=",
                "destroy" to "c2VlZDM="
            ),
            pub = "BKxYz7example",
            name = "Galaxy S24"
        )
        val encoded = json.encodeToString(PairingPayload.serializer(), payload)
        val decoded = json.decodeFromString(PairingPayload.serializer(), encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun `EnvelopeCodec 编码解码往返一致`() {
        val ciphertext = ByteArray(100) { it.toByte() }
        val iv = ByteArray(12) { (it + 50).toByte() }
        val signature = ByteArray(64) { (it + 100).toByte() }

        val envelope = EnvelopeCodec.encode(ciphertext, iv, signature)
        val (decodedCiphertext, decodedIv, decodedSignature) = EnvelopeCodec.decode(envelope)

        assertArrayEquals(ciphertext, decodedCiphertext)
        assertArrayEquals(iv, decodedIv)
        assertArrayEquals(signature, decodedSignature)
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.pairing.PairingModelsTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts \
  app/src/main/java/com/peerlock/domain/pairing/ \
  app/src/test/java/com/peerlock/domain/pairing/
git commit -m "feat: 添加配对数据模型和 kotlinx-serialization

- PairingRequest/Response/Payload 数据类
- EnvelopeCodec 二进制信封编解码
- kotlinx-serialization JSON 序列化"
```

---

## 任务 2：CryptoEngine 补全（decryptWithPeer）

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/domain/crypto/CryptoEngine.kt`
- 修改: `app/src/main/java/com/peerlock/domain/crypto/P256CryptoEngine.kt`
- 修改: `app/src/main/java/com/peerlock/domain/crypto/X25519CryptoEngine.kt`
- 修改: `app/src/main/java/com/peerlock/domain/crypto/AdaptiveCryptoEngine.kt`
- 修改: `app/src/test/java/com/peerlock/domain/crypto/P256CryptoEngineTest.kt`
- 修改: `app/src/test/java/com/peerlock/domain/crypto/X25519CryptoEngineTest.kt`

- [ ] **步骤 1：添加 decryptWithPeer 到 CryptoEngine 接口**

修改 `app/src/main/java/com/peerlock/domain/crypto/CryptoEngine.kt`，在接口中添加：
```kotlin
    /**
     * 使用对方公钥通过 ECDH 派生密钥解密数据。
     * 与 encrypt(data, peerPublicKey) 配对使用。
     */
    suspend fun decryptWithPeer(data: ByteArray, peerPublicKey: ByteArray): ByteArray
```

- [ ] **步骤 2：运行构建验证接口变更**

运行: `./gradlew assembleDebug`
预期: 编译失败——所有实现类缺少 decryptWithPeer

- [ ] **步骤 3：在 P256CryptoEngine 中实现**

修改 `app/src/main/java/com/peerlock/domain/crypto/P256CryptoEngine.kt`，将现有的 `suspend fun decryptWithPeerKey` 重命名为 `decryptWithPeer` 并实现接口方法：

将 `suspend fun decryptWithPeerKey(data: ByteArray, peerPublicKey: ByteArray): ByteArray` 改为：
```kotlin
    override suspend fun decryptWithPeer(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
```

同时更新 `decrypt` 方法保持抛出 NotImplementedError。

- [ ] **步骤 4：在 X25519CryptoEngine 中实现**

修改 `app/src/main/java/com/peerlock/domain/crypto/X25519CryptoEngine.kt`，将 `suspend fun decryptWithPeerKey` 改为：
```kotlin
    override suspend fun decryptWithPeer(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
```

- [ ] **步骤 5：在 AdaptiveCryptoEngine 中添加委托**

修改 `app/src/main/java/com/peerlock/domain/crypto/AdaptiveCryptoEngine.kt`，添加：
```kotlin
    override suspend fun decryptWithPeer(data: ByteArray, peerPublicKey: ByteArray): ByteArray =
        delegate.decryptWithPeer(data, peerPublicKey)
```

- [ ] **步骤 6：更新测试中的方法名**

修改 `P256CryptoEngineTest.kt` 和 `X25519CryptoEngineTest.kt`，将所有 `decryptWithPeerKey` 调用改为 `decryptWithPeer`。

- [ ] **步骤 7：添加加解密往返测试**

在 `P256CryptoEngineTest.kt` 中添加：
```kotlin
    @Test
    fun `encrypt + decryptWithPeer 往返一致`() = runTest {
        val alice = P256CryptoEngine()
        val bob = P256CryptoEngine()
        val aliceKeyPair = alice.generateKeyPair()
        val bobKeyPair = bob.generateKeyPair()
        val plaintext = "配对数据".toByteArray()

        val encrypted = alice.encrypt(plaintext, bobKeyPair.publicKey)
        val decrypted = bob.decryptWithPeer(encrypted, aliceKeyPair.publicKey)

        assertArrayEquals(plaintext, decrypted)
    }
```

- [ ] **步骤 8：运行全部测试**

运行: `./gradlew :app:testDebugUnitTest`
预期: ALL PASS

- [ ] **步骤 9：提交**

```bash
git add app/src/main/java/com/peerlock/domain/crypto/
git add app/src/test/java/com/peerlock/domain/crypto/
git commit -m "feat: CryptoEngine 补全 decryptWithPeer 方法

统一加密/解密接口，支持 ECDH 密钥协商解密。
P256/X25519/Adaptive 三个引擎均已实现。"
```

---

## 任务 3：种子管理器（SeedManager）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/data/seed/SeedManager.kt`
- 新建: `app/src/main/java/com/peerlock/data/seed/SeedManagerImpl.kt`
- 新建: `app/src/test/java/com/peerlock/data/seed/SeedManagerImplTest.kt`
- 修改: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt`（添加种子存储字段）

- [ ] **步骤 1：扩展 SecurePrefs 添加种子存储**

修改 `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt`，添加：
```kotlin
    // 配对材料
    var peerPublicKey: String?
        get() = prefs.getString("peer_public_key", null)
        set(value) = prefs.edit().putString("peer_public_key", value).apply()

    var encryptedSeedSetting: String?
        get() = prefs.getString("enc_seed_setting", null)
        set(value) = prefs.edit().putString("enc_seed_setting", value).apply()

    var encryptedSeedUnlock: String?
        get() = prefs.getString("enc_seed_unlock", null)
        set(value) = prefs.edit().putString("enc_seed_unlock", value).apply()

    var encryptedSeedDestroy: String?
        get() = prefs.getString("enc_seed_destroy", null)
        set(value) = prefs.edit().putString("enc_seed_destroy", value).apply()

    var myPublicKey: String?
        get() = prefs.getString("my_public_key", null)
        set(value) = prefs.edit().putString("my_public_key", value).apply()
```

- [ ] **步骤 2：创建 SeedManager 接口**

新建 `app/src/main/java/com/peerlock/data/seed/SeedManager.kt`：
```kotlin
package com.peerlock.data.seed

import com.peerlock.domain.totp.KeyType

/**
 * TOTP 种子管理器。
 * 负责生成随机种子、加密存储、解密读取。
 */
interface SeedManager {
    /**
     * 生成 3 个独立的随机 TOTP 种子（每个 20 字节）。
     * 返回 KeyType -> 种子明文 的映射。
     */
    fun generateSeeds(): Map<KeyType, ByteArray>

    /**
     * 加密种子并存储。
     */
    suspend fun storeSeeds(seeds: Map<KeyType, ByteArray>)

    /**
     * 读取并解密指定类型的种子。
     * 未配对时返回 null。
     */
    suspend fun retrieveSeed(keyType: KeyType): ByteArray?

    /**
     * 清除所有存储的种子。
     */
    suspend fun clearSeeds()
}
```

- [ ] **步骤 3：编写 SeedManagerImpl 测试**

新建 `app/src/test/java/com/peerlock/data/seed/SeedManagerImplTest.kt`：
```kotlin
package com.peerlock.data.seed

import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.totp.KeyType
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SeedManagerImplTest {

    private lateinit var seedManager: SeedManagerImpl
    private lateinit var mockKeystore: KeystoreManager
    private lateinit var mockPrefs: SecurePrefs

    @BeforeEach
    fun setup() {
        mockKeystore = mockk()
        mockPrefs = mockk(relaxed = true)
        seedManager = SeedManagerImpl(mockKeystore, mockPrefs)
    }

    @Test
    fun `生成 3 个种子且各为 20 字节`() {
        val seeds = seedManager.generateSeeds()
        assertEquals(3, seeds.size)
        assertTrue(seeds.containsKey(KeyType.SETTING))
        assertTrue(seeds.containsKey(KeyType.UNLOCK))
        assertTrue(seeds.containsKey(KeyType.DESTROY))
        seeds.values.forEach { seed ->
            assertEquals(20, seed.size)
        }
    }

    @Test
    fun `每次生成的种子不同`() {
        val seeds1 = seedManager.generateSeeds()
        val seeds2 = seedManager.generateSeeds()
        KeyType.entries.forEach { keyType ->
            assertFalse(
                seeds1[keyType]!!.contentEquals(seeds2[keyType]!!),
                "$keyType 种子不应相同"
            )
        }
    }

    @Test
    fun `存储种子应调用 KeystoreManager 加密`() {
        val seeds = mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val encrypted = ByteArray(50)

        every { mockKeystore.encryptSeed(any()) } returns encrypted

        kotlinx.coroutines.runBlocking {
            seedManager.storeSeeds(seeds)
        }

        verify(exactly = 3) { mockKeystore.encryptSeed(any()) }
        verify { mockPrefs.encryptedSeedSetting = any() }
        verify { mockPrefs.encryptedSeedUnlock = any() }
        verify { mockPrefs.encryptedSeedDestroy = any() }
    }

    @Test
    fun `读取种子应调用 KeystoreManager 解密`() {
        val encryptedBase64 = "ZW5jcnlwdGVk"

        every { mockPrefs.encryptedSeedSetting } returns encryptedBase64
        every { mockKeystore.decryptSeed(any()) } returns ByteArray(20) { 42 }

        val result = kotlinx.coroutines.runBlocking {
            seedManager.retrieveSeed(KeyType.SETTING)
        }

        assertNotNull(result)
        assertEquals(20, result!!.size)
        verify { mockKeystore.decryptSeed(any()) }
    }

    @Test
    fun `未配对时读取种子返回 null`() {
        every { mockPrefs.encryptedSeedSetting } returns null

        val result = kotlinx.coroutines.runBlocking {
            seedManager.retrieveSeed(KeyType.SETTING)
        }

        assertNull(result)
    }
}
```

- [ ] **步骤 4：运行测试验证失败**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.data.seed.SeedManagerImplTest"`
预期: FAIL — SeedManagerImpl 类未找到

- [ ] **步骤 5：实现 SeedManagerImpl**

新建 `app/src/main/java/com/peerlock/data/seed/SeedManagerImpl.kt`：
```kotlin
package com.peerlock.data.seed

import android.util.Base64
import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.totp.KeyType
import java.security.SecureRandom

class SeedManagerImpl(
    private val keystoreManager: KeystoreManager,
    private val securePrefs: SecurePrefs,
) : SeedManager {

    companion object {
        private const val SEED_LENGTH = 20 // 160 bit
    }

    override fun generateSeeds(): Map<KeyType, ByteArray> {
        val random = SecureRandom()
        return KeyType.entries.associateWith {
            ByteArray(SEED_LENGTH).also { random.nextBytes(it) }
        }
    }

    override suspend fun storeSeeds(seeds: Map<KeyType, ByteArray>) {
        for ((keyType, seed) in seeds) {
            val encrypted = keystoreManager.encryptSeed(seed)
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            when (keyType) {
                KeyType.SETTING -> securePrefs.encryptedSeedSetting = encoded
                KeyType.UNLOCK -> securePrefs.encryptedSeedUnlock = encoded
                KeyType.DESTROY -> securePrefs.encryptedSeedDestroy = encoded
            }
        }
    }

    override suspend fun retrieveSeed(keyType: KeyType): ByteArray? {
        val encoded = when (keyType) {
            KeyType.SETTING -> securePrefs.encryptedSeedSetting
            KeyType.UNLOCK -> securePrefs.encryptedSeedUnlock
            KeyType.DESTROY -> securePrefs.encryptedSeedDestroy
        } ?: return null

        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        return keystoreManager.decryptSeed(encrypted)
    }

    override suspend fun clearSeeds() {
        securePrefs.encryptedSeedSetting = null
        securePrefs.encryptedSeedUnlock = null
        securePrefs.encryptedSeedDestroy = null
    }
}
```

- [ ] **步骤 6：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.data.seed.SeedManagerImplTest"`
预期: ALL PASS

- [ ] **步骤 7：提交**

```bash
git add app/src/main/java/com/peerlock/data/seed/ app/src/test/java/com/peerlock/data/seed/ \
  app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt
git commit -m "feat: 实现 SeedManager（TOTP 种子生成、加密存储）

- 生成 3 个独立 20 字节随机种子
- 使用 KeystoreManager AES-GCM 加密存储
- SecurePrefs 存储 Base64 编码的密文"
```

---

## 任务 4：PairingRepository + PairingProtocol

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/data/pairing/PairingRepository.kt`
- 新建: `app/src/main/java/com/peerlock/data/pairing/PairingRepositoryImpl.kt`
- 新建: `app/src/main/java/com/peerlock/domain/pairing/PairingProtocol.kt`
- 新建: `app/src/main/java/com/peerlock/domain/pairing/PairingProtocolImpl.kt`
- 新建: `app/src/test/java/com/peerlock/domain/pairing/PairingProtocolImplTest.kt`

- [ ] **步骤 1：创建 PairingRepository 接口**

新建 `app/src/main/java/com/peerlock/data/pairing/PairingRepository.kt`：
```kotlin
package com.peerlock.data.pairing

/**
 * 配对状态存储接口。
 * 管理密钥对、对方公钥、会话状态。
 */
interface PairingRepository {
    suspend fun storeMyPublicKey(publicKey: ByteArray)
    suspend fun getMyPublicKey(): ByteArray?
    suspend fun storePeerPublicKey(publicKey: ByteArray)
    suspend fun getPeerPublicKey(): ByteArray?
    suspend fun storeSessionId(sessionId: String)
    suspend fun getSessionId(): String?
    suspend fun storeRole(role: String)
    suspend fun getRole(): String?
    suspend fun markPaired()
    suspend fun isPaired(): Boolean
    suspend fun clearPairing()
}
```

- [ ] **步骤 2：实现 PairingRepositoryImpl**

新建 `app/src/main/java/com/peerlock/data/pairing/PairingRepositoryImpl.kt`：
```kotlin
package com.peerlock.data.pairing

import android.util.Base64
import com.peerlock.data.prefs.SecurePrefs

class PairingRepositoryImpl(
    private val securePrefs: SecurePrefs,
) : PairingRepository {

    override suspend fun storeMyPublicKey(publicKey: ByteArray) {
        securePrefs.myPublicKey = Base64.encodeToString(publicKey, Base64.NO_WRAP)
    }

    override suspend fun getMyPublicKey(): ByteArray? {
        return securePrefs.myPublicKey?.let {
            Base64.decode(it, Base64.NO_WRAP)
        }
    }

    override suspend fun storePeerPublicKey(publicKey: ByteArray) {
        securePrefs.peerPublicKey = Base64.encodeToString(publicKey, Base64.NO_WRAP)
    }

    override suspend fun getPeerPublicKey(): ByteArray? {
        return securePrefs.peerPublicKey?.let {
            Base64.decode(it, Base64.NO_WRAP)
        }
    }

    override suspend fun storeSessionId(sessionId: String) {
        securePrefs.sessionId = sessionId
    }

    override suspend fun getSessionId(): String = securePrefs.sessionId ?: ""

    override suspend fun storeRole(role: String) {
        securePrefs.role = role
    }

    override suspend fun getRole(): String = securePrefs.role ?: ""

    override suspend fun markPaired() {
        securePrefs.isPaired = true
    }

    override suspend fun isPaired(): Boolean = securePrefs.isPaired

    override suspend fun clearPairing() {
        securePrefs.clear()
    }
}
```

- [ ] **步骤 3：创建 PairingProtocol 接口**

新建 `app/src/main/java/com/peerlock/domain/pairing/PairingProtocol.kt`：
```kotlin
package com.peerlock.domain.pairing

/**
 * 配对协议接口。
 * 纯 Kotlin，可在 JVM 上测试。
 */
interface PairingProtocol {
    /**
     * 被控端：生成配对请求。
     * 生成 ECC 密钥对，返回包含公钥的请求对象。
     */
    suspend fun generatePairRequest(deviceName: String): PairingRequest

    /**
     * 控制端：处理配对请求，生成配对响应。
     * 生成自己的密钥对和 3 个 TOTP 种子，用对方公钥加密后返回。
     */
    suspend fun processPairRequest(
        request: PairingRequest,
        controllerName: String,
    ): PairingResponse

    /**
     * 被控端：处理配对响应，完成配对。
     * 解密信封，验证签名，存储种子和对方公钥。
     */
    suspend fun processPairResponse(
        response: PairingResponse,
    ): PairingResult

    /**
     * 获取当前配对状态。
     */
    suspend fun isPaired(): Boolean

    /**
     * 获取当前会话 ID。
     */
    suspend fun getSessionId(): String?

    /**
     * 获取当前角色。
     */
    suspend fun getRole(): String?
}
```

- [ ] **步骤 4：编写 PairingProtocolImpl 测试**

新建 `app/src/test/java/com/peerlock/domain/pairing/PairingProtocolImplTest.kt`：
```kotlin
package com.peerlock.domain.pairing

import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.crypto.CryptoKeyPair
import com.peerlock.domain.crypto.P256CryptoEngine
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEngineImpl
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PairingProtocolImplTest {

    private lateinit var controlledProtocol: PairingProtocolImpl
    private lateinit var controllerProtocol: PairingProtocolImpl

    private lateinit var controlledCrypto: CryptoEngine
    private lateinit var controllerCrypto: CryptoEngine
    private lateinit var controlledRepo: PairingRepository
    private lateinit var controllerRepo: PairingRepository
    private lateinit var controlledSeeds: SeedManager
    private lateinit var controllerSeeds: SeedManager
    private lateinit var totpEngine: TotpEngine

    @BeforeEach
    fun setup() {
        controlledCrypto = P256CryptoEngine()
        controllerCrypto = P256CryptoEngine()
        controlledRepo = mockk(relaxed = true)
        controllerRepo = mockk(relaxed = true)
        controlledSeeds = mockk(relaxed = true)
        controllerSeeds = mockk(relaxed = true)
        totpEngine = TotpEngineImpl()

        controlledProtocol = PairingProtocolImpl(
            controlledCrypto, totpEngine, controlledSeeds, controlledRepo
        )
        controllerProtocol = PairingProtocolImpl(
            controllerCrypto, totpEngine, controllerSeeds, controllerRepo
        )
    }

    @Test
    fun `生成配对请求应包含有效公钥和会话 ID`() = runTest {
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        assertEquals("pair_req", request.type)
        assertEquals(1, request.v)
        assertTrue(request.id.isNotBlank())
        assertTrue(request.pub.isNotBlank())
        assertEquals("Pixel 7", request.name)
    }

    @Test
    fun `处理配对请求应生成有效响应`() = runTest {
        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )

        val request = controlledProtocol.generatePairRequest("Pixel 7")
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        assertTrue(response.data.isNotBlank())
    }

    @Test
    fun `完整配对流程应成功`() = runTest {
        // 被控端生成请求
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        // 控制端处理请求
        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        // 被控端处理响应
        coEvery { controlledRepo.getSessionId() } returns request.id
        val result = controlledProtocol.processPairResponse(response)

        assertTrue(result is PairingResult.Success, "配对应成功，实际: $result")
        result as PairingResult.Success
        assertEquals(request.id, result.sessionId)
        assertEquals("controlled", result.role)
    }

    @Test
    fun `篡改密文后配对应失败`() = runTest {
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        // 篡改 data
        val tamperedResponse = PairingResponse(data = response.data + "X")

        coEvery { controlledRepo.getSessionId() } returns request.id
        val result = controlledProtocol.processPairResponse(tamperedResponse)

        assertTrue(result is PairingResult.Error, "篡改后配对应失败")
    }

    @Test
    fun `会话 ID 不匹配时配对应失败`() = runTest {
        val request = controlledProtocol.generatePairRequest("Pixel 7")

        every { controllerSeeds.generateSeeds() } returns mapOf(
            KeyType.SETTING to ByteArray(20) { 1 },
            KeyType.UNLOCK to ByteArray(20) { 2 },
            KeyType.DESTROY to ByteArray(20) { 3 },
        )
        val response = controllerProtocol.processPairRequest(request, "Galaxy S24")

        // 返回错误的会话 ID
        coEvery { controlledRepo.getSessionId() } returns "wrong-session-id"
        val result = controlledProtocol.processPairResponse(response)

        assertTrue(result is PairingResult.Error)
        assertTrue((result as PairingResult.Error).reason.contains("会话"))
    }
}
```

- [ ] **步骤 5：运行测试验证失败**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.pairing.PairingProtocolImplTest"`
预期: FAIL — PairingProtocolImpl 类未找到

- [ ] **步骤 6：实现 PairingProtocolImpl**

新建 `app/src/main/java/com/peerlock/domain/pairing/PairingProtocolImpl.kt`：
```kotlin
package com.peerlock.domain.pairing

import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PairingProtocolImpl(
    private val cryptoEngine: CryptoEngine,
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val pairingRepository: PairingRepository,
) : PairingProtocol {

    private val json = Json { ignoreUnknownKeys = true }
    private var myKeyPair: com.peerlock.domain.crypto.CryptoKeyPair? = null

    override suspend fun generatePairRequest(deviceName: String): PairingRequest {
        val keyPair = cryptoEngine.generateKeyPair()
        myKeyPair = keyPair
        val sessionId = UUID.randomUUID().toString()

        pairingRepository.storeMyPublicKey(keyPair.publicKey)
        pairingRepository.storeSessionId(sessionId)
        pairingRepository.storeRole("controlled")

        return PairingRequest(
            id = sessionId,
            pub = encodeBase64(keyPair.publicKey),
            name = deviceName,
        )
    }

    override suspend fun processPairRequest(
        request: PairingRequest,
        controllerName: String,
    ): PairingResponse {
        val peerPublicKey = decodeBase64(request.pub)

        val seeds = seedManager.generateSeeds()

        val keyPair = cryptoEngine.generateKeyPair()
        myKeyPair = keyPair

        val payload = PairingPayload(
            id = request.id,
            seeds = seeds.mapKeys { it.key.name.lowercase() }
                .mapValues { encodeBase64(it.value) },
            pub = encodeBase64(keyPair.publicKey),
            name = controllerName,
        )

        val plaintext = json.encodeToString(PairingPayload.serializer(), payload).toByteArray()
        val ciphertext = cryptoEngine.encrypt(plaintext, peerPublicKey)

        // 提取 IV（前 12 字节）和实际密文
        val iv = ciphertext.sliceArray(0 until 12)
        val actualCiphertext = ciphertext.sliceArray(12 until ciphertext.size)

        // 签名覆盖 iv + ciphertext
        val toSign = iv + actualCiphertext
        val signature = cryptoEngine.sign(toSign)

        val envelope = EnvelopeCodec.encode(actualCiphertext, iv, signature)
        val envelopeBase64 = encodeBase64(envelope)

        // 存储本地状态
        pairingRepository.storeMyPublicKey(keyPair.publicKey)
        pairingRepository.storePeerPublicKey(peerPublicKey)
        pairingRepository.storeSessionId(request.id)
        pairingRepository.storeRole("controller")
        seedManager.storeSeeds(seeds)

        return PairingResponse(data = envelopeBase64)
    }

    override suspend fun processPairResponse(response: PairingResponse): PairingResult {
        val peerPublicKey = pairingRepository.getPeerPublicKey()
            ?: return PairingResult.Error("未找到对方公钥，请先扫描配对请求")

        val storedSessionId = pairingRepository.getSessionId()

        return try {
            val envelope = decodeBase64(response.data)
            val (actualCiphertext, iv, signature) = EnvelopeCodec.decode(envelope)

            // 验证签名（覆盖 iv + ciphertext）
            val toVerify = iv + actualCiphertext
            if (!cryptoEngine.verify(toVerify, signature, peerPublicKey)) {
                return PairingResult.Error("签名验证失败，数据可能被篡改")
            }

            // 解密：还原为 encrypt() 的输出格式（iv + ciphertext）
            val encryptedData = iv + actualCiphertext
            val plaintext = cryptoEngine.decryptWithPeer(encryptedData, peerPublicKey)

            val payloadString = plaintext.toString(Charsets.UTF_8)
            val payload = json.decodeFromString(PairingPayload.serializer(), payloadString)

            // 验证会话 ID
            if (storedSessionId.isNotBlank() && payload.id != storedSessionId) {
                return PairingResult.Error("会话 ID 不匹配")
            }

            // 存储种子
            val seeds = payload.seeds.mapKeys { KeyType.fromTotpId(it.key) }
                .mapValues { decodeBase64(it.value) }
            seedManager.storeSeeds(seeds)

            // 存储对方公钥
            val controllerPublicKey = decodeBase64(payload.pub)
            pairingRepository.storePeerPublicKey(controllerPublicKey)

            // 标记配对完成
            pairingRepository.markPaired()

            // 清除内存中的明文种子
            seeds.values.forEach { it.fill(0) }

            PairingResult.Success(
                sessionId = payload.id,
                role = "controlled",
            )
        } catch (e: Exception) {
            PairingResult.Error("配对失败: ${e.message}")
        }
    }

    override suspend fun isPaired(): Boolean = pairingRepository.isPaired()
    override suspend fun getSessionId(): String? = pairingRepository.getSessionId()
    override suspend fun getRole(): String? = pairingRepository.getRole()

    private fun encodeBase64(data: ByteArray): String =
        android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

    private fun decodeBase64(encoded: String): ByteArray =
        android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
}
```

**注意：** PairingProtocolImpl 中的 `encodeBase64`/`decodeBase64` 使用了 `android.util.Base64`。在 JVM 单元测试中需要使用 `java.util.Base64`。为了解决这个问题，将 Base64 操作抽象为可替换的依赖，或在测试中使用 Robolectric。当前阶段先使用 Android API，测试在 Android 环境中运行。

**修改：** 将 Base64 操作改为使用 `java.util.Base64`（JVM 兼容），后续在 Android 环境中验证。

将 `encodeBase64` 和 `decodeBase64` 改为：
```kotlin
    private fun encodeBase64(data: ByteArray): String =
        java.util.Base64.getEncoder().withoutPadding().encodeToString(data)

    private fun decodeBase64(encoded: String): ByteArray =
        java.util.Base64.getDecoder().decode(encoded)
```

- [ ] **步骤 7：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.pairing.PairingProtocolImplTest"`
预期: ALL PASS

- [ ] **步骤 8：提交**

```bash
git add app/src/main/java/com/peerlock/data/pairing/ \
  app/src/main/java/com/peerlock/domain/pairing/ \
  app/src/test/java/com/peerlock/domain/pairing/
git commit -m "feat: 实现配对协议（PairingProtocol + PairingRepository）

- 被控端生成 ECC 密钥对 + 配对请求二维码
- 控制端生成 TOTP 种子 + 加密回传二维码
- 被控端解密验证 + 种子加密存储
- ECDH + AES-256-GCM + ECDSA/Ed25519 签名信封
- 会话 ID 防重放"
```

---

## 任务 5：二维码数据编解码（QrCodec）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/data/qr/QrCodec.kt`
- 新建: `app/src/test/java/com/peerlock/data/qr/QrCodecTest.kt`

- [ ] **步骤 1：创建 QrCodec 接口和实现**

新建 `app/src/main/java/com/peerlock/data/qr/QrCodec.kt`：
```kotlin
package com.peerlock.data.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.peerlock.domain.pairing.PairingRequest
import com.peerlock.domain.pairing.PairingResponse
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.EnumMap

/**
 * 二维码编解码器。
 * 将配对请求/响应序列化为可扫描的字符串，以及反向解码。
 * 位图渲染在 UI 层完成（需要 Android Bitmap API）。
 */
object QrCodec {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 将配对请求编码为 QR 码内容字符串。
     * 格式: "PL:req:" + Base64(JSON)
     */
    fun encodeRequest(request: PairingRequest): String {
        val jsonString = json.encodeToString(PairingRequest.serializer(), request)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(jsonString.toByteArray())
        return "PL:req:$encoded"
    }

    /**
     * 将配对响应编码为 QR 码内容字符串。
     * 格式: "PL:resp:" + Base64(JSON)
     */
    fun encodeResponse(response: PairingResponse): String {
        val jsonString = json.encodeToString(PairingResponse.serializer(), response)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(jsonString.toByteArray())
        return "PL:resp:$encoded"
    }

    /**
     * 从 QR 码内容解码为配对请求。
     */
    fun decodeRequest(content: String): PairingRequest {
        require(content.startsWith("PL:req:")) { "不是 PeerLock 配对请求码" }
        val base64 = content.removePrefix("PL:req:")
        val jsonString = String(Base64.getDecoder().decode(base64))
        return json.decodeFromString(PairingRequest.serializer(), jsonString)
    }

    /**
     * 从 QR 码内容解码为配对响应。
     */
    fun decodeResponse(content: String): PairingResponse {
        require(content.startsWith("PL:resp:")) { "不是 PeerLock 配对响应码" }
        val base64 = content.removePrefix("PL:resp:")
        val jsonString = String(Base64.getDecoder().decode(base64))
        return json.decodeFromString(PairingResponse.serializer(), jsonString)
    }

    /**
     * 自动检测 QR 内容类型并解码。
     */
    fun decode(content: String): Any {
        return when {
            content.startsWith("PL:req:") -> decodeRequest(content)
            content.startsWith("PL:resp:") -> decodeResponse(content)
            else -> throw IllegalArgumentException("未知的 PeerLock 二维码格式")
        }
    }

    /**
     * 将字符串内容渲染为 QR 码的 BitMatrix。
     * 位图转换在 UI 层完成。
     */
    fun toBitMatrix(content: String, size: Int = 512): BitMatrix {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = 1
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    }
}
```

- [ ] **步骤 2：编写编解码测试**

新建 `app/src/test/java/com/peerlock/data/qr/QrCodecTest.kt`：
```kotlin
package com.peerlock.data.qr

import com.peerlock.domain.pairing.PairingRequest
import com.peerlock.domain.pairing.PairingResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class QrCodecTest {

    @Test
    fun `配对请求编码解码往返一致`() {
        val request = PairingRequest(
            id = UUID.randomUUID().toString(),
            pub = "BKyDx9examplePublicKey",
            name = "Pixel 7"
        )
        val encoded = QrCodec.encodeRequest(request)
        assertTrue(encoded.startsWith("PL:req:"))

        val decoded = QrCodec.decodeRequest(encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `配对响应编码解码往返一致`() {
        val response = PairingResponse(data = "YWJjZGVmZ2hpams=")
        val encoded = QrCodec.encodeResponse(response)
        assertTrue(encoded.startsWith("PL:resp:"))

        val decoded = QrCodec.decodeResponse(encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `自动检测请求类型`() {
        val request = PairingRequest(id = "test", pub = "pub", name = "dev")
        val encoded = QrCodec.encodeRequest(request)
        val decoded = QrCodec.decode(encoded)
        assertTrue(decoded is PairingRequest)
    }

    @Test
    fun `自动检测响应类型`() {
        val response = PairingResponse(data = "data")
        val encoded = QrCodec.encodeResponse(response)
        val decoded = QrCodec.decode(encoded)
        assertTrue(decoded is PairingResponse)
    }

    @Test
    fun `未知格式应抛出异常`() {
        assertThrows<IllegalArgumentException> {
            QrCodec.decode("unknown:format")
        }
    }

    @Test
    fun `生成 BitMatrix 不为空`() {
        val request = PairingRequest(id = "test", pub = "pub", name = "dev")
        val content = QrCodec.encodeRequest(request)
        val matrix = QrCodec.toBitMatrix(content, 256)
        assertTrue(matrix.width > 0)
        assertTrue(matrix.height > 0)
    }
}
```

- [ ] **步骤 3：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.data.qr.QrCodecTest"`
预期: ALL PASS

- [ ] **步骤 4：提交**

```bash
git add app/src/main/java/com/peerlock/data/qr/ app/src/test/java/com/peerlock/data/qr/
git commit -m "feat: 实现 QrCodec 二维码数据编解码

- PL:req: / PL:resp: 前缀区分请求/响应
- Base64 编码 JSON 数据
- ZXing BitMatrix 生成（UI 层转换为 Bitmap）"
```

---

## 任务 6：DI 装配 + 最终验证

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/di/AppModule.kt`

- [ ] **步骤 1：更新 AppModule 添加新依赖**

修改 `app/src/main/java/com/peerlock/di/AppModule.kt`：
```kotlin
package com.peerlock.di

import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.pairing.PairingRepository
import com.peerlock.data.pairing.PairingRepositoryImpl
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.data.seed.SeedManagerImpl
import com.peerlock.domain.crypto.AdaptiveCryptoEngine
import com.peerlock.domain.crypto.CryptoEngine
import com.peerlock.domain.pairing.PairingProtocol
import com.peerlock.domain.pairing.PairingProtocolImpl
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.domain.totp.TotpEngineImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideCryptoEngine(): CryptoEngine = AdaptiveCryptoEngine()

    @Provides @Singleton
    fun provideTotpEngine(): TotpEngine = TotpEngineImpl()

    @Provides @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides @Singleton
    fun provideSecurePrefs(app: android.app.Application): SecurePrefs =
        SecurePrefs(app.applicationContext)

    @Provides @Singleton
    fun provideSeedManager(
        keystoreManager: KeystoreManager,
        securePrefs: SecurePrefs,
    ): SeedManager = SeedManagerImpl(keystoreManager, securePrefs)

    @Provides @Singleton
    fun providePairingRepository(
        securePrefs: SecurePrefs,
    ): PairingRepository = PairingRepositoryImpl(securePrefs)

    @Provides @Singleton
    fun providePairingProtocol(
        cryptoEngine: CryptoEngine,
        totpEngine: TotpEngine,
        seedManager: SeedManager,
        pairingRepository: PairingRepository,
    ): PairingProtocol = PairingProtocolImpl(
        cryptoEngine, totpEngine, seedManager, pairingRepository
    )
}
```

- [ ] **步骤 2：运行全部测试**

运行: `./gradlew test`
预期: ALL PASS

- [ ] **步骤 3：运行完整构建**

运行: `./gradlew assembleDebug`
预期: BUILD SUCCESSFUL

- [ ] **步骤 4：最终提交**

```bash
git add -A
git commit -m "chore: 第二阶段完成 — PeerLock MVP 配对协议

- 配对数据模型（PairingRequest/Response/Payload）
- CryptoEngine 补全 decryptWithPeer
- SeedManager：TOTP 种子生成、加密存储
- PairingRepository：配对状态管理
- PairingProtocol：完整配对流程（请求-响应-完成）
- QrCodec：二维码数据编解码
- EnvelopeCodec：二进制信封编解码
- Hilt DI 装配"
```

---

## 第二阶段未包含的内容（延迟到后续阶段）

| 内容 | 所属阶段 | 原因 |
|------|---------|------|
| Device Owner 设置流程 | 第三阶段 | 依赖 DeviceAdminReceiver |
| 应用暂停逻辑 | 第三阶段 | 依赖 Device Owner |
| 前台服务 | 第三阶段 | 依赖暂停功能 |
| 策略引擎实现 | 第四阶段 | 依赖服务 + 暂停 |
| 时间同步（NTP） | 第四阶段 | 依赖服务 |
| 使用统计采集 | 第四阶段 | 依赖服务 |
| TOTP 信封传输 | 第五阶段 | 依赖加密 + 配对 |
| 请求/响应协议 | 第五阶段 | 依赖信封 |
| 紧急逃生 | 第六阶段 | 依赖 DO + 加密 |
| 所有 UI | 第七阶段 | 依赖以上全部 |
| StorageRepository 完整实现 | 第四阶段 | 当前仅实现配对相关部分 |
| QR 码 Bitmap 渲染/扫描 | 第七阶段 | 需要 Android UI |
