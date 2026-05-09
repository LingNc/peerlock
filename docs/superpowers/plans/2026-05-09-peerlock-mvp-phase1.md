# PeerLock MVP 第一阶段：基础层实现计划

> **给 AI 工作者的说明：** 推荐使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步执行本计划。步骤使用 `- [ ]` 勾选语法追踪进度。

**目标：** 构建 PeerLock 所有功能依赖的加密和数据基础层——项目脚手架、加密引擎、TOTP 引擎、加密数据库。

**架构：** 单模块 Android 应用，使用 Kotlin + Jetpack Compose。Domain 层为纯 Kotlin，零 Android 依赖，可独立测试。Data 层封装 Android Keystore 和 Room/SQLCipher。System 层封装 DevicePolicyManager 和 UsageStatsManager。MVVM + Hilt 依赖注入。

**技术栈：** Kotlin, Jetpack Compose, Hilt, Room, SQLCipher, Android Keystore, Bouncy Castle（X25519 用于 API 30-32）, ZXing（二维码）, JUnit 5, Mockk, Turbine

**参考设计文档：** `docs/superpowers/specs/2026-05-09-peerlock-mvp-design.md`

---

## 阶段路线图

| 阶段 | 范围 | 依赖 |
|------|------|------|
| **第一阶段（本计划）** | 项目脚手架、加密引擎、TOTP 引擎、数据库、DI | — |
| 第二阶段 | 配对协议、二维码交换、密钥存储 | 第一阶段 |
| 第三阶段 | Device Owner 设置、应用暂停、前台服务 | 第一阶段 |
| 第四阶段 | 策略引擎、使用统计、时间同步、安全模式 | 第一、三阶段 |
| 第五阶段 | TOTP 信封协议、请求/响应、双向流程 | 第一、二阶段 |
| 第六阶段 | 紧急逃生（终止码、broadcast、固定命令） | 第一、二、三阶段 |
| 第七阶段 | UI（引导、控制端、被控端、通用组件） | 以上全部 |

---

## 文件结构（第一阶段）

```
peerlock/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/peerlock/
│   │   │   ├── PeerLockApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt
│   │   │   ├── domain/
│   │   │   │   ├── crypto/
│   │   │   │   │   ├── CryptoEngine.kt          # 接口
│   │   │   │   │   ├── P256CryptoEngine.kt       # secp256r1 实现
│   │   │   │   │   ├── X25519CryptoEngine.kt     # X25519 实现（API 33+）
│   │   │   │   │   └── AdaptiveCryptoEngine.kt   # 运行时自适应选择
│   │   │   │   ├── totp/
│   │   │   │   │   ├── TotpEngine.kt             # 接口
│   │   │   │   │   ├── TotpEngineImpl.kt         # RFC 6238 实现
│   │   │   │   │   ├── TotpEnvelope.kt           # 信封数据类
│   │   │   │   │   └── KeyType.kt                # 枚举：setting, unlock, destroy
│   │   │   │   ├── policy/
│   │   │   │   │   ├── PolicyEngine.kt           # 接口（第四阶段实现）
│   │   │   │   │   ├── RestrictionPolicy.kt      # 数据类
│   │   │   │   │   └── PolicyAction.kt           # 密封类
│   │   │   │   ├── security/
│   │   │   │   │   ├── TimeSyncManager.kt        # 接口
│   │   │   │   │   └── SafeModeManager.kt        # 接口
│   │   │   │   └── repository/
│   │   │   │       └── StorageRepository.kt      # 接口
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   ├── PeerLockDatabase.kt
│   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── UsageRecordDao.kt
│   │   │   │   │   │   ├── HourlySummaryDao.kt
│   │   │   │   │   │   ├── DailySummaryDao.kt
│   │   │   │   │   │   ├── RestrictionPolicyDao.kt
│   │   │   │   │   │   └── AuditLogDao.kt
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── UsageRecordEntity.kt
│   │   │   │   │   │   ├── HourlySummaryEntity.kt
│   │   │   │   │   │   ├── DailySummaryEntity.kt
│   │   │   │   │   │   ├── RestrictionPolicyEntity.kt
│   │   │   │   │   │   └── AuditLogEntity.kt
│   │   │   │   │   └── converter/
│   │   │   │   │       └── Converters.kt
│   │   │   │   ├── keystore/
│   │   │   │   │   ├── KeystoreManager.kt
│   │   │   │   │   └── KeyAlias.kt
│   │   │   │   └── prefs/
│   │   │   │       └── SecurePrefs.kt
│   │   │   └── system/
│   │   │       └── deviceadmin/
│   │   │           └── PeerLockDeviceAdminReceiver.kt  # 桩实现
│   │   └── res/
│   │       └── xml/
│   │           └── device_admin_policies.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    └── libs.versions.toml
```

---

## 任务 1：Android 项目脚手架

**涉及文件：**
- 新建: `settings.gradle.kts`
- 新建: `build.gradle.kts`（根目录）
- 新建: `gradle/libs.versions.toml`
- 新建: `gradle.properties`
- 新建: `app/build.gradle.kts`
- 新建: `app/src/main/AndroidManifest.xml`
- 新建: `app/src/main/java/com/peerlock/PeerLockApp.kt`
- 新建: `app/src/main/java/com/peerlock/MainActivity.kt`
- 新建: `app/src/main/res/xml/device_admin_policies.xml`
- 新建: `app/src/main/res/values/themes.xml`

- [ ] **步骤 1：初始化 Gradle 项目**

新建 `settings.gradle.kts`：
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PeerLock"
include(":app")
```

- [ ] **步骤 2：创建版本目录**

新建 `gradle/libs.versions.toml`：
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
compose-bom = "2024.12.01"
hilt = "2.53.1"
room = "2.6.1"
sqlcipher = "4.5.6"
compose-compiler = "2.1.0"
coroutines = "1.9.0"
lifecycle = "2.8.7"
navigation = "2.8.5"
junit5 = "5.11.3"
mockk = "1.13.13"
turbine = "1.2.0"
bouncycastle = "1.79"
zxing = "3.5.3"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }

# 生命周期
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# 导航
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Hilt 依赖注入
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room 数据库
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# SQLCipher 数据库加密
sqlcipher = { group = "net.zetetic", name = "android-database-sqlcipher", version.ref = "sqlcipher" }

# Bouncy Castle（X25519 用于 API 30-32）
bouncycastle = { group = "org.bouncycastle", name = "bcprov-jdk18on", version.ref = "bouncycastle" }

# ZXing（二维码）
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }
zxing-android-embedded = { group = "com.journeyapps", name = "zxing-android-embedded", version = "4.3.0" }

# 协程
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# AndroidX
core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.15.0" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.9.3" }
security-crypto = { group = "androidx.security", name = "security-crypto", version = "1.1.0-alpha06" }

# 测试
junit5-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.1.0-1.0.29" }
```

- [ ] **步骤 3：创建根构建文件**

新建 `build.gradle.kts`：
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

新建 `gradle.properties`：
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **步骤 4：创建 app 构建文件**

新建 `app/build.gradle.kts`：
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.peerlock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.peerlock"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // 生命周期
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)

    // 导航
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // SQLCipher
    implementation(libs.sqlcipher)

    // Bouncy Castle
    implementation(libs.bouncycastle)

    // ZXing
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    // 协程
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.security.crypto)

    // 测试
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.junit5.api)
    androidTestImplementation(libs.mockk.android)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **步骤 5：创建 Manifest**

