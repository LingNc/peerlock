# PeerLock MVP 第三阶段：Device Owner、应用暂停、前台服务

> **给 AI 工作者的说明：** 推荐使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步执行本计划。步骤使用 `- [ ]` 勾选语法追踪进度。

**目标：** 实现 Device Owner 管理、应用暂停策略执行、前台服务保活。被控端通过 Device Owner 特权调用 `setPackagesSuspended()` 暂停受限应用，前台服务每 30 秒巡检一次策略状态，开机自启保活。

**架构：** Domain 层定义 `PolicyEngine`、`DeviceOwnerManager` 接口（已有 PolicyEngine/RestrictionPolicy/PolicyAction 骨架）。Data 层实现 `StorageRepositoryImpl`（Room DAO 桥接）、`UsageStatsCollector`（UsageStatsManager 封装）。System 层实现 `PeerLockService`（前台服务 + 巡检循环）、`BootReceiver`（开机自启）、增强 `DeviceAdminReceiver`。

**技术栈：** 第一、二阶段全部 + Android DevicePolicyManager + UsageStatsManager + Foreground Service

**参考设计文档：** `docs/superpowers/specs/2026-05-09-peerlock-mvp-design.md` 第 8、11、12 节

---

## 文件结构（第三阶段新增/修改）

```
app/src/main/java/com/peerlock/
├── domain/
│   ├── policy/
│   │   ├── PolicyEngine.kt                 # 已有接口
│   │   ├── PolicyEngineImpl.kt             # 新建：策略评估实现
│   │   ├── RestrictionPolicy.kt            # 已有数据类
│   │   └── PolicyAction.kt                 # 已有密封类
│   └── repository/
│       ├── StorageRepository.kt            # 已有接口
│       └── StorageRepositoryImpl.kt        # 新建：Room DAO 桥接
├── data/
│   └── usage/
│       ├── UsageStatsCollector.kt          # 新建：接口
│       └── UsageStatsCollectorImpl.kt      # 新建：UsageStatsManager 封装
├── system/
│   ├── deviceadmin/
│   │   ├── PeerLockDeviceAdminReceiver.kt  # 修改：增强生命周期处理
│   │   └── DeviceOwnerManager.kt           # 新建：接口
│   │   └── DeviceOwnerManagerImpl.kt       # 新建：DPM 封装
│   ├── service/
│   │   └── PeerLockService.kt              # 新建：前台服务
│   └── receiver/
│       └── BootReceiver.kt                 # 新建：开机广播
├── di/
│   └── AppModule.kt                        # 修改：添加新依赖
app/src/main/AndroidManifest.xml            # 修改：注册 Service + BootReceiver
app/src/test/java/com/peerlock/
├── domain/policy/
│   └── PolicyEngineImplTest.kt
├── domain/repository/
│   └── StorageRepositoryImplTest.kt
├── data/usage/
│   └── UsageStatsCollectorImplTest.kt
└── system/
    └── PeerLockServiceTest.kt
```

---

## 任务 1：StorageRepository 实现（Room DAO 桥接）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/repository/StorageRepositoryImpl.kt`
- 新建: `app/src/test/java/com/peerlock/domain/repository/StorageRepositoryImplTest.kt`
- 已有: `app/src/main/java/com/peerlock/domain/repository/StorageRepository.kt`（接口）
- 已有: `app/src/main/java/com/peerlock/data/db/dao/*.kt`（5 个 DAO）
- 已有: `app/src/main/java/com/peerlock/data/db/entity/*.kt`（5 个 Entity）

- [ ] **步骤 1：编写 StorageRepositoryImpl 测试**

新建 `app/src/test/java/com/peerlock/domain/repository/StorageRepositoryImplTest.kt`：

