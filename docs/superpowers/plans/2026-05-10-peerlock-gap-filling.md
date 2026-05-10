# PeerLock 差距补齐实施计划

> **给 AI 工作者的说明：** 推荐使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步执行本计划。步骤使用 `- [ ]` 勾选语法追踪进度。

**目标：** 补齐 PeerLock MVP 设计规范与当前实现之间的 15 个缺口——安全修复、巡检数据管线、请求/响应协议修复、QR 扫描、以及缺失的 UI 界面。

**架构：** 按依赖层顺序执行：安全修复 → 巡检管线 → 协议修复 → QR 扫描 → UI 屏幕。每层内任务可并行。

**技术栈：** Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Hilt, Room+SQLCipher, AlarmManager, zxing-android-embedded

**构建环境：** `ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17`

---

## 进度追踪

- [x] Task 1.1: 修复 DB 密码硬编码 (fd8af94)
- [x] Task 1.2: L2 紧急解除移除 Device Owner (aa1226f)
- [x] Task 1.3: 配对 QR 添加过期时间 (acdf4ef)
- [ ] Task 0.0: 重写提交信息（移除 Co-Authored-By 署名）
- [ ] Task 1.4: 设备名使用真实型号
- [ ] Task 3.1: 修复 processRequest() 存根
- [ ] Task 3.2: 修复 processResponse() 存根
- [ ] Task 2.1: 巡检写入原始使用记录
- [ ] Task 2.2: 触发聚合和每日重置
- [ ] Task 2.3: AlarmManager 心跳集成
- [ ] Task 4.1: 集成 zxing QR 扫描组件
- [ ] Task 5.4: 通知增强
- [ ] Task 5.5: otpauth URI 导出
- [ ] Task 5.3: 控制端审批界面
- [ ] Task 5.1: 紧急逃生 UI
- [ ] Task 5.2: Device Owner 设置引导 UI

---

## Task 0.0: 重写提交信息（移除 Co-Authored-By 署名）

**问题：** 11 个提交（Phase 7 共 7 个 + gap-filling 已完成 4 个）的 commit message 包含多余的 "Generated with Claude Code / via Happy / Co-Authored-By" 署名行，不符合 git 提交规范。

**涉及提交（按时间顺序）：**
1. `f9cab74` — docs: 添加第七阶段实施计划（UI）
2. `61515c5` — feat: Material3 主题（动态配色 + 蓝色主色调）
3. `3dddd66` — feat: Compose 导航（路由定义 + 起始路由逻辑 + 占位 Screen）
4. `ae715a1` — feat: 共享 UI 组件（TOTP 输入、二维码、状态卡片）
5. `bc1cd1c` — feat: 引导流程（角色选择 + 配对 QR 流程 + OnboardingViewModel）
6. `5b97d85` — feat: 控制端主界面（TOTP 码展示 + 策略列表）
7. `c5afc5c` — feat: 被控端主界面（受限应用 + 解锁申请 + ControlledViewModel）
8. `ef6a848` — docs: 添加差距补齐实施计划（15 个缺口）
9. `fd8af94` — fix: 使用 KeystoreManager 生成数据库密码，移除硬编码密钥
10. `aa1226f` — fix: L2 紧急解除时移除 Device Owner 权限
11. `acdf4ef` — fix: 配对请求添加 5 分钟过期时间限制

**方案：** 使用 `git rebase` + 自定义 editor 脚本自动剥离署名行。

- [ ] **步骤 1：创建临时 editor 脚本**

```bash
cat > /tmp/strip-coauthor.sh << 'SCRIPT'
#!/bin/bash
sed -i '/^$/N;/^\n$/d' "$1"
sed -i '/^Generated with \[Claude Code\]/d' "$1"
sed -i '/^via \[Happy\]/d' "$1"
sed -i '/^Co-Authored-By:/d' "$1"
sed -i -e :a -e '/^\n*$/{$d;N;ba' -e '}' "$1"
SCRIPT
chmod +x /tmp/strip-coauthor.sh
```

- [ ] **步骤 2：执行 rebase**

```bash
GIT_SEQUENCE_EDITOR="sed -i 's/^pick/reword/g'" \
GIT_EDITOR="/tmp/strip-coauthor.sh" \
git rebase -i f9cab74^
```