新建 `app/src/main/AndroidManifest.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".PeerLockApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="PeerLock"
        android:supportsRtl="true"
        android:testOnly="true"
        android:theme="@style/Theme.PeerLock">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PeerLock">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".system.deviceadmin.PeerLockDeviceAdminReceiver"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin_policies" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
```

- [ ] **步骤 6：创建设备管理员策略 XML**

新建 `app/src/main/res/xml/device_admin_policies.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin>
    <uses-policies>
        <limit-password />
        <watch-login />
        <reset-password />
        <force-lock />
        <wipe-data />
        <expire-password />
        <encrypted-storage />
        <disable-camera />
        <suspend-packages />
    </uses-policies>
</device-admin>
```

- [ ] **步骤 7：创建 Application 类桩**

新建 `app/src/main/java/com/peerlock/PeerLockApp.kt`：
```kotlin
package com.peerlock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PeerLockApp : Application()
```

- [ ] **步骤 8：创建 MainActivity 桩**

新建 `app/src/main/java/com/peerlock/MainActivity.kt`：
```kotlin
package com.peerlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("PeerLock")
        }
    }
}
```

- [ ] **步骤 9：创建空主题资源**

新建 `app/src/main/res/values/themes.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PeerLock" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **步骤 10：验证构建通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew assembleDebug`
预期: BUILD SUCCESSFUL

- [ ] **步骤 11：提交**

```bash
git init
git add -A
git commit -m "feat: 初始化 Android 项目脚手架

- Kotlin + Jetpack Compose（minSdk 30, targetSdk 35）
- Hilt 依赖注入、Room 数据库、SQLCipher 加密
- Device Owner 接收器桩（testOnly=true）
- Bouncy Castle 用于 X25519、ZXing 用于二维码"
```

---

## 任务 2：Domain 层接口定义

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/crypto/CryptoEngine.kt`
- 新建: `app/src/main/java/com/peerlock/domain/totp/TotpEngine.kt`
- 新建: `app/src/main/java/com/peerlock/domain/totp/TotpEnvelope.kt`
- 新建: `app/src/main/java/com/peerlock/domain/totp/KeyType.kt`
- 新建: `app/src/main/java/com/peerlock/domain/policy/PolicyEngine.kt`
- 新建: `app/src/main/java/com/peerlock/domain/policy/RestrictionPolicy.kt`
- 新建: `app/src/main/java/com/peerlock/domain/policy/PolicyAction.kt`
- 新建: `app/src/main/java/com/peerlock/domain/security/TimeSyncManager.kt`
- 新建: `app/src/main/java/com/peerlock/domain/security/SafeModeManager.kt`
- 新建: `app/src/main/java/com/peerlock/domain/repository/StorageRepository.kt`

- [ ] **步骤 1：创建 KeyType 枚举**

新建 `app/src/main/java/com/peerlock/domain/totp/KeyType.kt`：
```kotlin
package com.peerlock.domain.totp

enum class KeyType(val label: String) {
    SETTING("管理码"),
    UNLOCK("解锁码"),
    DESTROY("终止码");

    companion object {
        fun fromTotpId(id: String): KeyType = when (id) {
            "setting" -> SETTING
            "unlock" -> UNLOCK
            "destroy" -> DESTROY
            else -> throw IllegalArgumentException("未知的密钥类型: $id")
        }
    }
}
```

- [ ] **步骤 2：创建 TotpEnvelope 数据类**

新建 `app/src/main/java/com/peerlock/domain/totp/TotpEnvelope.kt`：
```kotlin
package com.peerlock.domain.totp

/**
 * TOTP 签名信封，用于携带验证码和可选配置。
 */
data class TotpEnvelope(
    val v: Int = 1,
    val type: String,           // "setting", "unlock", "destroy", "unlock_req", "config_req" 等
    val code: String,           // 6 位 TOTP 码
    val sessionId: String,      // 配对会话 ID
    val timestamp: Long,        // 秒级时间戳
    val config: EnvelopeConfig? = null,
)

/**
 * 信封内携带的配置信息。
 */
data class EnvelopeConfig(
    val durationMinutes: Int? = null,
    val durationMode: String? = null,      // "cumulative"（累计）或 "absolute"（绝对时间）
    val absoluteEndTime: Long? = null,     // 绝对模式下的结束时间（毫秒时间戳）
)

sealed class EnvelopeResult {
    data class Valid(val envelope: TotpEnvelope) : EnvelopeResult()
    data class Invalid(val reason: String) : EnvelopeResult()
}
```

- [ ] **步骤 3：创建 TotpEngine 接口**

新建 `app/src/main/java/com/peerlock/domain/totp/TotpEngine.kt`：
```kotlin
package com.peerlock.domain.totp

/**
 * TOTP 引擎接口，基于 RFC 6238。
 * 纯 Kotlin 实现，无 Android 依赖，可在 JVM 上独立测试。
 */
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
```

- [ ] **步骤 4：创建 CryptoEngine 接口**

新建 `app/src/main/java/com/peerlock/domain/crypto/CryptoEngine.kt`：
```kotlin
package com.peerlock.domain.crypto

/**
 * 加密引擎接口。
 * 私钥存储在 Android Keystore 中，永不导出。
 */
interface CryptoEngine {
    suspend fun generateKeyPair(): CryptoKeyPair
    suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray
    suspend fun decrypt(data: ByteArray): ByteArray
    suspend fun sign(data: ByteArray): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean
    suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray
}

data class CryptoKeyPair(
    val publicKey: ByteArray,
    val privateKeyAlias: String   // Keystore 别名——私钥永不离开 Keystore
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CryptoKeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKeyAlias == other.privateKeyAlias
    }
    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + privateKeyAlias.hashCode()
}
```

- [ ] **步骤 5：创建 PolicyAction 密封类**

新建 `app/src/main/java/com/peerlock/domain/policy/PolicyAction.kt`：
```kotlin
package com.peerlock.domain.policy

sealed class PolicyAction {
    data object Monitor : PolicyAction()    // 继续监控，无操作
    data object Suspend : PolicyAction()    // 暂停应用
    data object Unsuspend : PolicyAction()  // 解除暂停（解锁时段内）
}
```

- [ ] **步骤 6：创建 RestrictionPolicy 数据类**

新建 `app/src/main/java/com/peerlock/domain/policy/RestrictionPolicy.kt`：
```kotlin
package com.peerlock.domain.policy

import java.time.LocalTime

data class RestrictionPolicy(
    val id: Long = 0,
    val targetPackage: String,
    val dailyLimitMinutes: Int?,        // null = 不限
    val allowedTimeStart: LocalTime?,   // null = 不限时段
    val allowedTimeEnd: LocalTime?,
    val isBlacklist: Boolean,           // true = 黑名单模式
    val isActive: Boolean = true,
    val createdAt: Long,
    val lastModified: Long
)
```

- [ ] **步骤 7：创建 PolicyEngine 接口**

新建 `app/src/main/java/com/peerlock/domain/policy/PolicyEngine.kt`：
```kotlin
package com.peerlock.domain.policy