```kotlin
package com.peerlock.domain.repository

import com.peerlock.data.db.dao.*
import com.peerlock.data.db.entity.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class StorageRepositoryImplTest {

    private lateinit var repo: StorageRepositoryImpl
    private lateinit var usageRecordDao: UsageRecordDao
    private lateinit var hourlySummaryDao: HourlySummaryDao
    private lateinit var dailySummaryDao: DailySummaryDao
    private lateinit var policyDao: RestrictionPolicyDao
    private lateinit var auditLogDao: AuditLogDao

    @BeforeEach
    fun setup() {
        usageRecordDao = mockk(relaxed = true)
        hourlySummaryDao = mockk(relaxed = true)
        dailySummaryDao = mockk(relaxed = true)
        policyDao = mockk(relaxed = true)
        auditLogDao = mockk(relaxed = true)
        repo = StorageRepositoryImpl(
            usageRecordDao, hourlySummaryDao, dailySummaryDao, policyDao, auditLogDao
        )
    }

    @Test
    fun `getAllPolicies 映射正确`() = runTest {
        val entity = RestrictionPolicyEntity(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = 60,
            allowedTimeStart = "08:00", allowedTimeEnd = "22:00",
            isBlacklist = true, isActive = true, createdAt = 1000L, lastModified = 2000L
        )
        coEvery { policyDao.getAll() } returns listOf(entity)

        val result = repo.getAllPolicies()

        assertEquals(1, result.size)
        assertEquals("com.test", result[0].targetPackage)
        assertEquals(60, result[0].dailyLimitMinutes)
        assertEquals(LocalTime.of(8, 0), result[0].allowedTimeStart)
        assertEquals(LocalTime.of(22, 0), result[0].allowedTimeEnd)
    }

    @Test
    fun `upsertPolicy 映射到 Entity`() = runTest {
        val policy = RestrictionPolicy(
            id = 0, targetPackage = "com.test", dailyLimitMinutes = 30,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = false, isActive = true, createdAt = 1000L, lastModified = 2000L
        )

        repo.upsertPolicy(policy)

        coVerify {
            policyDao.upsert(withArg { entity ->
                assertEquals("com.test", entity.targetPackage)
                assertEquals(30, entity.dailyLimitMinutes)
                assertNull(entity.allowedTimeStart)
            })
        }
    }

    @Test
    fun `insertUsageRecord 映射到 Entity`() = runTest {
        val record = UsageRecord(
            packageName = "com.test", startTime = 1000L, endTime = 2000L,
            durationMs = 1000L, date = "2026-05-10"
        )

        repo.insertUsageRecord(record)

        coVerify {
            usageRecordDao.insert(withArg { entity ->
                assertEquals("com.test", entity.packageName)
                assertEquals(1000L, entity.durationMs)
            })
        }
    }

    @Test
    fun `insertAuditLog 映射到 Entity`() = runTest {
        val log = AuditLog(
            timestamp = 1000L, action = "SUSPEND", targetPackage = "com.test", detail = "超时"
        )

        repo.insertAuditLog(log)

        coVerify {
            auditLogDao.insert(withArg { entity ->
                assertEquals("SUSPEND", entity.action)
                assertEquals("com.test", entity.targetPackage)
            })
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.repository.StorageRepositoryImplTest"`
预期: FAIL — StorageRepositoryImpl 类未找到

- [ ] **步骤 3：实现 StorageRepositoryImpl**

新建 `app/src/main/java/com/peerlock/domain/repository/StorageRepositoryImpl.kt`：

```kotlin
package com.peerlock.domain.repository

import com.peerlock.data.db.dao.*
import com.peerlock.data.db.entity.*
import com.peerlock.domain.policy.RestrictionPolicy
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

class StorageRepositoryImpl(
    private val usageRecordDao: UsageRecordDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val dailySummaryDao: DailySummaryDao,
    private val policyDao: RestrictionPolicyDao,
    private val auditLogDao: AuditLogDao,
) : StorageRepository {

    // ---- 使用记录 ----

    override suspend fun insertUsageRecord(record: UsageRecord) {
        usageRecordDao.insert(record.toEntity())
    }

    override suspend fun getUsageByDate(date: String): List<UsageRecord> {
        return usageRecordDao.getByDate(date).map { it.toDomain() }
    }

    override fun observeUsageByDate(date: String): Flow<List<UsageRecord>> {
        return usageRecordDao.observeByDate(date).flow { records -> records.map { it.toDomain() } }
    }

    // ---- 小时汇总 ----

    override suspend fun insertHourlySummary(summary: HourlySummary) {
        hourlySummaryDao.upsert(summary.toEntity())
    }

    override suspend fun getHourlySummary(date: String): List<HourlySummary> {
        return hourlySummaryDao.getByDate(date).map { it.toDomain() }
    }

    // ---- 日汇总 ----

    override suspend fun insertDailySummary(summary: DailySummary) {
        dailySummaryDao.upsert(summary.toEntity())
    }

    override suspend fun getDailySummary(startDate: String, endDate: String): List<DailySummary> {
        return dailySummaryDao.getByDateRange(startDate, endDate).map { it.toDomain() }
    }

    override suspend fun getDailySummaryByPackage(
        packageName: String, startDate: String, endDate: String
    ): List<DailySummary> {
        return dailySummaryDao.getByPackageAndDateRange(packageName, startDate, endDate).map { it.toDomain() }
    }

    // ---- 策略 ----

    override suspend fun getAllPolicies(): List<RestrictionPolicy> {
        return policyDao.getAll().map { it.toDomain() }
    }

    override suspend fun getActivePolicies(): List<RestrictionPolicy> {
        return policyDao.getActive().map { it.toDomain() }
    }

    override suspend fun upsertPolicy(policy: RestrictionPolicy) {
        policyDao.upsert(policy.toEntity())
    }

    override suspend fun deletePolicy(id: Long) {
        policyDao.deleteById(id)
    }

    // ---- 审计日志 ----

    override suspend fun insertAuditLog(log: AuditLog) {
        auditLogDao.insert(log.toEntity())
    }

    override suspend fun getRecentAuditLogs(limit: Int): List<AuditLog> {
        return auditLogDao.getRecent(limit).map { it.toDomain() }
    }

    // ---- 映射函数 ----

    private fun UsageRecordEntity.toDomain() = UsageRecord(
        packageName = packageName, startTime = startTime, endTime = endTime,
        durationMs = durationMs, date = date
    )

    private fun UsageRecord.toEntity() = UsageRecordEntity(
        packageName = packageName, startTime = startTime, endTime = endTime,
        durationMs = durationMs, date = date
    )

    private fun HourlySummaryEntity.toDomain() = HourlySummary(
        packageName = packageName, date = date, hour = hour, totalMs = totalMs
    )

    private fun HourlySummary.toEntity() = HourlySummaryEntity(
        packageName = packageName, date = date, hour = hour, totalMs = totalMs
    )

    private fun DailySummaryEntity.toDomain() = DailySummary(
        packageName = packageName, date = date, totalMs = totalMs, launchCount = launchCount
    )

    private fun DailySummary.toEntity() = DailySummaryEntity(
        packageName = packageName, date = date, totalMs = totalMs, launchCount = launchCount
    )

    private fun RestrictionPolicyEntity.toDomain() = RestrictionPolicy(
        id = id, targetPackage = targetPackage, dailyLimitMinutes = dailyLimitMinutes,
        allowedTimeStart = allowedTimeStart?.let { LocalTime.parse(it) },
        allowedTimeEnd = allowedTimeEnd?.let { LocalTime.parse(it) },
        isBlacklist = isBlacklist, isActive = isActive, createdAt = createdAt, lastModified = lastModified
    )

    private fun RestrictionPolicy.toEntity() = RestrictionPolicyEntity(
        id = id, targetPackage = targetPackage, dailyLimitMinutes = dailyLimitMinutes,
        allowedTimeStart = allowedTimeStart?.toString(), allowedTimeEnd = allowedTimeEnd?.toString(),
        isBlacklist = isBlacklist, isActive = isActive, createdAt = createdAt, lastModified = lastModified
    )

    private fun AuditLogEntity.toDomain() = AuditLog(
        timestamp = timestamp, action = action, targetPackage = targetPackage, detail = detail
    )

    private fun AuditLog.toEntity() = AuditLogEntity(
        timestamp = timestamp, action = action, targetPackage = targetPackage, detail = detail
    )
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.repository.StorageRepositoryImplTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/repository/StorageRepositoryImpl.kt \
  app/src/test/java/com/peerlock/domain/repository/StorageRepositoryImplTest.kt
git commit -m "feat: 实现 StorageRepository（Room DAO 桥接）

- Domain 模型与 Entity 双向映射
- 策略/使用记录/汇总/审计日志 CRUD
- LocalTime <-> String 类型转换"
```