这会将 f9cab74 及之后的所有提交标记为 `reword`，然后用脚本自动清理 message。

- [ ] **步骤 3：验证**

```bash
git log --oneline -15
# 确认所有提交只有第一行，无署名
git log --format="%B" -1 | cat
# 确认最新提交的 message 干净
```

- [ ] **步骤 4：清理临时文件**

```bash
rm /tmp/strip-coauthor.sh
```

---

## Layer 1: 安全修复（无依赖）

### Task 1.1: 修复 DB 密码硬编码

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/data/db/DatabaseModule.kt:26`
- 参考: `app/src/main/java/com/peerlock/data/keystore/KeystoreManager.kt:103`（已有 `generateDbPassphrase()`）

**问题：** 第 26 行硬编码 `"peerlock_dev_passphrase"`。
**方案：** 注入 `KeystoreManager`，调用已有的 `generateDbPassphrase()`。

- [ ] **步骤 1：修改 DatabaseModule.kt**

`provideDatabase` 接收 `KeystoreManager` 参数，替换硬编码密码：

```kotlin
@Provides
@Singleton
fun provideDatabase(
    @ApplicationContext context: Context,
    keystoreManager: KeystoreManager,
): PeerLockDatabase {
    val passphrase = keystoreManager.generateDbPassphrase()
    val factory = SupportFactory(passphrase)
    return Room.databaseBuilder(context, PeerLockDatabase::class.java, "peerlock.db")
        .openHelperFactory(factory)
        .build()
}
```

- [ ] **步骤 2：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`

- [ ] **步骤 3：提交**

```bash
git add app/src/main/java/com/peerlock/data/db/DatabaseModule.kt
git commit -m "fix: 使用 KeystoreManager 生成数据库密码，移除硬编码密钥"
```

---

### Task 1.2: L2 紧急解除移除 Device Owner

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/system/deviceadmin/DeviceOwnerManager.kt` — 接口添加 `removeDeviceOwner()`
- 修改: `app/src/main/java/com/peerlock/system/deviceadmin/DeviceOwnerManagerImpl.kt` — 实现
- 修改: `app/src/main/java/com/peerlock/domain/emergency/EmergencyManagerImpl.kt` — L2 调用移除

- [ ] **步骤 1：接口添加方法**

`DeviceOwnerManager.kt` 添加：
```kotlin
/** 移除当前应用的 Device Owner 身份 */
fun removeDeviceOwner(): Boolean
```

- [ ] **步骤 2：实现方法**

`DeviceOwnerManagerImpl.kt` 添加：
```kotlin
override fun removeDeviceOwner(): Boolean {
    return try {
        dpm.clearDeviceOwnerApp(context.packageName)
        true
    } catch (e: Exception) {
        Log.e(TAG, "移除 DO 失败", e)
        false
    }
}
```

- [ ] **步骤 3：EmergencyManagerImpl 调用**

`verifyAndExecuteEmergency()` 中在 `clearCryptoMaterial()` 之后添加 `deviceOwnerManager.removeDeviceOwner()`。

- [ ] **步骤 4：更新测试**

更新 `EmergencyManagerImplTest.kt` 验证 L2 流程调用 `removeDeviceOwner()`。
更新 `DeviceOwnerManagerImplTest.kt` 添加测试。

- [ ] **步骤 5：构建+测试验证**

运行: `./gradlew :app:testDebugUnitTest 2>&1 | tail -10`

- [ ] **步骤 6：提交**

```bash
git commit -m "fix: L2 紧急解除时移除 Device Owner 权限"
```

---

### Task 1.3: 配对 QR 添加过期时间

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/domain/pairing/PairingModels.kt` — PairingRequest 添加 `exp` 字段
- 修改: `app/src/main/java/com/peerlock/domain/pairing/PairingProtocolImpl.kt` — 设置过期 + 检查过期
- 修改: `app/src/main/java/com/peerlock/data/qr/QrCodec.kt` — 序列化自动包含新字段

- [ ] **步骤 1：PairingRequest 添加 exp 字段**

```kotlin
@Serializable
data class PairingRequest(
    val v: Int = 1,
    val type: String = "pair_req",
    val id: String,
    val pub: String,
    val name: String,
    val exp: Long = 0L,  // 过期时间（epoch 秒），0 = 不过期
)
```