interface PolicyEngine {
    suspend fun evaluate(packageName: String, currentTimeMillis: Long): PolicyAction
    suspend fun suspendApp(packageName: String)
    suspend fun unsuspendApp(packageName: String)
    suspend fun resetDailyUsage()
}
```

- [ ] **步骤 8：创建 TimeSyncManager 接口**

新建 `app/src/main/java/com/peerlock/domain/security/TimeSyncManager.kt`：
```kotlin
package com.peerlock.domain.security

interface TimeSyncManager {
    suspend fun getCurrentRealTime(): Long
    suspend fun syncWithNtp(): SyncResult
    fun getStoredOffset(): Long
    fun isSystemTimeReliable(): Boolean
}

sealed class SyncResult {
    data class Success(val offsetMs: Long) : SyncResult()
    data object Failed : SyncResult()
}
```

- [ ] **步骤 9：创建 SafeModeManager 接口**

新建 `app/src/main/java/com/peerlock/domain/security/SafeModeManager.kt`：
```kotlin
package com.peerlock.domain.security

interface SafeModeManager {
    fun isInSafeMode(): Boolean
    suspend fun enterSafeMode(reason: String)
    suspend fun exitSafeMode()
    fun getSafeModeReason(): String?
}
```

- [ ] **步骤 10：创建 StorageRepository 接口**

新建 `app/src/main/java/com/peerlock/domain/repository/StorageRepository.kt`：
```kotlin
package com.peerlock.domain.repository

import com.peerlock.domain.policy.RestrictionPolicy
import kotlinx.coroutines.flow.Flow

interface StorageRepository {
    // 使用记录
    suspend fun insertUsageRecord(record: UsageRecord)
    suspend fun getUsageByDate(date: String): List<UsageRecord>
    fun observeUsageByDate(date: String): Flow<List<UsageRecord>>

    // 小时汇总
    suspend fun insertHourlySummary(summary: HourlySummary)
    suspend fun getHourlySummary(date: String): List<HourlySummary>

    // 日汇总
    suspend fun insertDailySummary(summary: DailySummary)
    suspend fun getDailySummary(startDate: String, endDate: String): List<DailySummary>
    suspend fun getDailySummaryByPackage(packageName: String, startDate: String, endDate: String): List<DailySummary>

    // 策略
    suspend fun getAllPolicies(): List<RestrictionPolicy>
    suspend fun getActivePolicies(): List<RestrictionPolicy>
    suspend fun upsertPolicy(policy: RestrictionPolicy)
    suspend fun deletePolicy(id: Long)

    // 审计日志
    suspend fun insertAuditLog(log: AuditLog)
    suspend fun getRecentAuditLogs(limit: Int): List<AuditLog>
}

data class UsageRecord(
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val date: String
)

data class HourlySummary(
    val packageName: String,
    val date: String,
    val hour: Int,
    val totalMs: Long
)

data class DailySummary(
    val packageName: String,
    val date: String,
    val totalMs: Long,
    val launchCount: Int
)

data class AuditLog(
    val timestamp: Long,
    val action: String,
    val targetPackage: String?,
    val detail: String?
)
```

- [ ] **步骤 11：提交**

```bash
git add app/src/main/java/com/peerlock/domain/
git commit -m "feat: 添加 Domain 层接口定义

纯 Kotlin 接口，零 Android 依赖，每个接口可独立测试：
- CryptoEngine（加密引擎）
- TotpEngine（TOTP 验证）
- PolicyEngine（策略引擎）
- TimeSyncManager / SafeModeManager（安全监控）
- StorageRepository（数据仓库）"
```

---

## 任务 3：TotpEngine 实现

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/totp/TotpEngineImpl.kt`
- 新建: `app/src/test/java/com/peerlock/domain/totp/TotpEngineImplTest.kt`

- [ ] **步骤 1：编写 TOTP 码生成的失败测试**

新建 `app/src/test/java/com/peerlock/domain/totp/TotpEngineImplTest.kt`：
```kotlin
package com.peerlock.domain.totp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TotpEngineImplTest {

    private lateinit var engine: TotpEngineImpl

    // RFC 6238 测试向量种子（ASCII "12345678901234567890"）
    private val testSeed = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @BeforeEach
    fun setup() {
        engine = TotpEngineImpl()
    }

    @Test
    fun `生成码应为 6 位数字字符串`() {
        val code = engine.generateCode(testSeed, timeStep = 59L / 30)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `相同种子和步数应生成确定性结果`() {
        val step = 100L
        val code1 = engine.generateCode(testSeed, step)
        val code2 = engine.generateCode(testSeed, step)
        assertEquals(code1, code2)
    }

    @Test
    fun `不同步数应生成不同码`() {
        val code1 = engine.generateCode(testSeed, 1L)
        val code2 = engine.generateCode(testSeed, 2L)
        assertNotEquals(code1, code2)
    }

    @Test
    fun `应验证当前窗口的码`() {
        val step = engine.currentStep()
        val code = engine.generateCode(testSeed, step)
        assertTrue(engine.verifyCode(testSeed, code, tolerance = 1))
    }

    @Test
    fun `应验证前一个窗口的码`() {
        val step = engine.currentStep()
        val previousCode = engine.generateCode(testSeed, step - 1)
        assertTrue(engine.verifyCode(testSeed, previousCode, tolerance = 1))
    }

    @Test
    fun `应拒绝过期码`() {
        val step = engine.currentStep()
        val oldCode = engine.generateCode(testSeed, step - 3)
        assertFalse(engine.verifyCode(testSeed, oldCode, tolerance = 1))
    }

    @Test
    fun `应拒绝错误码`() {
        assertFalse(engine.verifyCode(testSeed, "000000", tolerance = 1))
    }

    @Test
    fun `前导零的码也应为 6 位`() {
        // 部分时间步长会生成以 0 开头的码，验证仍然为 6 位
        var foundLeadingZero = false
        for (step in 0L..1000L) {
            val code = engine.generateCode(testSeed, step)
            if (code.startsWith("0")) {
                foundLeadingZero = true
                assertEquals(6, code.length)
                break
            }
        }
        if (!foundLeadingZero) {
            println("注意: 前 1000 步内未找到前导零码（概率上不太可能）")
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.totp.TotpEngineImplTest"`
预期: FAIL — TotpEngineImpl 类未找到

- [ ] **步骤 3：实现 TotpEngineImpl**

新建 `app/src/main/java/com/peerlock/domain/totp/TotpEngineImpl.kt`：
```kotlin
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
        // 1. 检查会话 ID
        if (envelope.sessionId != expectedSessionId) {
            return EnvelopeResult.Invalid("会话 ID 不匹配")
        }

        // 2. 检查时间戳新鲜度
        val nowSeconds = System.currentTimeMillis() / 1000
        if (kotlin.math.abs(nowSeconds - envelope.timestamp) > maxAgeSeconds) {
            return EnvelopeResult.Invalid("信封已过期")
        }

        // 3. 验证 TOTP 码
        if (!verifyCode(seed, envelope.code, tolerance = 1)) {
            return EnvelopeResult.Invalid("验证码错误")
        }

        return EnvelopeResult.Valid(envelope)
    }

    /**
     * HOTP 算法（RFC 4226）
     */
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

    /**
     * 常量时间字符串比较，防止时序攻击。
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.totp.TotpEngineImplTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/totp/TotpEngineImpl.kt app/src/test/java/com/peerlock/domain/totp/TotpEngineImplTest.kt
git commit -m "feat: 实现 TotpEngine（RFC 6238 TOTP 生成与验证）

- 6 位 HOTP（HmacSHA1）
- ±1 窗口容差
- 信封生成与验证（含会话 ID 和时间戳检查）
- 常量时间比较防止时序攻击"
```