---

## 任务 2：Device Owner 管理（DeviceOwnerManager）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/system/deviceadmin/DeviceOwnerManager.kt`
- 新建: `app/src/main/java/com/peerlock/system/deviceadmin/DeviceOwnerManagerImpl.kt`
- 修改: `app/src/main/java/com/peerlock/system/deviceadmin/PeerLockDeviceAdminReceiver.kt`
- 新建: `app/src/test/java/com/peerlock/system/deviceadmin/DeviceOwnerManagerImplTest.kt`

- [ ] **步骤 1：创建 DeviceOwnerManager 接口**

新建 `app/src/main/java/com/peerlock/system/deviceadmin/DeviceOwnerManager.kt`：

```kotlin
package com.peerlock.system.deviceadmin

/**
 * Device Owner 管理接口。
 * 封装 DevicePolicyManager 的 DO 相关操作。
 */
interface DeviceOwnerManager {
    /** 当前应用是否为 Device Owner */
    fun isDeviceOwner(): Boolean

    /** 当前应用是否为设备管理员（含 DO 和普通 admin） */
    fun isAdminActive(): Boolean

    /** 暂停指定应用包 */
    fun setPackagesSuspended(packages: List<String>, suspended: Boolean): List<String>

    /** 获取管理员组件名（用于 DPM 调用） */
    fun getAdminComponentName(): android.content.ComponentName
}
```

- [ ] **步骤 2：实现 DeviceOwnerManagerImpl**

新建 `app/src/main/java/com/peerlock/system/deviceadmin/DeviceOwnerManagerImpl.kt`：

```kotlin
package com.peerlock.system.deviceadmin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class DeviceOwnerManagerImpl(
    private val context: Context,
) : DeviceOwnerManager {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent = ComponentName(context, PeerLockDeviceAdminReceiver::class.java)

    override fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    override fun isAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    override fun setPackagesSuspended(packages: List<String>, suspended: Boolean): List<String> {
        return dpm.setPackagesSuspended(adminComponent, packages.toTypedArray(), suspended).toList()
    }

    override fun getAdminComponentName(): ComponentName = adminComponent
}
```

- [ ] **步骤 3：增强 PeerLockDeviceAdminReceiver**

