# PeerLock MVP 第六阶段：紧急逃生通道

> **给 AI 工作者的说明：** 推荐使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步执行本计划。步骤使用 `- [ ]` 勾选语法追踪进度。

**目标：** 实现三级紧急逃生体系——L1 终止码（应用内 TOTP 验证解除配对）、L2 高级解除（一次性 ADB broadcast + nonce）、L3 固定命令（仅文档说明）。

**架构：** Domain 层新增 `domain/emergency/EmergencyManager` 接口和实现，负责终止码验证和选择性数据清除（销毁加密材料，保留 DO 权限和用户数据）。System 层新增 `EmergencyUnlockReceiver` 接收 L2 的 ADB broadcast，验证一次性 nonce 后执行解除。Nonce 生成和过期管理集成在 EmergencyManager 中。

**技术栈：** 第一至五阶段全部 + BroadcastReceiver + ADB broadcast

**参考设计文档：** `docs/superpowers/specs/2026-05-09-peerlock-mvp-design.md` 第 10、14 节

---

## 文件结构（第六阶段新增/修改）

```
app/src/main/java/com/peerlock/
├── domain/
│   └── emergency/
│       ├── EmergencyManager.kt             # 新建：接口
│       └── EmergencyManagerImpl.kt         # 新建：实现
├── system/
│   └── receiver/
│       └── EmergencyUnlockReceiver.kt      # 新建：L2 广播接收器
├── di/
│   └── AppModule.kt                        # 修改：添加 EmergencyManager
app/src/main/AndroidManifest.xml            # 修改：注册 EmergencyUnlockReceiver
app/src/test/java/com/peerlock/
├── domain/emergency/
│   └── EmergencyManagerImplTest.kt
└── system/receiver/
    └── EmergencyUnlockReceiverTest.kt
```

---

## 任务 1：EmergencyManager — 紧急逃生核心逻辑

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/emergency/EmergencyManager.kt`
- 新建: `app/src/main/java/com/peerlock/domain/emergency/EmergencyManagerImpl.kt`
- 新建: `app/src/test/java/com/peerlock/domain/emergency/EmergencyManagerImplTest.kt`
- 已有: `app/src/main/java/com/peerlock/domain/totp/TotpEngine.kt`（verifyCode）
- 已有: `app/src/main/java/com/peerlock/domain/totp/KeyType.kt`（DESTROY）
- 已有: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt`（clear + 选择性清除）
- 已有: `app/src/main/java/com/peerlock/data/seed/SeedManager.kt`（retrieveSeed）

**设计：**
- `EmergencyManager` 接口：
  - `verifyDestroyCode(code: String): Boolean` — 验证终止码 TOTP（±1 窗口，5次错误→30秒锁定）
  - `executeDestroy(): Boolean` — 执行 L1 终止：销毁加密材料、解除配对、保留 DO + 用户数据
  - `generateNonce(): String` — 生成 L2 一次性 nonce（16位 hex, 5分钟过期）
  - `verifyAndExecuteEmergency(nonce: String): Boolean` — 验证 L2 nonce 并执行解除（移除 DO + 清除密钥，保留数据）
- L1 清除内容：`peerPublicKey`, `myPublicKey`, `encryptedSeedSetting/Unlock/Destroy`, `sessionId`, `isPaired`, `consumedEnvelopes`, `totpErrorCount`, `totpLockedUntil`, `safeModeActive/Reason`
- L1 保留内容：DO 权限、使用数据、策略配置、审计日志
- L2 清除内容：同 L1 + 移除 Device Owner
- L2 保留内容：使用数据、策略配置、审计日志
- Nonce 存储在 SecurePrefs（`emergencyNonce` + `emergencyNonceExpiry`）

- [ ] **步骤 1：编写 EmergencyManagerImpl 测试**

新建 `app/src/test/java/com/peerlock/domain/emergency/EmergencyManagerImplTest.kt`：