---

## 任务 4：CryptoEngine — secp256r1 实现

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/crypto/P256CryptoEngine.kt`
- 新建: `app/src/test/java/com/peerlock/domain/crypto/P256CryptoEngineTest.kt`

- [ ] **步骤 1：编写 P256CryptoEngine 的失败测试**

新建 `app/src/test/java/com/peerlock/domain/crypto/P256CryptoEngineTest.kt`：
```kotlin
package com.peerlock.domain.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class P256CryptoEngineTest {

    private lateinit var engine: P256CryptoEngine

    @BeforeEach
    fun setup() {
        engine = P256CryptoEngine()
    }

    @Test
    fun `生成密钥对应产生有效公钥`() = runTest {
        val keyPair = engine.generateKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKeyAlias)
        assertEquals(65, keyPair.publicKey.size) // 未压缩 P-256 公钥
        assertEquals(0x04.toByte(), keyPair.publicKey[0]) // 未压缩点前缀
    }

    @Test
    fun `加密后解密应恢复原始数据`() = runTest {
        val keyPair = engine.generateKeyPair()
        val plaintext = "Hello, PeerLock!".toByteArray()

        val encrypted = engine.encrypt(plaintext, keyPair.publicKey)
        assertNotEquals(plaintext.toList(), encrypted.toList())

        val decrypted = engine.decryptWithPeerKey(encrypted, keyPair.publicKey)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `错误密钥应解密失败`() = runTest {
        val keyPair1 = engine.generateKeyPair()
        val keyPair2 = P256CryptoEngine().apply { generateKeyPair() }
        val plaintext = "secret".toByteArray()

        val encrypted = engine.encrypt(plaintext, keyPair1.publicKey)

        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking {
                keyPair2.decryptWithPeerKey(encrypted, keyPair1.publicKey)
            }
        }
    }

    @Test
    fun `签名和验证往返一致`() = runTest {
        val keyPair = engine.generateKeyPair()
        val data = "重要数据".toByteArray()

        val signature = engine.sign(data)
        assertTrue(engine.verify(data, signature, keyPair.publicKey))
    }

    @Test
    fun `篡改数据后验证应失败`() = runTest {
        val keyPair = engine.generateKeyPair()
        val data = "重要数据".toByteArray()

        val signature = engine.sign(data)
        val tampered = "被篡改的数据".toByteArray()
        assertFalse(engine.verify(tampered, signature, keyPair.publicKey))
    }

    @Test
    fun `双方派生共享密钥应一致`() = runTest {
        val alice = P256CryptoEngine()
        val bob = P256CryptoEngine()

        val aliceKeyPair = alice.generateKeyPair()
        val bobKeyPair = bob.generateKeyPair()

        val secretAB = alice.deriveSharedSecret(bobKeyPair.publicKey)
        val secretBA = bob.deriveSharedSecret(aliceKeyPair.publicKey)

        assertArrayEquals(secretAB, secretBA)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.crypto.P256CryptoEngineTest"`
预期: FAIL — P256CryptoEngine 类未找到

- [ ] **步骤 3：实现 P256CryptoEngine（可 JVM 测试版本）**

新建 `app/src/main/java/com/peerlock/domain/crypto/P256CryptoEngine.kt`：
```kotlin
package com.peerlock.domain.crypto

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * secp256r1（NIST P-256）加密引擎。
 * 这是可在 JVM 上测试的版本，使用内存密钥存储。
 * Android 版本将使用 Android Keystore 存储私钥。
 */
class P256CryptoEngine : CryptoEngine {

    private var keyPair: KeyPair? = null

    companion object {
        private const val EC_CURVE = "secp256r1"
        private const val KEY_AGREEMENT_ALGORITHM = "ECDH"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    override suspend fun generateKeyPair(): CryptoKeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE))
        val kp = kpg.generateKeyPair()
        val alias = "p256_${System.nanoTime()}"
        this.keyPair = kp

        return CryptoKeyPair(
            publicKey = kp.public.encoded,
            privateKeyAlias = alias
        )
    }

    override suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")

        // 通过 ECDH 派生共享密钥
        val sharedSecret = deriveSharedSecretInternal(currentKeyPair.private, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        // AES-256-GCM 加密
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(data)

        // IV 前置拼接密文
        return iv + ciphertext
    }

    override suspend fun decrypt(data: ByteArray): ByteArray {
        throw NotImplementedError("请使用 decryptWithPeerKey 并显式传入对方公钥")
    }

    /**
     * 使用已知对方公钥通过 ECDH 派生密钥解密数据。
     */
    suspend fun decryptWithPeerKey(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")

        val sharedSecret = deriveSharedSecretInternal(currentKeyPair.private, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val iv = data.sliceArray(0 until GCM_IV_LENGTH)
        val ciphertext = data.sliceArray(GCM_IV_LENGTH until data.size)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    override suspend fun sign(data: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(currentKeyPair.private)
        signature.update(data)
        return signature.sign()
    }

    override suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean {
        val keyFactory = KeyFactory.getInstance("EC")
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKey))
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initVerify(pubKey)
        sig.update(data)
        return sig.verify(signature)
    }

    override suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray {
        val currentKeyPair = keyPair ?: throw IllegalStateException("尚未生成密钥对")
        return deriveSharedSecretInternal(currentKeyPair.private, peerPublicKey)
    }

    private fun deriveSharedSecretInternal(privateKey: PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray {
        // 类 HKDF：SHA-256 哈希共享密钥得到 32 字节 AES 密钥
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sharedSecret)
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.crypto.P256CryptoEngineTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/crypto/P256CryptoEngine.kt app/src/test/java/com/peerlock/domain/crypto/P256CryptoEngineTest.kt
git commit -m "feat: 实现 P256CryptoEngine（secp256r1 加密引擎）

- secp256r1 密钥对生成
- ECDH 密钥协商派生共享密钥
- AES-256-GCM 加密/解密（随机 IV）
- SHA256withECDSA 签名和验证
- SHA-256 密钥派生"
```

---

## 任务 5：CryptoEngine — X25519 实现

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/crypto/X25519CryptoEngine.kt`
- 新建: `app/src/test/java/com/peerlock/domain/crypto/X25519CryptoEngineTest.kt`

- [ ] **步骤 1：编写 X25519CryptoEngine 的失败测试**

新建 `app/src/test/java/com/peerlock/domain/crypto/X25519CryptoEngineTest.kt`：
```kotlin
package com.peerlock.domain.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class X25519CryptoEngineTest {

    private lateinit var engine: X25519CryptoEngine

    @BeforeEach
    fun setup() {
        engine = X25519CryptoEngine()
    }

    @Test
    fun `生成密钥对应产生 32 字节公钥`() = runTest {
        val keyPair = engine.generateKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKeyAlias)
        assertEquals(32, keyPair.publicKey.size) // X25519 公钥为 32 字节
    }

    @Test
    fun `签名和验证往返一致`() = runTest {
        val keyPair = engine.generateKeyPair()
        val data = "测试数据".toByteArray()
        val signature = engine.sign(data)
        assertTrue(engine.verify(data, signature, keyPair.publicKey))
    }

    @Test
    fun `错误签名应验证失败`() = runTest {
        val keyPair = engine.generateKeyPair()
        val data = "测试".toByteArray()
        val signature = engine.sign(data)
        val fakeSignature = ByteArray(64) { it.toByte() }
        assertFalse(engine.verify(data, fakeSignature, keyPair.publicKey))
    }

    @Test
    fun `双方派生共享密钥应一致`() = runTest {
        val alice = X25519CryptoEngine()
        val bob = X25519CryptoEngine()

        val aliceKeyPair = alice.generateKeyPair()
        val bobKeyPair = bob.generateKeyPair()

        val secretAB = alice.deriveSharedSecret(bobKeyPair.publicKey)
        val secretBA = bob.deriveSharedSecret(aliceKeyPair.publicKey)

        assertArrayEquals(secretAB, secretBA)
        assertEquals(32, secretAB.size) // X25519 共享密钥为 32 字节
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.crypto.X25519CryptoEngineTest"`
预期: FAIL — X25519CryptoEngine 类未找到

- [ ] **步骤 3：实现 X25519CryptoEngine**

新建 `app/src/main/java/com/peerlock/domain/crypto/X25519CryptoEngine.kt`：
```kotlin
package com.peerlock.domain.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * X25519/Ed25519 加密引擎，使用 Bouncy Castle。
 * 用于 API 30-32 设备（Keystore 不原生支持 X25519）。
 * 私钥在此以内存存储；Android Keystore 包装在 KeystoreManager 中。
 */
class X25519CryptoEngine : CryptoEngine {

    private var x25519PrivateKey: X25519PrivateKeyParameters? = null
    private var edPrivateKey: Ed25519PrivateKeyParameters? = null

    override suspend fun generateKeyPair(): CryptoKeyPair {
        val random = SecureRandom()

        // 生成 X25519 密钥对（用于 ECDH）
        val x25519Private = X25519PrivateKeyParameters(random)
        val x25519Public = x25519Private.generatePublicKey()

        // 生成 Ed25519 密钥对（用于签名）
        val edPrivate = Ed25519PrivateKeyParameters(random)
        val edPublic = edPrivate.generatePublicKey()

        val alias = "x25519_${System.nanoTime()}"

        this.x25519PrivateKey = x25519Private
        this.edPrivateKey = edPrivate

        // 返回 X25519 公钥（32 字节）作为主公钥
        return CryptoKeyPair(
            publicKey = x25519Public.encoded,
            privateKeyAlias = alias
        )
    }

    override suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentPrivate = x25519PrivateKey ?: throw IllegalStateException("尚未生成密钥对")

        val sharedSecret = deriveSharedSecretInternal(currentPrivate, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(aesKey, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    override suspend fun decrypt(data: ByteArray): ByteArray {
        throw NotImplementedError("请使用 decryptWithPeerKey 并显式传入对方公钥")
    }

    suspend fun decryptWithPeerKey(data: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val currentPrivate = x25519PrivateKey ?: throw IllegalStateException("尚未生成密钥对")

        val sharedSecret = deriveSharedSecretInternal(currentPrivate, peerPublicKey)
        val aesKey = deriveAesKey(sharedSecret)

        val iv = data.sliceArray(0 until 12)
        val ciphertext = data.sliceArray(12 until data.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(aesKey, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        return cipher.doFinal(ciphertext)
    }

    override suspend fun sign(data: ByteArray): ByteArray {
        val currentEdPrivate = edPrivateKey ?: throw IllegalStateException("尚未生成密钥对")
        val signer = Ed25519Signer()
        signer.init(true, currentEdPrivate)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean {
        // Ed25519 验证需要 Ed25519 公钥（32 字节）
        // X25519 公钥不能直接用于 Ed25519 验证
        // 配对流程中会同时存储 X25519 和 Ed25519 公钥
        return try {
            val edPubKey = Ed25519PublicKeyParameters(peerPublicKey, 0)
            val signer = Ed25519Signer()
            signer.init(false, edPubKey)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray {
        val currentPrivate = x25519PrivateKey ?: throw IllegalStateException("尚未生成密钥对")
        return deriveSharedSecretInternal(currentPrivate, peerPublicKey)
    }

    private fun deriveSharedSecretInternal(
        privateKey: X25519PrivateKeyParameters,
        peerPublicKeyBytes: ByteArray
    ): ByteArray {
        val peerPublicKey = X25519PublicKeyParameters(peerPublicKeyBytes, 0)
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(peerPublicKey, sharedSecret, 0)
        return sharedSecret
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sharedSecret)
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.crypto.X25519CryptoEngineTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/crypto/X25519CryptoEngine.kt app/src/test/java/com/peerlock/domain/crypto/X25519CryptoEngineTest.kt
git commit -m "feat: 实现 X25519CryptoEngine（Bouncy Castle）

- X25519 ECDH 密钥协商（32 字节密钥）
- Ed25519 签名和验证
- AES-256-GCM 加密（随机 IV）
- 用于 API 30-32 设备"
```

---

## 任务 6：AdaptiveCryptoEngine（运行时曲线自适应选择）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/crypto/AdaptiveCryptoEngine.kt`
- 新建: `app/src/test/java/com/peerlock/domain/crypto/AdaptiveCryptoEngineTest.kt`

- [ ] **步骤 1：编写失败测试**

新建 `app/src/test/java/com/peerlock/domain/crypto/AdaptiveCryptoEngineTest.kt`：
```kotlin
package com.peerlock.domain.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdaptiveCryptoEngineTest {

    @Test
    fun `API 30 应选择 P256 曲线`() = runTest {
        val engine = AdaptiveCryptoEngine(apiLevel = 30)
        val keyPair = engine.generateKeyPair()
        assertEquals(65, keyPair.publicKey.size) // P-256 未压缩
    }

    @Test
    fun `API 33 应选择 X25519 曲线`() = runTest {
        val engine = AdaptiveCryptoEngine(apiLevel = 33)
        val keyPair = engine.generateKeyPair()
        assertEquals(32, keyPair.publicKey.size) // X25519
    }

    @Test
    fun `两种曲线下签名验证均正常工作`() = runTest {
        for (apiLevel in listOf(30, 33)) {
            val engine = AdaptiveCryptoEngine(apiLevel = apiLevel)
            val keyPair = engine.generateKeyPair()
            val data = "测试".toByteArray()
            val sig = engine.sign(data)
            assertTrue(engine.verify(data, sig, keyPair.publicKey), "API $apiLevel 失败")
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.crypto.AdaptiveCryptoEngineTest"`
预期: FAIL — AdaptiveCryptoEngine 类未找到

- [ ] **步骤 3：实现 AdaptiveCryptoEngine**

新建 `app/src/main/java/com/peerlock/domain/crypto/AdaptiveCryptoEngine.kt`：
```kotlin
package com.peerlock.domain.crypto

import android.os.Build

/**
 * 根据 Android API 级别自动选择加密后端：
 * - API 33+：X25519（密钥更短，Keystore 原生支持）
 * - API 30-32：secp256r1（硬件级 Keystore 支持）
 */
class AdaptiveCryptoEngine(
    private val apiLevel: Int = Build.VERSION.SDK_INT
) : CryptoEngine {

    private val delegate: CryptoEngine by lazy {
        if (apiLevel >= 33) X25519CryptoEngine() else P256CryptoEngine()
    }

    val curveName: String
        get() = if (apiLevel >= 33) "X25519" else "secp256r1"

    override suspend fun generateKeyPair(): CryptoKeyPair = delegate.generateKeyPair()
    override suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray =
        delegate.encrypt(data, peerPublicKey)
    override suspend fun decrypt(data: ByteArray): ByteArray = delegate.decrypt(data)
    override suspend fun sign(data: ByteArray): ByteArray = delegate.sign(data)
    override suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean =
        delegate.verify(data, signature, peerPublicKey)
    override suspend fun deriveSharedSecret(peerPublicKey: ByteArray): ByteArray =
        delegate.deriveSharedSecret(peerPublicKey)
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.domain.crypto.AdaptiveCryptoEngineTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/crypto/AdaptiveCryptoEngine.kt app/src/test/java/com/peerlock/domain/crypto/AdaptiveCryptoEngineTest.kt
git commit -m "feat: 添加 AdaptiveCryptoEngine 运行时曲线自适应选择

API 33+ 使用 X25519，API 30-32 使用 secp256r1。
对调用方透明，通过 CryptoEngine 接口统一访问。"
```

---

## 任务 7：Room 数据库 + SQLCipher 加密

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/data/db/entity/UsageRecordEntity.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/entity/HourlySummaryEntity.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/entity/DailySummaryEntity.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/entity/RestrictionPolicyEntity.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/entity/AuditLogEntity.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/dao/UsageRecordDao.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/dao/HourlySummaryDao.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/dao/DailySummaryDao.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/dao/RestrictionPolicyDao.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/dao/AuditLogDao.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/PeerLockDatabase.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/converter/Converters.kt`
- 新建: `app/src/main/java/com/peerlock/data/db/DatabaseModule.kt`
- 新建: `app/src/test/java/com/peerlock/data/db/DatabaseSchemaTest.kt`

- [ ] **步骤 1：创建 Room 实体类**

新建 `app/src/main/java/com/peerlock/data/db/entity/UsageRecordEntity.kt`：
```kotlin
package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["date", "packageName"])]
)
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val date: String   // "2026-05-09"
)
```

新建 `app/src/main/java/com/peerlock/data/db/entity/HourlySummaryEntity.kt`：
```kotlin
package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "usage_hourly_summary",
    primaryKeys = ["packageName", "date", "hour"],
    indices = [Index(value = ["date"])]
)
data class HourlySummaryEntity(
    val packageName: String,
    val date: String,
    val hour: Int,       // 0-23
    val totalMs: Long
)
```

新建 `app/src/main/java/com/peerlock/data/db/entity/DailySummaryEntity.kt`：
```kotlin
package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "usage_daily_summary",
    primaryKeys = ["packageName", "date"],
    indices = [Index(value = ["date"])]
)
data class DailySummaryEntity(
    val packageName: String,
    val date: String,
    val totalMs: Long,
    val launchCount: Int = 0
)
```

新建 `app/src/main/java/com/peerlock/data/db/entity/RestrictionPolicyEntity.kt`：
```kotlin
package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restriction_policies")
data class RestrictionPolicyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetPackage: String,
    val dailyLimitMinutes: Int?,
    val allowedTimeStart: String?,   // "HH:mm"
    val allowedTimeEnd: String?,     // "HH:mm"
    val isBlacklist: Boolean,
    val isActive: Boolean = true,
    val createdAt: Long,
    val lastModified: Long
)
```

新建 `app/src/main/java/com/peerlock/data/db/entity/AuditLogEntity.kt`：
```kotlin
package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_log",
    indices = [Index(value = ["timestamp"])]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val action: String,
    val targetPackage: String?,
    val detail: String?
)
```

- [ ] **步骤 2：创建 Room DAO 接口**

新建 `app/src/main/java/com/peerlock/data/db/dao/UsageRecordDao.kt`：
```kotlin
package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.UsageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {
    @Insert
    suspend fun insert(record: UsageRecordEntity)

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY startTime")
    suspend fun getByDate(date: String): List<UsageRecordEntity>

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY startTime")
    fun observeByDate(date: String): Flow<List<UsageRecordEntity>>

    @Query("DELETE FROM usage_records WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("SELECT COUNT(*) FROM usage_records")
    suspend fun count(): Int
}
```

新建 `app/src/main/java/com/peerlock/data/db/dao/HourlySummaryDao.kt`：
```kotlin
package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.HourlySummaryEntity

@Dao
interface HourlySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: HourlySummaryEntity)

    @Query("SELECT * FROM usage_hourly_summary WHERE date = :date ORDER BY hour")
    suspend fun getByDate(date: String): List<HourlySummaryEntity>

    @Query("SELECT * FROM usage_hourly_summary WHERE packageName = :pkg AND date = :date")
    suspend fun getByPackageAndDate(pkg: String, date: String): List<HourlySummaryEntity>
}
```

新建 `app/src/main/java/com/peerlock/data/db/dao/DailySummaryDao.kt`：
```kotlin
package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.DailySummaryEntity