- [ ] **步骤 2：PairingProtocolImpl 设置过期**

`generatePairRequest()` 中：
```kotlin
val expSeconds = System.currentTimeMillis() / 1000 + 300L  // 5 分钟
return PairingRequest(id = sessionId, pub = ..., name = deviceName, exp = expSeconds)
```

- [ ] **步骤 3：processPairRequest 检查过期**

```kotlin
if (request.exp > 0) {
    val nowSeconds = System.currentTimeMillis() / 1000
    if (nowSeconds > request.exp) {
        throw IllegalStateException("配对请求已过期")
    }
}
```

- [ ] **步骤 4：更新测试 + 构建验证**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.pairing.*" 2>&1 | tail -10`

- [ ] **步骤 5：提交**

```bash
git commit -m "fix: 配对请求添加 5 分钟过期时间限制"
```

---

### Task 1.4: 设备名使用真实型号

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/ui/onboarding/OnboardingViewModel.kt`

- [ ] **步骤 1：添加 Application 依赖和 deviceName**

构造函数注入 `Application`，添加：
```kotlin
private val deviceName: String
    get() = "${Build.MANUFACTURER} ${Build.MODEL}"
```

替换所有 `"被控端"` / `"控制端"` 为 `deviceName`。

- [ ] **步骤 2：构建验证**

运行: `./gradlew :app:assembleDebug 2>&1 | tail -10`

- [ ] **步骤 3：提交**

```bash
git commit -m "fix: 使用真实设备型号替代硬编码设备名"
```

---

## Layer 2: 巡检数据管线

### Task 2.1: 巡检写入原始使用记录

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/system/service/PatrolLogic.kt`
- 参考: `app/src/main/java/com/peerlock/data/usage/UsageStatsCollector.kt`（`queryUsageStats`）
- 参考: `app/src/main/java/com/peerlock/domain/repository/StorageRepository.kt`（`insertUsageRecord`）

- [ ] **步骤 1：在 executePatrol() 开头添加使用记录采集**

```kotlin
// 0. 采集使用记录
collectUsageRecords()
```

新方法：
```kotlin
private suspend fun collectUsageRecords() {
    val now = System.currentTimeMillis()
    val hourStartMs = now - (now % 3_600_000L)
    val date = java.time.Instant.ofEpochMilli(now)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate().toString()

    val stats = usageCollector.queryUsageStats(hourStartMs, now)
    for (stat in stats) {
        storageRepository.insertUsageRecord(
            UsageRecord(
                packageName = stat.packageName,
                startTime = hourStartMs,
                endTime = now,
                durationMs = stat.totalTimeMs,
                date = date,
            )
        )
    }
}
```

- [ ] **步骤 2：构建验证**

运行: `./gradlew :app:assembleDebug 2>&1 | tail -10`

- [ ] **步骤 3：提交**

```bash
git commit -m "feat: 巡检时采集并写入应用使用记录"
```

---

### Task 2.2: 触发聚合和每日重置

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/system/service/PatrolLogic.kt` — 添加 `securePrefs` 参数 + `scheduleAggregation()`
- 修改: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt` — 添加 `lastDailyAggregationDate`
- 修改: `app/src/main/java/com/peerlock/system/service/PeerLockService.kt` — 传递 `securePrefs`

- [ ] **步骤 1：SecurePrefs 添加字段**

```kotlin
var lastDailyAggregationDate: String
    get() = prefs.getString("last_daily_agg_date", "") ?: ""
    set(value) = prefs.edit().putString("last_daily_agg_date", value).apply()
```

- [ ] **步骤 2：PatrolLogic 添加 securePrefs 参数和 scheduleAggregation()**

在 `collectUsageRecords()` 之后调用 `scheduleAggregation()`：

```kotlin
private suspend fun scheduleAggregation() {
    val now = System.currentTimeMillis()
    val zone = java.time.ZoneId.systemDefault()
    val localNow = java.time.Instant.ofEpochMilli(now).atZone(zone)
    val currentDate = localNow.toLocalDate().toString()
    val currentHour = localNow.hour

    val lastHour = if (currentHour == 0) 23 else currentHour - 1
    val lastDate = if (currentHour == 0) {
        localNow.toLocalDate().minusDays(1).toString()
    } else currentDate
    usageAggregator.aggregateHourly(lastDate, lastHour)

    val lastDailyRun = securePrefs.lastDailyAggregationDate
    if (lastDailyRun != currentDate) {
        usageAggregator.aggregateDaily(lastDate)
        usageAggregator.cleanupOldRecords()
        policyEngine.resetDailyUsage()
        securePrefs.lastDailyAggregationDate = currentDate
    }
}
```