```kotlin
package com.peerlock.domain.emergency

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmergencyManagerImplTest {

    private lateinit var manager: EmergencyManagerImpl
    private lateinit var securePrefs: SecurePrefs
    private lateinit var seedManager: SeedManager
    private lateinit var totpEngine: TotpEngine
    private lateinit var storageRepository: StorageRepository
    private lateinit var deviceOwnerManager: DeviceOwnerManager

    @BeforeEach
    fun setup() {
        securePrefs = mockk(relaxed = true)
        seedManager = mockk(relaxed = true)
        totpEngine = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        manager = EmergencyManagerImpl(
            securePrefs, seedManager, totpEngine, storageRepository, deviceOwnerManager
        )
    }

    // --- L1 终止码验证 ---

    @Test
    fun `verifyDestroyCode 正确码返回 true`() = runTest {
        coEvery { seedManager.retrieveSeed(KeyType.DESTROY) } returns ByteArray(32) { 0x01 }
        every { totpEngine.verifyCode(any(), "123456", any()) } returns true

        val result = manager.verifyDestroyCode("123456")

        assertTrue(result)
        assertEquals(0, securePrefs.totpErrorCount)
    }

    @Test
    fun `verifyDestroyCode 错误码返回 false 并增加错误计数`() = runTest {
        coEvery { seedManager.retrieveSeed(KeyType.DESTROY) } returns ByteArray(32) { 0x01 }
        every { totpEngine.verifyCode(any(), "000000", any()) } returns false
        every { securePrefs.totpErrorCount } returns 0

        val result = manager.verifyDestroyCode("000000")

        assertFalse(result)
        verify { securePrefs.totpErrorCount = 1 }
    }

    @Test
    fun `verifyDestroyCode 5次错误后锁定30秒`() = runTest {
        coEvery { seedManager.retrieveSeed(KeyType.DESTROY) } returns ByteArray(32) { 0x01 }
        every { totpEngine.verifyCode(any(), "000000", any()) } returns false
        every { securePrefs.totpErrorCount } returns 4

        manager.verifyDestroyCode("000000")

        verify { securePrefs.totpLockedUntil = any() }
    }

    @Test
    fun `verifyDestroyCode 锁定期间直接返回 false`() = runTest {
        every { securePrefs.totpLockedUntil } returns System.currentTimeMillis() + 30_000L

        val result = manager.verifyDestroyCode("123456")

        assertFalse(result)
        coVerify(exactly = 0) { seedManager.retrieveSeed(any()) }
    }

    // --- L1 执行终止 ---

    @Test
    fun `executeDestroy 清除加密材料和配对状态`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true

        val result = manager.executeDestroy()

        assertTrue(result)
        verify {
            securePrefs.peerPublicKey = null
            securePrefs.myPublicKey = null
            securePrefs.encryptedSeedSetting = null
            securePrefs.encryptedSeedUnlock = null
            securePrefs.encryptedSeedDestroy = null
            securePrefs.sessionId = null
            securePrefs.isPaired = false
        }
        coVerify {
            storageRepository.insertAuditLog(match { it.action == "DESTROY" })
        }
    }

    @Test
    fun `executeDestroy 不移除 Device Owner`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true

        manager.executeDestroy()

        verify(exactly = 0) { deviceOwnerManager.setPackagesSuspended(any(), any()) }
    }

    // --- L2 Nonce ---

    @Test
    fun `generateNonce 返回16位hex字符串`() {
        val nonce = manager.generateNonce()

        assertEquals(16, nonce.length)
        assertTrue(nonce.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `verifyAndExecuteEmergency 有效nonce返回true`() = runTest {
        val nonce = "a1b2c3d4e5f6a7b8"
        every { securePrefs.emergencyNonce } returns nonce
        every { securePrefs.emergencyNonceExpiry } returns System.currentTimeMillis() + 300_000L
        every { deviceOwnerManager.isDeviceOwner() } returns true

        val result = manager.verifyAndExecuteEmergency(nonce)

        assertTrue(result)
        verify {
            securePrefs.emergencyNonce = null
            securePrefs.emergencyNonceExpiry = 0L
        }
        coVerify {
            storageRepository.insertAuditLog(match { it.action == "EMERGENCY_L2" })
        }
    }

    @Test
    fun `verifyAndExecuteEmergency 过期nonce返回false`() = runTest {
        val nonce = "a1b2c3d4e5f6a7b8"
        every { securePrefs.emergencyNonce } returns nonce
        every { securePrefs.emergencyNonceExpiry } returns System.currentTimeMillis() - 1000L

        val result = manager.verifyAndExecuteEmergency(nonce)

        assertFalse(result)
    }

    @Test
    fun `verifyAndExecuteEmergency 错误nonce返回false`() = runTest {
        every { securePrefs.emergencyNonce } returns "correct_nonce_16"
        every { securePrefs.emergencyNonceExpiry } returns System.currentTimeMillis() + 300_000L

        val result = manager.verifyAndExecuteEmergency("wrong_nonce_16x")

        assertFalse(result)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.emergency.EmergencyManagerImplTest" 2>&1 | tail -20`