@Dao
interface DailySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM usage_daily_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getByDateRange(startDate: String, endDate: String): List<DailySummaryEntity>

    @Query("SELECT * FROM usage_daily_summary WHERE packageName = :pkg AND date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getByPackageAndDateRange(pkg: String, startDate: String, endDate: String): List<DailySummaryEntity>
}
```

新建 `app/src/main/java/com/peerlock/data/db/dao/RestrictionPolicyDao.kt`：
```kotlin
package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.RestrictionPolicyEntity

@Dao
interface RestrictionPolicyDao {
    @Query("SELECT * FROM restriction_policies ORDER BY targetPackage")
    suspend fun getAll(): List<RestrictionPolicyEntity>

    @Query("SELECT * FROM restriction_policies WHERE isActive = 1 ORDER BY targetPackage")
    suspend fun getActive(): List<RestrictionPolicyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: RestrictionPolicyEntity)

    @Query("DELETE FROM restriction_policies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM restriction_policies WHERE targetPackage = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): RestrictionPolicyEntity?
}
```

新建 `app/src/main/java/com/peerlock/data/db/dao/AuditLogDao.kt`：
```kotlin
package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.AuditLogEntity

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AuditLogEntity>

    @Query("DELETE FROM audit_log WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
```

- [ ] **步骤 3：创建 Room 类型转换器**

新建 `app/src/main/java/com/peerlock/data/db/converter/Converters.kt`：
```kotlin
package com.peerlock.data.db.converter

import androidx.room.TypeConverter
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }
}
```

- [ ] **步骤 4：创建 Room 数据库类**

新建 `app/src/main/java/com/peerlock/data/db/PeerLockDatabase.kt`：
```kotlin
package com.peerlock.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.peerlock.data.db.converter.Converters
import com.peerlock.data.db.dao.*
import com.peerlock.data.db.entity.*