修改 `app/src/main/java/com/peerlock/system/deviceadmin/PeerLockDeviceAdminReceiver.kt`：

```kotlin
package com.peerlock.system.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * PeerLock 设备管理员接收器。
 * 处理设备管理员生命周期事件和策略回调。
 */
class PeerLockDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "PeerLockAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已启用（Device Owner）")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已禁用，清除配对状态")
        // 由 UI 层处理状态清除
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "PeerLock 需要设备管理员权限才能执行应用限制。禁用后所有限制将失效。"
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.i(TAG, "应用进入锁定任务模式: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.i(TAG, "退出锁定任务模式")
    }
}
```

- [ ] **步骤 4：编写测试**

新建 `app/src/test/java/com/peerlock/system/deviceadmin/DeviceOwnerManagerImplTest.kt`：

```kotlin
package com.peerlock.system.deviceadmin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * DeviceOwnerManager 的接口契约测试。
 * 实际 DPM 操作需要 Android 环境，此处测试接口存在性和方法签名。
 */
class DeviceOwnerManagerImplTest {

    @Test
    fun `接口存在且方法完整`() {
        val interfaceClass = DeviceOwnerManager::class.java
        assertNotNull(interfaceClass)
        assertTrue(interfaceClass.methods.any { it.name == "isDeviceOwner" })
        assertTrue(interfaceClass.methods.any { it.name == "isAdminActive" })
        assertTrue(interfaceClass.methods.any { it.name == "setPackagesSuspended" })
        assertTrue(interfaceClass.methods.any { it.name == "getAdminComponentName" })
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.system.deviceadmin.DeviceOwnerManagerImplTest"`
预期: ALL PASS

- [ ] **步骤 6：提交**

```bash
git add app/src/main/java/com/peerlock/system/deviceadmin/ \
  app/src/test/java/com/peerlock/system/deviceadmin/
git commit -m "feat: 实现 DeviceOwnerManager（DO 管理 + 应用暂停）

- DeviceOwnerManager 接口/实现
- setPackagesSuspended 封装
- DeviceAdminReceiver 增强生命周期处理"
```

---

## 任务 3：使用统计采集（UsageStatsCollector）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/data/usage/UsageStatsCollector.kt`
- 新建: `app/src/main/java/com/peerlock/data/usage/UsageStatsCollectorImpl.kt`
- 新建: `app/src/test/java/com/peerlock/data/usage/UsageStatsCollectorImplTest.kt`

- [ ] **步骤 1：创建 UsageStatsCollector 接口**

新建 `app/src/main/java/com/peerlock/data/usage/UsageStatsCollector.kt`：

```kotlin
package com.peerlock.data.usage

/**
 * 使用统计采集接口。
 * 封装 UsageStatsManager 操作。
 */
interface UsageStatsCollector {
    /** 获取指定日期某个应用的总使用时长（毫秒） */
    fun getUsageTimeMs(packageName: String, startMs: Long, endMs: Long): Long

    /** 获取指定时间段内所有应用的使用统计 */
    fun queryUsageStats(startMs: Long, endMs: Long): List<AppUsageInfo>

    /** 获取当前前台应用包名（null = 无前台应用） */
    fun getCurrentForegroundPackage(): String?
}

data class AppUsageInfo(
    val packageName: String,
    val totalTimeMs: Long,
    val lastUsedMs: Long,
)
```

- [ ] **步骤 2：编写测试**

新建 `app/src/test/java/com/peerlock/data/usage/UsageStatsCollectorImplTest.kt`：

```kotlin
package com.peerlock.data.usage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UsageStatsCollectorImplTest {

    @Test
    fun `接口存在且方法完整`() {
        val interfaceClass = UsageStatsCollector::class.java
        assertNotNull(interfaceClass)
        assertTrue(interfaceClass.methods.any { it.name == "getUsageTimeMs" })
        assertTrue(interfaceClass.methods.any { it.name == "queryUsageStats" })
        assertTrue(interfaceClass.methods.any { it.name == "getCurrentForegroundPackage" })
    }

    @Test
    fun `AppUsageInfo 数据类可构造`() {
        val info = AppUsageInfo(
            packageName = "com.test",
            totalTimeMs = 60000L,
            lastUsedMs = 1000L,
        )
        assertEquals("com.test", info.packageName)
        assertEquals(60000L, info.totalTimeMs)
    }
}
```

- [ ] **步骤 3：实现 UsageStatsCollectorImpl**

新建 `app/src/main/java/com/peerlock/data/usage/UsageStatsCollectorImpl.kt`：