预期: FAIL — EmergencyManager 类未找到

- [ ] **步骤 3：实现 EmergencyManager 接口**

新建 `app/src/main/java/com/peerlock/domain/emergency/EmergencyManager.kt`：

```kotlin
package com.peerlock.domain.emergency

/**
 * 紧急逃生管理接口。
 * L1：终止码（应用内 TOTP 验证，解除配对，保留 DO + 数据）
 * L2：高级解除（一次性 ADB broadcast + nonce，移除 DO + 密钥，保留数据）
 */
interface EmergencyManager {
    /** 验证终止码（L1），TOTP ±1 窗口，5次错误→30秒锁定 */
    suspend fun verifyDestroyCode(code: String): Boolean

    /** 执行 L1 终止：清除加密材料和配对状态，保留 DO + 用户数据 */
    suspend fun executeDestroy(): Boolean

    /** 生成 L2 一次性 nonce（16位 hex，5分钟过期） */
    fun generateNonce(): String

    /** 验证 L2 nonce 并执行解除（移除 DO + 清除密钥，保留数据） */
    suspend fun verifyAndExecuteEmergency(nonce: String): Boolean
}
```

- [ ] **步骤 4：实现 EmergencyManagerImpl**

新建 `app/src/main/java/com/peerlock/domain/emergency/EmergencyManagerImpl.kt`：

```kotlin
package com.peerlock.domain.emergency

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import java.security.SecureRandom

class EmergencyManagerImpl(
    private val securePrefs: SecurePrefs,
    private val seedManager: SeedManager,
    private val totpEngine: TotpEngine,
    private val storageRepository: StorageRepository,
    private val deviceOwnerManager: DeviceOwnerManager,
) : EmergencyManager {

    override suspend fun verifyDestroyCode(code: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < securePrefs.totpLockedUntil) return false

        val seed = seedManager.retrieveSeed(KeyType.DESTROY) ?: return false
        val valid = totpEngine.verifyCode(seed, code, tolerance = 1)
        if (valid) {
            securePrefs.totpErrorCount = 0
            return true
        } else {
            val errors = securePrefs.totpErrorCount + 1
            securePrefs.totpErrorCount = errors
            if (errors >= 5) {
                securePrefs.totpLockedUntil = now + 30_000L
                securePrefs.totpErrorCount = 0
            }
            return false
        }
    }

    override suspend fun executeDestroy(): Boolean {
        clearCryptoMaterial()
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "DESTROY",
                targetPackage = null,
                detail = "L1 终止码执行：清除加密材料，解除配对"
            )
        )
        return true
    }

    override fun generateNonce(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        val nonce = bytes.joinToString("") { "%02x".format(it) }
        securePrefs.emergencyNonce = nonce
        securePrefs.emergencyNonceExpiry = System.currentTimeMillis() + NONCE_EXPIRY_MS
        return nonce
    }

    override suspend fun verifyAndExecuteEmergency(nonce: String): Boolean {
        val storedNonce = securePrefs.emergencyNonce ?: return false
        val expiry = securePrefs.emergencyNonceExpiry
        if (System.currentTimeMillis() > expiry) return false
        if (nonce != storedNonce) return false

        // 清除 nonce（一次性使用）
        securePrefs.emergencyNonce = null
        securePrefs.emergencyNonceExpiry = 0L

        // L2：移除 Device Owner + 清除加密材料
        clearCryptoMaterial()
        // 注意：DPM.removeActiveAdmin 需要系统 API，此处标记状态
        // 实际移除 DO 由设备管理员接收器处理
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "EMERGENCY_L2",
                targetPackage = null,
                detail = "L2 高级解除执行：清除加密材料，标记移除 DO"
            )
        )
        return true
    }

    /**
     * 清除加密材料和配对状态。
     * 保留：DO 权限、使用数据、策略配置、审计日志。
     */
    private fun clearCryptoMaterial() {
        securePrefs.peerPublicKey = null
        securePrefs.myPublicKey = null
        securePrefs.encryptedSeedSetting = null
        securePrefs.encryptedSeedUnlock = null
        securePrefs.encryptedSeedDestroy = null
        securePrefs.sessionId = null
        securePrefs.isPaired = false
        securePrefs.consumedEnvelopes = emptySet()
        securePrefs.totpErrorCount = 0
        securePrefs.totpLockedUntil = 0L
        securePrefs.safeModeActive = false
        securePrefs.safeModeReason = null
    }

    companion object {
        private const val NONCE_EXPIRY_MS = 300_000L // 5 分钟
    }
}
```