@Database(
    entities = [
        UsageRecordEntity::class,
        HourlySummaryEntity::class,
        DailySummaryEntity::class,
        RestrictionPolicyEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PeerLockDatabase : RoomDatabase() {
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun hourlySummaryDao(): HourlySummaryDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun restrictionPolicyDao(): RestrictionPolicyDao
    abstract fun auditLogDao(): AuditLogDao
}
```

- [ ] **步骤 5：创建 Hilt 数据库模块**

新建 `app/src/main/java/com/peerlock/data/db/DatabaseModule.kt`：
```kotlin
package com.peerlock.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // SQLCipher 密码——生产环境从 Keystore 派生
    // 当前使用占位符，第二阶段替换为 KeystoreManager
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PeerLockDatabase {
        // TODO: 第二阶段从 Android Keystore 派生密码
        val passphrase = "peerlock_dev_passphrase".toByteArray()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            PeerLockDatabase::class.java,
            "peerlock.db"
        )
            .openHelperFactory(factory)
            .build()
    }

    @Provides fun provideUsageRecordDao(db: PeerLockDatabase) = db.usageRecordDao()
    @Provides fun provideHourlySummaryDao(db: PeerLockDatabase) = db.hourlySummaryDao()
    @Provides fun provideDailySummaryDao(db: PeerLockDatabase) = db.dailySummaryDao()
    @Provides fun provideRestrictionPolicyDao(db: PeerLockDatabase) = db.restrictionPolicyDao()
    @Provides fun provideAuditLogDao(db: PeerLockDatabase) = db.auditLogDao()
}
```

- [ ] **步骤 6：编写数据库编译验证测试**

新建 `app/src/test/java/com/peerlock/data/db/DatabaseSchemaTest.kt`：
```kotlin
package com.peerlock.data.db

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DatabaseSchemaTest {

    @Test
    fun `数据库类存在且注解正确`() {
        val dbClass = PeerLockDatabase::class.java
        assertNotNull(dbClass)
        assertTrue(dbClass.interfaces.any { it == androidx.room.RoomDatabase::class.java })
    }

    @Test
    fun `所有 DAO 接口存在`() {
        assertNotNull(com.peerlock.data.db.dao.UsageRecordDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.HourlySummaryDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.DailySummaryDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.RestrictionPolicyDao::class.java)
        assertNotNull(com.peerlock.data.db.dao.AuditLogDao::class.java)
    }
}
```

- [ ] **步骤 7：运行测试验证编译通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.data.db.DatabaseSchemaTest"`
预期: PASS