```kotlin
package com.peerlock.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class UsageStatsCollectorImpl(
    context: Context,
) : UsageStatsCollector {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override fun getUsageTimeMs(packageName: String, startMs: Long, endMs: Long): Long {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startMs, endMs
        )
        return stats
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }

    override fun queryUsageStats(startMs: Long, endMs: Long): List<AppUsageInfo> {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startMs, endMs
        )
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .map { stat ->
                AppUsageInfo(
                    packageName = stat.packageName,
                    totalTimeMs = stat.totalTimeInForeground,
                    lastUsedMs = stat.lastTimeUsed,
                )
            }
    }

    override fun getCurrentForegroundPackage(): String? {
        val endMs = System.currentTimeMillis()
        val startMs = endMs - 5_000 // 最近 5 秒
        val events = usageStatsManager.queryEvents(startMs, endMs)

        var lastForegroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundPackage = event.packageName
            }
        }
        return lastForegroundPackage
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.data.usage.UsageStatsCollectorImplTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/data/usage/ \
  app/src/test/java/com/peerlock/data/usage/
git commit -m "feat: 实现 UsageStatsCollector（使用统计采集）

- queryUsageStats 查询应用使用时长
- getCurrentForegroundPackage 获取前台应用
- 封装 UsageStatsManager"
```

---

## 任务 4：策略引擎实现（PolicyEngineImpl）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/domain/policy/PolicyEngineImpl.kt`
- 新建: `app/src/test/java/com/peerlock/domain/policy/PolicyEngineImplTest.kt`
- 已有: `app/src/main/java/com/peerlock/domain/policy/PolicyEngine.kt`
- 已有: `app/src/main/java/com/peerlock/domain/policy/RestrictionPolicy.kt`
- 已有: `app/src/main/java/com/peerlock/domain/policy/PolicyAction.kt`

- [ ] **步骤 1：编写 PolicyEngineImpl 测试**

新建 `app/src/test/java/com/peerlock/domain/policy/PolicyEngineImplTest.kt`：

```kotlin
package com.peerlock.domain.policy

import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class PolicyEngineImplTest {

    private lateinit var engine: PolicyEngineImpl
    private lateinit var storageRepo: StorageRepository
    private lateinit var usageCollector: UsageStatsCollector
    private lateinit var deviceOwnerManager: DeviceOwnerManager

    @BeforeEach
    fun setup() {
        storageRepo = mockk(relaxed = true)
        usageCollector = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        engine = PolicyEngineImpl(storageRepo, usageCollector, deviceOwnerManager)
    }

    @Test
    fun `无策略时返回 Monitor`() = runTest {
        coEvery { storageRepo.getActivePolicies() } returns emptyList()

        val action = engine.evaluate("com.test", System.currentTimeMillis())

        assertTrue(action is PolicyAction.Monitor)
    }

    @Test
    fun `超过每日时长限制返回 Suspend`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = 60,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)

        val now = System.currentTimeMillis()
        val dayStart = now - (now % 86_400_000) // 当天 00:00
        every { usageCollector.getUsageTimeMs("com.test", dayStart, now) } returns 61 * 60 * 1000L

        val action = engine.evaluate("com.test", now)

        assertTrue(action is PolicyAction.Suspend)
    }

    @Test
    fun `未超时返回 Monitor`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = 60,
            allowedTimeStart = null, allowedTimeEnd = null,
            isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)

        val now = System.currentTimeMillis()
        val dayStart = now - (now % 86_400_000)
        every { usageCollector.getUsageTimeMs("com.test", dayStart, now) } returns 30 * 60 * 1000L

        val action = engine.evaluate("com.test", now)

        assertTrue(action is PolicyAction.Monitor)
    }

    @Test
    fun `非允许时段返回 Suspend`() = runTest {
        val policy = RestrictionPolicy(
            id = 1, targetPackage = "com.test", dailyLimitMinutes = null,
            allowedTimeStart = LocalTime.of(8, 0), allowedTimeEnd = LocalTime.of(22, 0),
            isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
        )
        coEvery { storageRepo.getActivePolicies() } returns listOf(policy)

        // 构造凌晨 3 点的时间戳
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 3)
        cal.set(java.util.Calendar.MINUTE, 0)
        val now = cal.timeInMillis

        val action = engine.evaluate("com.test", now)

        assertTrue(action is PolicyAction.Suspend)
    }

    @Test
    fun `suspendApp 调用 DeviceOwnerManager`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true
        every { deviceOwnerManager.setPackagesSuspended(any(), true) } returns emptyList()

        engine.suspendApp("com.test")

        verify { deviceOwnerManager.setPackagesSuspended(listOf("com.test"), true) }
        coVerify { storageRepo.insertAuditLog(any()) }
    }

    @Test
    fun `unsuspendApp 调用 DeviceOwnerManager`() = runTest {
        every { deviceOwnerManager.isDeviceOwner() } returns true
        every { deviceOwnerManager.setPackagesSuspended(any(), false) } returns emptyList()

        engine.unsuspendApp("com.test")

        verify { deviceOwnerManager.setPackagesSuspended(listOf("com.test"), false) }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.policy.PolicyEngineImplTest"`
预期: FAIL — PolicyEngineImpl 类未找到

- [ ] **步骤 3：实现 PolicyEngineImpl**

新建 `app/src/main/java/com/peerlock/domain/policy/PolicyEngineImpl.kt`：