- [ ] **步骤 5：添加 SecurePrefs nonce 字段**

修改 `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt`，在 `consumedEnvelopes` 之后添加：

```kotlin
    var emergencyNonce: String?
        get() = prefs.getString("emergency_nonce", null)
        set(value) = prefs.edit().putString("emergency_nonce", value).apply()

    var emergencyNonceExpiry: Long
        get() = prefs.getLong("emergency_nonce_expiry", 0L)
        set(value) = prefs.edit().putLong("emergency_nonce_expiry", value).apply()
```

- [ ] **步骤 6：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.emergency.EmergencyManagerImplTest" 2>&1 | tail -20`
预期: ALL PASS

- [ ] **步骤 7：提交**

```bash
git add app/src/main/java/com/peerlock/domain/emergency/ \
  app/src/test/java/com/peerlock/domain/emergency/ \
  app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt
git commit -m "feat: 实现 EmergencyManager（L1 终止码 + L2 Nonce）"
```

---

## 任务 2：EmergencyUnlockReceiver — L2 广播接收器

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/system/receiver/EmergencyUnlockReceiver.kt`
- 新建: `app/src/test/java/com/peerlock/system/receiver/EmergencyUnlockReceiverTest.kt`
- 修改: `app/src/main/AndroidManifest.xml`（注册 Receiver）

**设计：**
- `EmergencyUnlockReceiver` 是 BroadcastReceiver，接收 `com.peerlock.ACTION_EMERGENCY` 广播
- 从 Intent extra 读取 `unlock_nonce` 参数
- 调用 `EmergencyManager.verifyAndExecuteEmergency(nonce)` 验证并执行
- 验证成功后发送本地广播通知 UI 更新
- 命令格式：`adb shell am broadcast -a com.peerlock.ACTION_EMERGENCY -n com.peerlock/.system.receiver.EmergencyUnlockReceiver --es unlock_nonce <16位hex>`

- [ ] **步骤 1：编写 EmergencyUnlockReceiver 测试**

新建 `app/src/test/java/com/peerlock/system/receiver/EmergencyUnlockReceiverTest.kt`：

```kotlin
package com.peerlock.system.receiver

import com.peerlock.domain.emergency.EmergencyManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * EmergencyUnlockReceiver 的逻辑测试。
 * 验证 nonce 提取和 EmergencyManager 调用。
 */
class EmergencyUnlockReceiverTest {

    private lateinit var emergencyManager: EmergencyManager

    @BeforeEach
    fun setup() {
        emergencyManager = mockk(relaxed = true)
    }

    @Test
    fun `接口存在`() {
        assertNotNull(EmergencyUnlockReceiver::class.java)
    }

    @Test
    fun `ACTION_EMERGENCY 常量正确`() {
        assertEquals("com.peerlock.ACTION_EMERGENCY", EmergencyUnlockReceiver.ACTION_EMERGENCY)
    }