- [ ] **步骤 3：PeerLockService 传递 securePrefs**

构造 PatrolLogic 时传入 `securePrefs`。

- [ ] **步骤 4：构建验证**

- [ ] **步骤 5：提交**

```bash
git commit -m "feat: 巡检触发小时聚合、每日聚合和数据清理"
```

---

### Task 2.3: AlarmManager 心跳集成

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/system/service/PeerLockService.kt`
- 修改: `app/src/main/AndroidManifest.xml` — 添加 `SCHEDULE_EXACT_ALARM`

- [ ] **步骤 1：AndroidManifest 添加权限**

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

- [ ] **步骤 2：重构 PeerLockService**

- 用 `AlarmManager.setExactAndAllowWhileIdle()` 替代 `delay()` 循环
- `onStartCommand` 处理 `ACTION_PATROL` intent → 执行一次巡检 → 调度下一次
- 移除 `startPatrolLoop()` 和 `patrolJob`
- 巡检后更新通知内容

- [ ] **步骤 3：构建验证**

- [ ] **步骤 4：提交**

```bash
git commit -m "refactor: 使用 AlarmManager 替代 delay() 实现可靠的巡检心跳"
```

---

## Layer 3: 请求/响应协议修复

### Task 3.1: 修复 processRequest() 存根

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/domain/totp/TotpEnvelope.kt` — 扩展 EnvelopeConfig
- 修改: `app/src/main/java/com/peerlock/domain/request/RequestProtocolImpl.kt` — processRequest + generate*

- [ ] **步骤 1：扩展 EnvelopeConfig**

```kotlin
@Serializable
data class EnvelopeConfig(
    val durationMinutes: Int? = null,
    val durationMode: String? = null,
    val absoluteEndTime: Long? = null,
    val targetPackage: String? = null,
    val requestDataJson: String? = null,  // 序列化的请求/响应数据
    val approved: Boolean? = null,
    val rejectReason: String? = null,
)
```

- [ ] **步骤 2：generateUnlockRequest 嵌入 config**

```kotlin
val config = EnvelopeConfig(
    durationMinutes = requestedDuration,
    durationMode = durationMode,
    targetPackage = targetPackage,
)
val totpEnvelope = TotpEnvelope(
    type = "unlock", code = code, sessionId = sessionId,
    timestamp = nowSeconds, config = config,
)
```

- [ ] **步骤 3：generateConfigRequest 嵌入 config**

```kotlin
val changesJson = Json.encodeToString(changes)
val config = EnvelopeConfig(requestDataJson = changesJson)
val totpEnvelope = TotpEnvelope(
    type = "setting", code = code, sessionId = sessionId,
    timestamp = nowSeconds, config = config,
)
```

- [ ] **步骤 4：重写 processRequest()**

- 验证信封有效期
- 验证 TOTP 码（使用 `totpEngine.verifyEnvelope()`）
- 从 `config` 提取 `targetPackage`、`durationMinutes`、`durationMode`
- 根据 `type` 构建正确的 `RequestPayload`

- [ ] **步骤 5：更新测试**

- [ ] **步骤 6：构建+测试验证**

- [ ] **步骤 7：提交**

```bash
git commit -m "fix: processRequest 实现 TOTP 验证和请求数据提取"
```

---