```kotlin
package com.peerlock.domain.policy

import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class PolicyEngineImpl(
    private val storageRepository: StorageRepository,
    private val usageStatsCollector: UsageStatsCollector,
    private val deviceOwnerManager: DeviceOwnerManager,
) : PolicyEngine {

    override suspend fun evaluate(packageName: String, currentTimeMillis: Long): PolicyAction {
        val policies = storageRepository.getActivePolicies()
        val policy = policies.find { it.targetPackage == packageName }
            ?: return PolicyAction.Monitor

        val zone = ZoneId.systemDefault()
        val currentLocalTime = Instant.ofEpochMilli(currentTimeMillis).atZone(zone).toLocalTime()

        // 检查允许时段
        if (policy.allowedTimeStart != null && policy.allowedTimeEnd != null) {
            if (!isWithinAllowedTime(currentLocalTime, policy.allowedTimeStart, policy.allowedTimeEnd)) {
                return PolicyAction.Suspend
            }
        }

        // 检查每日时长限制
        if (policy.dailyLimitMinutes != null) {
            val dayStartMs = currentTimeMillis - (currentTimeMillis % 86_400_000)
            val usageMs = usageStatsCollector.getUsageTimeMs(packageName, dayStartMs, currentTimeMillis)
            if (usageMs >= policy.dailyLimitMinutes * 60 * 1000L) {
                return PolicyAction.Suspend
            }
        }

        return PolicyAction.Monitor
    }

    override suspend fun suspendApp(packageName: String) {
        if (!deviceOwnerManager.isDeviceOwner()) return
        deviceOwnerManager.setPackagesSuspended(listOf(packageName), true)
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "SUSPEND",
                targetPackage = packageName,
                detail = "策略触发暂停"
            )
        )
    }

    override suspend fun unsuspendApp(packageName: String) {
        if (!deviceOwnerManager.isDeviceOwner()) return
        deviceOwnerManager.setPackagesSuspended(listOf(packageName), false)
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "UNSUSPEND",
                targetPackage = packageName,
                detail = "解除暂停"
            )
        )
    }

    override suspend fun resetDailyUsage() {
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "RESET_DAILY",
                targetPackage = null,
                detail = "每日使用数据重置"
            )
        )
    }

    /**
     * 判断当前时间是否在允许使用时段内。
     * 支持跨午夜（如 22:00 - 06:00 表示禁止白天使用）。
     */
    private fun isWithinAllowedTime(current: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start.isBefore(end) || start == end) {
            // 正常时段：08:00 - 22:00
            !current.isBefore(start) && !current.isAfter(end)
        } else {
            // 跨午夜：22:00 - 06:00（允许 22:00-23:59 和 00:00-06:00）
            !current.isBefore(start) || !current.isAfter(end)
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.policy.PolicyEngineImplTest"`
预期: ALL PASS

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/domain/policy/PolicyEngineImpl.kt \
  app/src/test/java/com/peerlock/domain/policy/PolicyEngineImplTest.kt
git commit -m "feat: 实现 PolicyEngine（策略评估 + 应用暂停）

- 每日时长限制检查
- 允许时段检查（支持跨午夜）
- setPackagesSuspended 暂停/解除暂停
- 审计日志记录"
```

---

## 任务 5：前台服务（PeerLockService）

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/system/service/PeerLockService.kt`
- 修改: `app/src/main/AndroidManifest.xml`（注册 Service + notification channel）
- 新建: `app/src/test/java/com/peerlock/system/service/PeerLockServiceLogicTest.kt`

- [ ] **步骤 1：编写巡检逻辑测试**

新建 `app/src/test/java/com/peerlock/system/service/PeerLockServiceLogicTest.kt`：

```kotlin
package com.peerlock.system.service

import com.peerlock.data.usage.AppUsageInfo
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyAction
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 测试 PeerLockService 的巡检逻辑（不依赖 Android Service 生命周期）。
 */
class PeerLockServiceLogicTest {

    private lateinit var policyEngine: PolicyEngine
    private lateinit var usageCollector: UsageStatsCollector
    private lateinit var storageRepo: StorageRepository
    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private lateinit var patrolLogic: PatrolLogic

    @BeforeEach
    fun setup() {
        policyEngine = mockk(relaxed = true)
        usageCollector = mockk(relaxed = true)
        storageRepo = mockk(relaxed = true)
        deviceOwnerManager = mockk(relaxed = true)
        patrolLogic = PatrolLogic(policyEngine, usageCollector, storageRepo, deviceOwnerManager)
    }

    @Test
    fun `巡检应检查所有受限应用`() = runTest {
        val policies = listOf(
            RestrictionPolicy(
                id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
            RestrictionPolicy(
                id = 2, targetPackage = "com.app2", dailyLimitMinutes = 30,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
        )
        coEvery { storageRepo.getActivePolicies() } returns policies
        every { usageCollector.getUsageTimeMs(any(), any(), any()) } returns 0L
        coEvery { policyEngine.evaluate(any(), any()) } returns PolicyAction.Monitor

        patrolLogic.executePatrol()

        coVerify { policyEngine.evaluate("com.app1", any()) }
        coVerify { policyEngine.evaluate("com.app2", any()) }
    }

    @Test
    fun `巡检遇到 Suspend 应暂停应用`() = runTest {
        val policies = listOf(
            RestrictionPolicy(
                id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
        )
        coEvery { storageRepo.getActivePolicies() } returns policies
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Suspend
        coEvery { policyEngine.suspendApp("com.app1") } just runs

        patrolLogic.executePatrol()

        coVerify { policyEngine.suspendApp("com.app1") }
    }

    @Test
    fun `巡检遇到 Monitor 不暂停`() = runTest {
        val policies = listOf(
            RestrictionPolicy(
                id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
                allowedTimeStart = null, allowedTimeEnd = null,
                isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
            ),
        )
        coEvery { storageRepo.getActivePolicies() } returns policies
        coEvery { policyEngine.evaluate("com.app1", any()) } returns PolicyAction.Monitor

        patrolLogic.executePatrol()

        coVerify(exactly = 0) { policyEngine.suspendApp(any()) }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.system.service.PeerLockServiceLogicTest"`