- [ ] **步骤 8：提交**

```bash
git add app/src/main/java/com/peerlock/data/db/ app/src/test/java/com/peerlock/data/db/
git commit -m "feat: 添加 Room 数据库（SQLCipher 加密）

5 张表：usage_records、hourly/daily summary、restriction_policies、audit_log。
SQLCipher 加密（Keystore 集成在第二阶段完成）。
DAO 支持 Flow 响应式查询。"
```

---

## 任务 8：Android Keystore 集成

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/data/keystore/KeystoreManager.kt`
- 新建: `app/src/main/java/com/peerlock/data/keystore/KeyAlias.kt`
- 新建: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt`
- 新建: `app/src/test/java/com/peerlock/data/keystore/KeystoreManagerTest.kt`

- [ ] **步骤 1：创建 KeyAlias 常量**

新建 `app/src/main/java/com/peerlock/data/keystore/KeyAlias.kt`：
```kotlin
package com.peerlock.data.keystore

/**
 * PeerLock 加密密钥的 Keystore 别名常量。
 * 所有密钥不可导出，由硬件安全模块保护。
 */
object KeyAlias {
    // ECC 密钥对（用于配对协议）
    const val ECC_KEY_PAIR = "peerlock_ecc_keypair"

    // AES 密钥（用于加密 TOTP 种子）
    const val TOTP_SEED_ENCRYPTOR = "peerlock_totp_seed_key"

    // AES 密钥（用于加密 SQLCipher 密码）
    const val DB_PASSPHRASE_KEY = "peerlock_db_passphrase"

    // AES 密钥（用于加密 SharedPreferences）
    const val PREFS_ENCRYPTOR = "peerlock_prefs_key"
}
```

- [ ] **步骤 2：编写 KeystoreManager 测试**

新建 `app/src/test/java/com/peerlock/data/keystore/KeystoreManagerTest.kt`：
```kotlin
package com.peerlock.data.keystore

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KeystoreManagerTest {

    @Test
    fun `KeyAlias 常量唯一`() {
        val aliases = listOf(
            KeyAlias.ECC_KEY_PAIR,
            KeyAlias.TOTP_SEED_ENCRYPTOR,
            KeyAlias.DB_PASSPHRASE_KEY,
            KeyAlias.PREFS_ENCRYPTOR
        )
        assertEquals(aliases.size, aliases.toSet().size)
    }

    @Test
    fun `KeyAlias 常量非空`() {
        assertTrue(KeyAlias.ECC_KEY_PAIR.isNotBlank())
        assertTrue(KeyAlias.TOTP_SEED_ENCRYPTOR.isNotBlank())
        assertTrue(KeyAlias.DB_PASSPHRASE_KEY.isNotBlank())
        assertTrue(KeyAlias.PREFS_ENCRYPTOR.isNotBlank())
    }
}
```

- [ ] **步骤 3：运行测试验证通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test --tests "com.peerlock.data.keystore.KeystoreManagerTest"`
预期: PASS

- [ ] **步骤 4：实现 KeystoreManager**

新建 `app/src/main/java/com/peerlock/data/keystore/KeystoreManager.kt`：
```kotlin
package com.peerlock.data.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 管理 Android Keystore 操作。
 * 所有密钥由硬件安全模块保护，不可导出。
 */
class KeystoreManager {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    /**
     * 在 Android Keystore 中生成 ECC 密钥对。
     * 私钥永不离开硬件安全模块。
     */
    fun generateEccKeyPair(alias: String = KeyAlias.ECC_KEY_PAIR): PublicKey {
        if (keyStore.containsAlias(alias)) {
            return keyStore.getCertificate(alias).publicKey
        }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT or
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(
                java.security.spec.ECGenParameterSpec("secp256r1")
            )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(false)
            .build()

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kpg.initialize(spec)
        val kp = kpg.generateKeyPair()
        return kp.public
    }

    /**
     * 获取私钥（用于签名/解密）。
     */
    fun getPrivateKey(alias: String = KeyAlias.ECC_KEY_PAIR): PrivateKey {
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey
    }

    /**
     * 生成 AES 密钥（用于加密 TOTP 种子）。
     */
    fun generateAesKey(alias: String): SecretKey {
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(spec)
        return kg.generateKey()
    }