### Task 3.2: 修复 processResponse() 存根

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/domain/request/RequestProtocolImpl.kt`

- [ ] **步骤 1：generateUnlockResponse 嵌入 config**

```kotlin
val config = EnvelopeConfig(
    targetPackage = ...,
    durationMinutes = duration,
    durationMode = durationMode,
    approved = approved,
)
```

- [ ] **步骤 2：generateConfigResponse 嵌入 config**

```kotlin
val config = EnvelopeConfig(
    requestDataJson = changes?.let { Json.encodeToString(it) },
    approved = approved,
    rejectReason = rejectReason,
)
```

- [ ] **步骤 3：重写 processResponse()**

- 验证信封 + TOTP + 防重放
- 从 `config` 提取 `approved`、响应数据
- 根据 `type` 构建正确的 `ResponsePayload`

- [ ] **步骤 4：更新测试**

- [ ] **步骤 5：构建+测试验证**

- [ ] **步骤 6：提交**

```bash
git commit -m "fix: processResponse 实现 TOTP 验证和响应数据提取"
```

---

## Layer 4: QR 扫描

### Task 4.1: 集成 zxing QR 扫描组件

**涉及文件：**
- 修改: `app/src/main/AndroidManifest.xml` — CAMERA 权限
- 新建: `app/src/main/java/com/peerlock/ui/common/QrScanner.kt`
- 修改: `app/src/main/java/com/peerlock/ui/onboarding/PairingScreen.kt` — 添加扫描按钮
- 修改: `app/src/main/java/com/peerlock/ui/onboarding/OnboardingViewModel.kt` — scanTrigger
- 参考: 已有依赖 `zxing-android-embedded:4.3.0` 在 `gradle/libs.versions.toml`

- [ ] **步骤 1：AndroidManifest 添加权限**

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

- [ ] **步骤 2：创建 QrScanner.kt**

使用 `zxing-android-embedded` 的 `ScanContract` + `ActivityResultContracts.RequestPermission`：
```kotlin
@Composable
fun QrScanLauncher(onResult: (String?) -> Unit, trigger: Int) {
    // 权限检查 → 启动 ScanContract → 回调结果
}
```

- [ ] **步骤 3：OnboardingViewModel 添加 scanTrigger**

```kotlin
private val _scanTrigger = MutableStateFlow(0)
val scanTrigger: StateFlow<Int> = _scanTrigger.asStateFlow()
fun requestScan() { _scanTrigger.value++ }
```

- [ ] **步骤 4：PairingScreen 添加扫描按钮**

在 controller 的 SHOW_MY_QR 步骤和 controlled 的 SCAN_PEER_QR 步骤中添加"扫描"按钮，调用 `viewModel.requestScan()`。

- [ ] **步骤 5：构建验证**

- [ ] **步骤 6：提交**

```bash
git commit -m "feat: 集成 zxing QR 扫描组件，添加 CAMERA 权限"
```

---

## Layer 5: UI 屏幕

### Task 5.1: 紧急逃生 UI

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/emergency/EmergencyViewModel.kt`
- 新建: `app/src/main/java/com/peerlock/ui/emergency/EmergencyScreen.kt`
- 修改: `app/src/main/java/com/peerlock/ui/navigation/PeerLockNavHost.kt` — 添加 EMERGENCY 路由
- 修改: `app/src/main/java/com/peerlock/ui/controller/ControllerHomeScreen.kt` — 入口按钮

- [ ] **步骤 1：创建 EmergencyViewModel**

- `verifyDestroyCode()` + `executeDestroy()`（L1）
- `generateNonce()` + 构建 ADB 命令（L2）
- `showDestroyConfirm` / `clearError` 状态管理

- [ ] **步骤 2：创建 EmergencyScreen**

- L1 区域：6 位 TOTP 输入 + "执行终止" 按钮 + 确认 AlertDialog
- L2 区域：7 次点击隐藏入口 + ADB 命令显示（不可复制）
- `FLAG_SECURE` 防截图

- [ ] **步骤 3：NavHost 添加路由**

```kotlin
const val EMERGENCY = "emergency"
composable(Routes.EMERGENCY) { EmergencyScreen(...) }
```

- [ ] **步骤 4：ControllerHomeScreen 添加入口**

- [ ] **步骤 5：构建验证**

- [ ] **步骤 6：提交**

```bash
git commit -m "feat: 紧急逃生 UI（L1 终止码 + L2 高级解除）"
```

---