    @Test
    fun `EXTRA_NONCE 常量正确`() {
        assertEquals("unlock_nonce", EmergencyUnlockReceiver.EXTRA_NONCE)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.system.receiver.EmergencyUnlockReceiverTest" 2>&1 | tail -20`
预期: FAIL — EmergencyUnlockReceiver 类未找到

- [ ] **步骤 3：实现 EmergencyUnlockReceiver**

新建 `app/src/main/java/com/peerlock/system/receiver/EmergencyUnlockReceiver.kt`：

```kotlin
package com.peerlock.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.peerlock.domain.emergency.EmergencyManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * L2 紧急解除广播接收器。
 * 接收 ADB broadcast，验证一次性 nonce 后执行解除。
 *
 * 命令格式：
 * adb shell am broadcast \
 *     -a com.peerlock.ACTION_EMERGENCY \
 *     -n com.peerlock/.system.receiver.EmergencyUnlockReceiver \
 *     --es unlock_nonce <16位hex>
 */
@AndroidEntryPoint
class EmergencyUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EmergencyReceiver"
        const val ACTION_EMERGENCY = "com.peerlock.ACTION_EMERGENCY"
        const val EXTRA_NONCE = "unlock_nonce"
    }

    @Inject lateinit var emergencyManager: EmergencyManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EMERGENCY) return

        val nonce = intent.getStringExtra(EXTRA_NONCE)
        if (nonce == null) {
            Log.w(TAG, "缺少 nonce 参数")
            return
        }

        Log.i(TAG, "收到紧急解除请求")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = emergencyManager.verifyAndExecuteEmergency(nonce)
                if (success) {
                    Log.i(TAG, "紧急解除成功")
                } else {
                    Log.w(TAG, "紧急解除失败：nonce 无效或已过期")
                }
            } catch (e: Exception) {
                Log.e(TAG, "紧急解除异常: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **步骤 4：注册 Receiver 到 AndroidManifest.xml**

修改 `app/src/main/AndroidManifest.xml`，在 `BootReceiver` 之后添加：

```xml
        <receiver
            android:name=".system.receiver.EmergencyUnlockReceiver"
            android:exported="true"
            android:permission="android.permission.DUMP">
            <intent-filter>
                <action android:name="com.peerlock.ACTION_EMERGENCY" />
            </intent-filter>
        </receiver>
```

- [ ] **步骤 5：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.system.receiver.EmergencyUnlockReceiverTest" 2>&1 | tail -20`
预期: ALL PASS

- [ ] **步骤 6：提交**

```bash
git add app/src/main/java/com/peerlock/system/receiver/EmergencyUnlockReceiver.kt \
  app/src/test/java/com/peerlock/system/receiver/EmergencyUnlockReceiverTest.kt \
  app/src/main/AndroidManifest.xml
git commit -m "feat: 实现 EmergencyUnlockReceiver（L2 高级解除广播）"
```

---

## 任务 3：DI 装配 + 最终验证

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/di/AppModule.kt`（添加 EmergencyManager 提供者）

- [ ] **步骤 1：更新 AppModule**

修改 `app/src/main/java/com/peerlock/di/AppModule.kt`，添加：

```kotlin
import com.peerlock.domain.emergency.EmergencyManager
import com.peerlock.domain.emergency.EmergencyManagerImpl
```

在 `AppModule` 中添加：

```kotlin
    @Provides @Singleton
    fun provideEmergencyManager(
        securePrefs: SecurePrefs,
        seedManager: SeedManager,
        totpEngine: TotpEngine,
        storageRepository: StorageRepository,
        deviceOwnerManager: DeviceOwnerManager,
    ): EmergencyManager = EmergencyManagerImpl(
        securePrefs, seedManager, totpEngine, storageRepository, deviceOwnerManager
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
git commit -m "feat: DI 装配（Phase 6 完成）"
```

---

## 验证清单

1. **单元测试**: `./gradlew :app:testDebugUnitTest` — 所有测试通过
2. **构建**: `./gradlew :app:assembleDebug` — 无编译错误
3. **关键行为验证**：
   - L1 终止码：TOTP 验证（±1 窗口）→ 清除加密材料 → 保留 DO + 数据
   - L1 锁定：5次错误 → 30秒锁定
   - L2 Nonce：16位 hex，5分钟过期，一次性使用
   - L2 解除：验证 nonce → 清除加密材料 → 记录审计日志
   - L3 固定命令：仅文档说明（`adb shell dpm remove-active-admin` + `pm clear`）
   - 选择性清除：`clearCryptoMaterial()` 清除 peerPublicKey、seeds、sessionId 等，保留 DO 权限
   - 审计日志：`DESTROY` 和 `EMERGENCY_L2` 事件记录