预期: FAIL — PatrolLogic 类未找到

- [ ] **步骤 3：创建 PeerLockService 和 PatrolLogic**

新建 `app/src/main/java/com/peerlock/system/service/PeerLockService.kt`：

```kotlin
package com.peerlock.system.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyAction
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 巡检逻辑：独立于 Android Service 生命周期，可单元测试。
 */
class PatrolLogic(
    private val policyEngine: PolicyEngine,
    private val usageCollector: UsageStatsCollector,
    private val storageRepository: StorageRepository,
    private val deviceOwnerManager: DeviceOwnerManager,
) {
    suspend fun executePatrol() {
        val now = System.currentTimeMillis()
        val policies = storageRepository.getActivePolicies()

        for (policy in policies) {
            val action = policyEngine.evaluate(policy.targetPackage, now)
            when (action) {
                is PolicyAction.Suspend -> {
                    Log.i("Patrol", "暂停应用: ${policy.targetPackage}")
                    policyEngine.suspendApp(policy.targetPackage)
                }
                is PolicyAction.Unsuspend -> {
                    Log.i("Patrol", "解除暂停: ${policy.targetPackage}")
                    policyEngine.unsuspendApp(policy.targetPackage)
                }
                is PolicyAction.Monitor -> {
                    // 正常状态，无需操作
                }
            }
        }
    }
}

/**
 * PeerLock 前台服务。
 * 每 30 秒执行一次策略巡检。
 */
@AndroidEntryPoint
class PeerLockService : Service() {

    companion object {
        private const val TAG = "PeerLockService"
        private const val CHANNEL_ID = "peerlock_service"
        private const val NOTIFICATION_ID = 1001
        private const val PATROL_INTERVAL_MS = 30_000L // 30 秒
    }

    @Inject lateinit var policyEngine: PolicyEngine
    @Inject lateinit var usageCollector: UsageStatsCollector
    @Inject lateinit var storageRepository: StorageRepository
    @Inject lateinit var deviceOwnerManager: DeviceOwnerManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var patrolJob: Job? = null
    private lateinit var patrolLogic: PatrolLogic

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(TAG, "前台服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // 初始化巡检逻辑（延迟注入，确保 Hilt 已完成）
        if (!::patrolLogic.isInitialized) {
            patrolLogic = PatrolLogic(policyEngine, usageCollector, storageRepository, deviceOwnerManager)
        }

        startPatrolLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        patrolJob?.cancel()
        serviceScope.cancel()
        Log.i(TAG, "前台服务已停止")
        super.onDestroy()
    }

    private fun startPatrolLoop() {
        patrolJob?.cancel()
        patrolJob = serviceScope.launch {
            while (isActive) {
                try {
                    patrolLogic.executePatrol()
                } catch (e: Exception) {
                    Log.e(TAG, "巡检异常: ${e.message}", e)
                }
                delay(PATROL_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PeerLock 服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "PeerLock 后台运行通知"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PeerLock 运行中")
            .setContentText("策略巡检服务正在运行")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}
```

- [ ] **步骤 4：更新 AndroidManifest.xml 注册 Service**

修改 `app/src/main/AndroidManifest.xml`，在 `<receiver>` 之前添加：

```xml
        <service
            android:name=".system.service.PeerLockService"
            android:exported="false"
            android:foregroundServiceType="specialUse" />
```

- [ ] **步骤 5：运行测试验证通过**

运行: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.system.service.PeerLockServiceLogicTest"`
预期: ALL PASS

- [ ] **步骤 6：提交**

```bash
git add app/src/main/java/com/peerlock/system/service/ \
  app/src/test/java/com/peerlock/system/service/ \
  app/src/main/AndroidManifest.xml
git commit -m "feat: 实现 PeerLockService（前台服务 + 巡检循环）

- PatrolLogic 独立巡检逻辑（可单元测试）
- 30 秒间隔策略巡检
- 前台通知 channel + 持久通知
- START_STICKY 保活"
```