### Task 5.2: Device Owner 设置引导 UI

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/onboarding/DeviceOwnerSetupScreen.kt`
- 新建: `app/src/main/java/com/peerlock/ui/onboarding/DeviceOwnerSetupViewModel.kt`
- 修改: `app/src/main/java/com/peerlock/ui/navigation/PeerLockNavHost.kt` — 添加路由

- [ ] **步骤 1：创建 DeviceOwnerSetupViewModel**

- 检查 `deviceOwnerManager.isDeviceOwner()` 状态

- [ ] **步骤 2：创建 DeviceOwnerSetupScreen**

- ADB 命令显示 + 复制按钮
- "检查设置状态" 按钮
- 成功后"继续"，可"跳过（功能受限）"

- [ ] **步骤 3：NavHost 添加路由 + 配对完成后跳转逻辑**

- [ ] **步骤 4：构建验证**

- [ ] **步骤 5：提交**

```bash
git commit -m "feat: Device Owner 设置引导页面"
```

---

### Task 5.3: 控制端审批界面

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/controller/RequestApprovalScreen.kt`
- 修改: `app/src/main/java/com/peerlock/ui/controller/ControllerViewModel.kt` — 添加审批状态+方法
- 修改: `app/src/main/java/com/peerlock/ui/navigation/PeerLockNavHost.kt` — 添加路由
- 修改: `app/src/main/java/com/peerlock/ui/controller/ControllerHomeScreen.kt` — 入口

- [ ] **步骤 1：ControllerViewModel 添加审批功能**

- `ApprovalStep` 枚举：IDLE, SCANNING, REVIEWING, SHOWING_RESPONSE
- `onQrScanned()` → `requestProtocol.processRequest()` → 显示请求详情
- `approveRequest()` → `generateUnlockResponse/generateConfigResponse` → 显示响应 QR
- `rejectRequest()` → 生成拒绝响应

- [ ] **步骤 2：创建 RequestApprovalScreen**

- 扫描请求 QR（使用 QrScanLauncher）
- 显示请求详情 + 审批/拒绝按钮
- 显示响应 QR 码

- [ ] **步骤 3：NavHost 添加路由 + ControllerHomeScreen 入口**

- [ ] **步骤 4：构建验证**

- [ ] **步骤 5：提交**

```bash
git commit -m "feat: 控制端请求审批界面（扫描 + 审核 + 响应 QR）"
```

---

### Task 5.4: 通知增强

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/system/service/PeerLockService.kt`

- [ ] **步骤 1：增强 buildNotification()**

- 显示今日屏幕时间 + 受限应用数量
- 添加 `setContentIntent` 点击打开 App
- 巡检后调用 `notificationManager.notify()` 更新

- [ ] **步骤 2：构建验证**

- [ ] **步骤 3：提交**

```bash
git commit -m "feat: 前台通知显示屏幕时间和受限应用数量，点击打开应用"
```

---

### Task 5.5: otpauth URI 导出

**涉及文件：**
- 修改: `app/src/main/java/com/peerlock/ui/controller/ControllerViewModel.kt`
- 修改: `app/src/main/java/com/peerlock/ui/controller/ControllerHomeScreen.kt`

- [ ] **步骤 1：ControllerViewModel 添加 getOtpauthUris()**

手动实现 Base32 编码（~15 行），生成 `otpauth://totp/PeerLock:管理码?secret=...` 格式 URI。

- [ ] **步骤 2：ControllerHomeScreen 添加导出区域**

TOTP 码列表下方添加"导出到外部应用"区域，每个 URI 带复制按钮。

- [ ] **步骤 3：构建验证**

- [ ] **步骤 4：提交**

```bash
git commit -m "feat: 控制端显示 otpauth URI，支持复制到外部 TOTP 应用"
```

---

## 实施顺序

| Phase | Tasks | 说明 |
|-------|-------|------|
| A: 安全 + 协议 | 1.1 → 1.2 → 1.3 → 1.4 → 3.1 → 3.2 | 核心安全修复 + 协议存根修复 |
| B: 巡检管线 | 2.1 → 2.2 → 2.3 | 数据写入 → 聚合触发 → AlarmManager |
| C: UI | 4.1 → 5.4 → 5.5 → 5.3 → 5.1 → 5.2 | 扫描 → 通知 → 导出 → 审批 → 紧急 → DO |

## 验证清单

每个 Task 完成后：
1. `./gradlew :app:assembleDebug` — 编译通过
2. `./gradlew :app:testDebugUnitTest` — 测试通过
3. 新功能有对应测试
4. 域层无 Android 依赖
5. 中文提交信息