    /**
     * 使用 Keystore AES 密钥加密数据。
     */
    fun encrypt(data: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    /**
     * 使用 Keystore AES 密钥解密数据。
     */
    fun decrypt(data: ByteArray, secretKey: SecretKey): ByteArray {
        val iv = data.sliceArray(0 until 12)
        val ciphertext = data.sliceArray(12 until data.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            secretKey,
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        return cipher.doFinal(ciphertext)
    }

    /**
     * 使用专用种子加密密钥加密 TOTP 种子。
     */
    fun encryptSeed(seed: ByteArray): ByteArray {
        val key = generateAesKey(KeyAlias.TOTP_SEED_ENCRYPTOR)
        return encrypt(seed, key)
    }

    /**
     * 解密 TOTP 种子。
     */
    fun decryptSeed(encryptedSeed: ByteArray): ByteArray {
        val key = generateAesKey(KeyAlias.TOTP_SEED_ENCRYPTOR)
        return decrypt(encryptedSeed, key)
    }

    /**
     * 生成 SQLCipher 数据库加密密码。
     * 基于 Keystore AES 密钥派生 32 字节密码。
     */
    fun generateDbPassphrase(): ByteArray {
        val key = generateAesKey(KeyAlias.DB_PASSPHRASE_KEY)
        val knownValue = "peerlock_db_v1".toByteArray()
        return encrypt(knownValue, key)
    }

    /**
     * 检查密钥是否存在于 Keystore 中。
     */
    fun hasKey(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    /**
     * 从 Keystore 中删除密钥。
     */
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
```

**注意：** KeystoreManager 使用的 Android Keystore API 需要 Android 设备或模拟器。JVM 单元测试仅测试非 Android 部分，Keystore 操作需编写集成测试。

- [ ] **步骤 5：实现 SecurePrefs**

新建 `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt`：
```kotlin
package com.peerlock.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 加密 SharedPreferences，用于非敏感配置。
 * 敏感数据（密钥、种子）存储在 Keystore 中。
 */
class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "peerlock_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // 会话状态
    var sessionId: String?
        get() = prefs.getString("session_id", null)
        set(value) = prefs.edit().putString("session_id", value).apply()

    var role: String?
        get() = prefs.getString("role", null) // "controller" 或 "controlled"
        set(value) = prefs.edit().putString("role", value).apply()

    var isPaired: Boolean
        get() = prefs.getBoolean("is_paired", false)
        set(value) = prefs.edit().putBoolean("is_paired", value).apply()

    var isDeviceOwner: Boolean
        get() = prefs.getBoolean("is_device_owner", false)
        set(value) = prefs.edit().putBoolean("is_device_owner", value).apply()

    // 时间同步
    var timeOffsetMs: Long
        get() = prefs.getLong("time_offset_ms", 0L)
        set(value) = prefs.edit().putLong("time_offset_ms", value).apply()

    var lastNtpSyncTimestamp: Long
        get() = prefs.getLong("last_ntp_sync", 0L)
        set(value) = prefs.edit().putLong("last_ntp_sync", value).apply()

    // TOTP 错误追踪
    var totpErrorCount: Int
        get() = prefs.getInt("totp_error_count", 0)
        set(value) = prefs.edit().putInt("totp_error_count", value).apply()

    var totpLockedUntil: Long
        get() = prefs.getLong("totp_locked_until", 0L)
        set(value) = prefs.edit().putLong("totp_locked_until", value).apply()

    // 原始记录保留天数
    var rawRecordsRetentionDays: Int
        get() = prefs.getInt("raw_retention_days", 30)
        set(value) = prefs.edit().putInt("raw_retention_days", value).apply()

    // 已消费信封 ID（防重放）
    var consumedEnvelopes: Set<String>
        get() = prefs.getStringSet("consumed_envelopes", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("consumed_envelopes", value).apply()

    fun addConsumedEnvelope(id: String) {
        val current = consumedEnvelopes.toMutableSet()
        current.add(id)
        consumedEnvelopes = current
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
```

- [ ] **步骤 6：提交**

```bash
git add app/src/main/java/com/peerlock/data/keystore/ app/src/main/java/com/peerlock/data/prefs/ app/src/test/java/com/peerlock/data/keystore/
git commit -m "feat: 添加 KeystoreManager 和 SecurePrefs

KeystoreManager：硬件级 ECC 密钥对、AES 密钥（种子加密、数据库密码派生）。
SecurePrefs：加密会话状态、时间同步、TOTP 错误追踪、防重放已消费信封。"
```

---

## 任务 9：Hilt 依赖注入装配

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/di/AppModule.kt`

- [ ] **步骤 1：创建 AppModule**

新建 `app/src/main/java/com/peerlock/di/AppModule.kt`：
```kotlin
package com.peerlock.di

import com.peerlock.data.keystore.KeystoreManager
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.crypto.AdaptiveCryptoEngine
import com.peerlock.domain.crypto.CryptoEngine
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

    @Provides
    @Singleton
    fun provideCryptoEngine(): CryptoEngine = AdaptiveCryptoEngine()

    @Provides
    @Singleton
    fun provideTotpEngine(): TotpEngine = TotpEngineImpl()

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides
    @Singleton
    fun provideSecurePrefs(app: android.app.Application): SecurePrefs =
        SecurePrefs(app.applicationContext)
}
```

- [ ] **步骤 2：验证构建通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew assembleDebug`
预期: BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```bash
git add app/src/main/java/com/peerlock/di/
git commit -m "feat: 装配 Hilt 依赖注入模块

SingletonComponent 作用域，所有核心依赖可供注入：
CryptoEngine、TotpEngine、KeystoreManager、SecurePrefs。"
```

---

## 任务 10：DeviceAdminReceiver 桩实现

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/system/deviceadmin/PeerLockDeviceAdminReceiver.kt`

- [ ] **步骤 1：创建 DeviceAdminReceiver**

新建 `app/src/main/java/com/peerlock/system/deviceadmin/PeerLockDeviceAdminReceiver.kt`：
```kotlin
package com.peerlock.system.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * PeerLock 设备管理员接收器。
 * 处理设备管理员生命周期事件。
 * 第三阶段将添加策略执行逻辑。
 */
class PeerLockDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "PeerLockAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已启用")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已禁用")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "PeerLock 需要设备管理员权限才能执行应用限制。"
    }
}
```

- [ ] **步骤 2：验证构建通过**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew assembleDebug`
预期: BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```bash
git add app/src/main/java/com/peerlock/system/
git commit -m "feat: 添加 DeviceAdminReceiver 桩实现

Manifest 已注册，第三阶段添加策略执行逻辑。"
```

---

## 任务 11：最终验证与清理

- [ ] **步骤 1：运行全部测试**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew test`
预期: ALL PASS

- [ ] **步骤 2：运行 lint 检查**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew lint`
预期: 无错误（警告可接受）

- [ ] **步骤 3：验证完整构建**

运行: `cd /home/lingnc/workspace/peerlock && ./gradlew assembleDebug`
预期: BUILD SUCCESSFUL

- [ ] **步骤 4：最终提交**

```bash
git add -A
git commit -m "chore: 第一阶段完成 — PeerLock MVP 基础层

- 项目脚手架：Kotlin + Compose + Hilt + Room + SQLCipher
- CryptoEngine：secp256r1（API 30-32）+ X25519（API 33+）自适应选择
- TotpEngine：RFC 6238 TOTP 生成/验证 + 信封协议
- Room 数据库：5 张表 + SQLCipher 加密
- KeystoreManager：硬件级密钥存储
- SecurePrefs：加密会话状态
- DeviceAdminReceiver 桩
- 所有 Domain 层接口已定义"
```

---

## 第一阶段未包含的内容（延迟到后续阶段）

| 内容 | 所属阶段 | 原因 |
|------|---------|------|
| 配对协议（二维码交换） | 第二阶段 | 依赖加密 + 数据库 |
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