---

## 任务 6：BootReceiver + DI 装配 + 最终验证

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/system/receiver/BootReceiver.kt`
- 修改: `app/src/main/AndroidManifest.xml`（注册 BootReceiver）
- 修改: `app/src/main/java/com/peerlock/di/AppModule.kt`（添加新依赖）
- 修改: `app/src/main/java/com/peerlock/data/db/DatabaseModule.kt`（提供 StorageRepository）

- [ ] **步骤 1：创建 BootReceiver**

新建 `app/src/main/java/com/peerlock/system/receiver/BootReceiver.kt`：

```kotlin
package com.peerlock.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.peerlock.system.service.PeerLockService

/**
 * 开机广播接收器。
 * 设备重启后自动启动 PeerLockService。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "设备已启动，启动 PeerLockService")
            val serviceIntent = Intent(context, PeerLockService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
```

- [ ] **步骤 2：注册 BootReceiver 到 AndroidManifest.xml**

修改 `app/src/main/AndroidManifest.xml`，在 `<receiver ... PeerLockDeviceAdminReceiver>` 之后添加：

```xml
        <receiver
            android:name=".system.receiver.BootReceiver"
            android:exported="true"
            android:directBootAware="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **步骤 3：更新 DI — 提供 StorageRepository**

修改 `app/src/main/java/com/peerlock/data/db/DatabaseModule.kt`，添加 StorageRepository 提供：

```kotlin
    @Provides
    @Singleton
    fun provideStorageRepository(
        usageRecordDao: UsageRecordDao,
        hourlySummaryDao: HourlySummaryDao,
        dailySummaryDao: DailySummaryDao,
        policyDao: RestrictionPolicyDao,
        auditLogDao: AuditLogDao,
    ): com.peerlock.domain.repository.StorageRepository =
        com.peerlock.domain.repository.StorageRepositoryImpl(
            usageRecordDao, hourlySummaryDao, dailySummaryDao, policyDao, auditLogDao
        )
```

- [ ] **步骤 4：更新 DI — 提供新组件**

修改 `app/src/main/java/com/peerlock/di/AppModule.kt`，添加：

```kotlin
import com.peerlock.data.db.dao.*
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.data.usage.UsageStatsCollectorImpl
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.PolicyEngineImpl
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.system.deviceadmin.DeviceOwnerManager
import com.peerlock.system.deviceadmin.DeviceOwnerManagerImpl
```

在 `AppModule` 中添加：

```kotlin
    @Provides @Singleton
    fun provideDeviceOwnerManager(app: android.app.Application): DeviceOwnerManager =
        DeviceOwnerManagerImpl(app.applicationContext)

    @Provides @Singleton
    fun provideUsageStatsCollector(app: android.app.Application): UsageStatsCollector =
        UsageStatsCollectorImpl(app.applicationContext)

    @Provides @Singleton
    fun providePolicyEngine(
        storageRepository: StorageRepository,
        usageStatsCollector: UsageStatsCollector,
        deviceOwnerManager: DeviceOwnerManager,
    ): PolicyEngine = PolicyEngineImpl(storageRepository, usageStatsCollector, deviceOwnerManager)
```

- [ ] **步骤 5：运行全部测试**

运行: `./gradlew test`
预期: ALL PASS

- [ ] **步骤 6：运行完整构建**

运行: `./gradlew assembleDebug`
预期: BUILD SUCCESSFUL

- [ ] **步骤 7：最终提交**

```bash
git add -A
git commit -m "chore: 第三阶段完成 — Device Owner、应用暂停、前台服务

- StorageRepository：Room DAO 桥接
- DeviceOwnerManager：DO 管理 + setPackagesSuspended
- UsageStatsCollector：使用统计采集
- PolicyEngine：策略评估（时长限制 + 时段限制）
- PeerLockService：前台服务 + 30 秒巡检
- BootReceiver：开机自启
- Hilt DI 装配"
```

---

## 第三阶段未包含的内容（延迟到后续阶段）

| 内容 | 所属阶段 | 原因 |
|------|---------|------|
| 策略引擎完整实现（黑白名单切换、解锁码验证） | 第四阶段 | 依赖 TOTP 信封 |
| 时间同步（NTP） | 第四阶段 | 需要网络模块 |
| 小时/日汇总自动聚合 | 第四阶段 | 依赖 AlarmManager |
| 使用统计前端展示 | 第七阶段 | 需要 UI |
| TOTP 信封传输协议 | 第五阶段 | 依赖加密 + 配对 |
| 紧急逃生（终止码、broadcast） | 第六阶段 | 依赖 DO + 加密 |
| 所有 UI | 第七阶段 | 依赖以上全部 |
| 数据库密码替换为 Keystore 派生 | 后续 | 当前 dev 占位符可用 |
