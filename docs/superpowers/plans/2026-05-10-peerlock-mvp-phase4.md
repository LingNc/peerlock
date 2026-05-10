# Phase 4: Time Sync, Safe Mode, Usage Aggregation, Policy Enhancement

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement NTP time synchronization, safe mode, usage data aggregation pipeline, and policy engine enhancements (blacklist/whitelist, unlock state, TOTP verification).

**Architecture:** TimeSyncManagerImpl uses native NTP protocol via `DatagramSocket` for direct UDP queries to NTP servers, with RTT compensation for accuracy. SafeModeManagerImpl persists state in SecurePrefs. UsageAggregator runs hourly/daily aggregation from raw records. PolicyEngine gains unlock state tracking and blacklist/whitelist differentiation.

**Tech Stack:** Kotlin 2.1.0, Hilt, Room, SecurePrefs, java.net.DatagramSocket (NTP UDP)

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `domain/security/TimeSyncManagerImpl.kt` | Create | NTP sync with RTT compensation |
| `domain/security/SafeModeManagerImpl.kt` | Create | Safe mode state machine |
| `data/usage/UsageAggregator.kt` | Create | Hourly/daily summary aggregation |
| `data/usage/UsageAggregatorImpl.kt` | Create | Implementation with Room DAOs |
| `domain/policy/PolicyEngineImpl.kt` | Modify | Blacklist/whitelist + unlock state |
| `domain/policy/PolicyAction.kt` | Modify | Add Unlock action |
| `system/service/PatrolLogic.kt` | Modify | Integrate time sync + safe mode |
| `di/AppModule.kt` | Modify | Wire new dependencies |
| `data/db/DatabaseModule.kt` | Modify | Wire UsageAggregator |
| Test files (×5) | Create | Unit tests for each new component |

---

### Task 1: TimeSyncManagerImpl

**Files:**
- Create: `app/src/main/java/com/peerlock/domain/security/TimeSyncManagerImpl.kt`
- Test: `app/src/test/java/com/peerlock/domain/security/TimeSyncManagerImplTest.kt`
- Read: `app/src/main/java/com/peerlock/domain/security/TimeSyncManager.kt` (interface)
- Read: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt` (timeOffsetMs, lastNtpSyncTimestamp)

**Design:**
- NTP server list: `time.google.com` (primary), `ntp.aliyun.com` (fallback)
- Timeout: 3 seconds per server
- RTT compensation: `offset = ntpTime + rtt/2 - queryEnd`
- System time mutation detection: `|new_offset - stored_offset| > 30_000ms`
- `syncWithNtp()` returns `SyncResult.Success(offsetMs)` or `SyncResult.Failed`
- `getCurrentRealTime()` returns `System.currentTimeMillis() + storedOffset`
- `isSystemTimeReliable()` returns `true` if last sync < 6 hours ago

- [ ] **Step 1: Write TimeSyncManagerImpl**

```kotlin
package com.peerlock.domain.security

import com.peerlock.data.prefs.SecurePrefs
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

class TimeSyncManagerImpl(
    private val securePrefs: SecurePrefs,
) : TimeSyncManager {

    companion object {
        private val NTP_SERVERS = listOf("time.google.com", "ntp.aliyun.com")
        private const val NTP_PORT = 123
        private const val TIMEOUT_MS = 3000
        private const val SYNC_VALIDITY_MS = 6 * 3600 * 1000L // 6 hours
        private const val TIME_MUTATION_THRESHOLD_MS = 30_000L // 30 seconds
        // NTP epoch offset: 1900-01-01 to 1970-01-01 in seconds
        private const val NTP_EPOCH_OFFSET = 2208988800L
    }

    override suspend fun getCurrentRealTime(): Long {
        return System.currentTimeMillis() + getStoredOffset()
    }

    override suspend fun syncWithNtp(): SyncResult {
        for (server in NTP_SERVERS) {
            val result = queryNtpServer(server)
            if (result != null) {
                val oldOffset = securePrefs.timeOffsetMs
                securePrefs.timeOffsetMs = result
                securePrefs.lastNtpSyncTimestamp = System.currentTimeMillis()
                return SyncResult.Success(result)
            }
        }
        return SyncResult.Failed
    }

    override fun getStoredOffset(): Long = securePrefs.timeOffsetMs

    override fun isSystemTimeReliable(): Boolean {
        val lastSync = securePrefs.lastNtpSyncTimestamp
        if (lastSync == 0L) return false
        return (System.currentTimeMillis() - lastSync) < SYNC_VALIDITY_MS
    }

    fun detectTimeMutation(): Boolean {
        val storedOffset = securePrefs.timeOffsetMs
        // Re-sync and compare
        val newOffset = queryNtpServer(NTP_SERVERS.first()) ?: return false
        return abs(newOffset - storedOffset) > TIME_MUTATION_THRESHOLD_MS
    }

    private fun queryNtpServer(server: String): Long? {
        return try {
            val address = InetAddress.getByName(server)
            val ntpData = ByteArray(48)
            ntpData[0] = 0x1B // NTP version 3, client mode

            val socket = DatagramSocket()
            socket.soTimeout = TIMEOUT_MS
            val queryStart = System.currentTimeMillis()

            val packet = DatagramPacket(ntpData, ntpData.size, address, NTP_PORT)
            socket.send(packet)

            val response = DatagramPacket(ByteArray(48), 48)
            socket.receive(response)
            val queryEnd = System.currentTimeMillis()
            socket.close()

            // Extract NTP timestamp from response bytes 40-47
            val responseBytes = response.data
            val seconds = ((responseBytes[40].toLong() and 0xFF) shl 24) or
                    ((responseBytes[41].toLong() and 0xFF) shl 16) or
                    ((responseBytes[42].toLong() and 0xFF) shl 8) or
                    (responseBytes[43].toLong() and 0xFF)
            val fraction = ((responseBytes[44].toLong() and 0xFF) shl 24) or
                    ((responseBytes[45].toLong() and 0xFF) shl 16) or
                    ((responseBytes[46].toLong() and 0xFF) shl 8) or
                    (responseBytes[47].toLong() and 0xFF)

            val ntpTimeMs = (seconds - NTP_EPOCH_OFFSET) * 1000 + fraction * 1000 / 0x100000000L
            val rtt = queryEnd - queryStart
            val offset = ntpTimeMs + rtt / 2 - queryEnd
            offset
        } catch (_: Exception) {
            null
        }
    }
}
```

- [ ] **Step 2: Write TimeSyncManagerImplTest**

```kotlin
package com.peerlock.domain.security

import com.peerlock.data.prefs.SecurePrefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimeSyncManagerImplTest {

    // Uses Robolectric for SecurePrefs (or mock)
    // For now test the non-network methods

    @Test
    fun `isSystemTimeReliable returns false when never synced`() {
        // securePrefs.lastNtpSyncTimestamp == 0
        // → false
    }

    @Test
    fun `isSystemTimeReliable returns true within 6 hours`() {
        // Set lastSyncTimestamp = now - 1 hour
        // → true
    }

    @Test
    fun `isSystemTimeReliable returns false after 6 hours`() {
        // Set lastSyncTimestamp = now - 7 hours
        // → false
    }

    @Test
    fun `getStoredOffset returns persisted offset`() {
        // Set timeOffsetMs = 5000
        // → 5000
    }
}
```

- [ ] **Step 3: Run tests to verify compilation**

Run: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.security.TimeSyncManagerImplTest" 2>&1 | tail -20`

Expected: Tests compile and pass (network tests will be integration-only)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/peerlock/domain/security/TimeSyncManagerImpl.kt \
       app/src/test/java/com/peerlock/domain/security/TimeSyncManagerImplTest.kt
git commit -m "feat: 实现 TimeSyncManagerImpl（NTP 时间同步 + RTT 补偿）"
```

---

### Task 2: SafeModeManagerImpl

**Files:**
- Create: `app/src/main/java/com/peerlock/domain/security/SafeModeManagerImpl.kt`
- Test: `app/src/test/java/com/peerlock/domain/security/SafeModeManagerImplTest.kt`
- Read: `app/src/main/java/com/peerlock/domain/security/SafeModeManager.kt` (interface)
- Read: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt` (needs new safe mode prefs)

**Design:**
- State persisted in SecurePrefs: `safeModeActive: Boolean`, `safeModeReason: String?`
- `enterSafeMode(reason)` — sets state, triggers audit log
- `exitSafeMode()` — clears state
- `isInSafeMode()` — reads persisted state

**Step 1 prerequisite: Add safe mode prefs to SecurePrefs**

Add to `SecurePrefs.kt`:
```kotlin
var safeModeActive: Boolean
    get() = prefs.getBoolean("safe_mode_active", false)
    set(value) = prefs.edit().putBoolean("safe_mode_active", value).apply()

var safeModeReason: String?
    get() = prefs.getString("safe_mode_reason", null)
    set(value) = prefs.edit().putString("safe_mode_reason", value).apply()
```

- [ ] **Step 1: Add safe mode fields to SecurePrefs**

- [ ] **Step 2: Write SafeModeManagerImpl**

```kotlin
package com.peerlock.domain.security

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.repository.AuditLog
import com.peerlock.domain.repository.StorageRepository

class SafeModeManagerImpl(
    private val securePrefs: SecurePrefs,
    private val storageRepository: StorageRepository,
) : SafeModeManager {

    override fun isInSafeMode(): Boolean = securePrefs.safeModeActive

    override suspend fun enterSafeMode(reason: String) {
        securePrefs.safeModeActive = true
        securePrefs.safeModeReason = reason
        storageRepository.insertAuditLog(
            AuditLog(
                timestamp = System.currentTimeMillis(),
                action = "SAFE_MODE_ENTER",
                targetPackage = null,
                detail = reason
            )
        )
    }

    override suspend fun exitSafeMode() {
        val wasActive = securePrefs.safeModeActive
        securePrefs.safeModeActive = false
        securePrefs.safeModeReason = null
        if (wasActive) {
            storageRepository.insertAuditLog(
                AuditLog(
                    timestamp = System.currentTimeMillis(),
                    action = "SAFE_MODE_EXIT",
                    targetPackage = null,
                    detail = "安全模式退出"
                )
            )
        }
    }

    override fun getSafeModeReason(): String? = securePrefs.safeModeReason
}
```

- [ ] **Step 3: Write SafeModeManagerImplTest**

Test: enter sets state, exit clears state, isInSafeMode reflects state, reason is persisted, audit logs are written.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.security.SafeModeManagerImplTest" 2>&1 | tail -20`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt \
       app/src/main/java/com/peerlock/domain/security/SafeModeManagerImpl.kt \
       app/src/test/java/com/peerlock/domain/security/SafeModeManagerImplTest.kt
git commit -m "feat: 实现 SafeModeManagerImpl（安全模式状态管理）"
```

---

### Task 3: Usage Aggregation

**Files:**
- Create: `app/src/main/java/com/peerlock/data/usage/UsageAggregator.kt` (interface)
- Create: `app/src/main/java/com/peerlock/data/usage/UsageAggregatorImpl.kt`
- Test: `app/src/test/java/com/peerlock/data/usage/UsageAggregatorImplTest.kt`
- Read: `app/src/main/java/com/peerlock/data/db/dao/UsageRecordDao.kt`
- Read: `app/src/main/java/com/peerlock/data/db/dao/HourlySummaryDao.kt`
- Read: `app/src/main/java/com/peerlock/data/db/dao/DailySummaryDao.kt`
- Read: `app/src/main/java/com/peerlock/data/prefs/SecurePrefs.kt` (rawRecordsRetentionDays)

**Design:**
- `aggregateHourly(date, hour)` — queries raw records for that hour, groups by package, upserts hourly summaries
- `aggregateDaily(date)` — queries hourly summaries for that date, groups by package, upserts daily summaries
- `cleanupOldRecords()` — deletes raw records older than `rawRecordsRetentionDays`
- Hourly called every hour on the hour; Daily called at 00:00

- [ ] **Step 1: Write UsageAggregator interface**

```kotlin
package com.peerlock.data.usage

interface UsageAggregator {
    suspend fun aggregateHourly(date: String, hour: Int)
    suspend fun aggregateDaily(date: String)
    suspend fun cleanupOldRecords()
}
```

- [ ] **Step 2: Write UsageAggregatorImpl**

```kotlin
package com.peerlock.data.usage

import com.peerlock.data.db.dao.DailySummaryDao
import com.peerlock.data.db.dao.HourlySummaryDao
import com.peerlock.data.db.dao.UsageRecordDao
import com.peerlock.data.db.entity.DailySummaryEntity
import com.peerlock.data.db.entity.HourlySummaryEntity
import com.peerlock.data.prefs.SecurePrefs
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class UsageAggregatorImpl(
    private val usageRecordDao: UsageRecordDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val dailySummaryDao: DailySummaryDao,
    private val securePrefs: SecurePrefs,
) : UsageAggregator {

    override suspend fun aggregateHourly(date: String, hour: Int) {
        val records = usageRecordDao.getByDate(date)
        // Filter records that overlap with the target hour
        val hourStartMs = dateHourToEpochMs(date, hour)
        val hourEndMs = hourStartMs + 3600_000L

        val hourlyByPackage = records
            .filter { it.endTime > hourStartMs && it.startTime < hourEndMs }
            .groupBy { it.packageName }
            .mapValues { (_, recs) ->
                recs.sumOf { rec ->
                    val overlapStart = maxOf(rec.startTime, hourStartMs)
                    val overlapEnd = minOf(rec.endTime, hourEndMs)
                    maxOf(0L, overlapEnd - overlapStart)
                }
            }

        for ((pkg, totalMs) in hourlyByPackage) {
            if (totalMs > 0) {
                hourlySummaryDao.upsert(
                    HourlySummaryEntity(
                        packageName = pkg,
                        date = date,
                        hour = hour,
                        totalMs = totalMs
                    )
                )
            }
        }
    }

    override suspend fun aggregateDaily(date: String) {
        val hourlySummaries = hourlySummaryDao.getByDate(date)
        val dailyByPackage = hourlySummaries
            .groupBy { it.packageName }
            .mapValues { (_, summaries) -> summaries.sumOf { it.totalMs } }

        for ((pkg, totalMs) in dailyByPackage) {
            dailySummaryDao.upsert(
                DailySummaryEntity(
                    packageName = pkg,
                    date = date,
                    totalMs = totalMs,
                    launchCount = 0 // Phase 7 can enhance with actual launch tracking
                )
            )
        }
    }

    override suspend fun cleanupOldRecords() {
        val retentionDays = securePrefs.rawRecordsRetentionDays
        val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        usageRecordDao.deleteOlderThan(cutoffDate)
    }

    private fun dateHourToEpochMs(date: String, hour: Int): Long {
        val dt = LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault())
        return dt.toInstant().toEpochMilli() + hour * 3600_000L
    }
}
```

- [ ] **Step 3: Write UsageAggregatorImplTest**

Test: aggregateHourly groups records by package, aggregateDaily sums hourly, cleanupOldRecords deletes old data, overlapping hour boundaries handled correctly.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.data.usage.UsageAggregatorImplTest" 2>&1 | tail -20`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/peerlock/data/usage/UsageAggregator.kt \
       app/src/main/java/com/peerlock/data/usage/UsageAggregatorImpl.kt \
       app/src/test/java/com/peerlock/data/usage/UsageAggregatorImplTest.kt
git commit -m "feat: 实现 UsageAggregator（小时/日使用量聚合）"
```

---

### Task 4: PolicyEngine Enhancement — Blacklist/Whitelist + Unlock State

**Files:**
- Modify: `app/src/main/java/com/peerlock/domain/policy/PolicyAction.kt`
- Modify: `app/src/main/java/com/peerlock/domain/policy/PolicyEngineImpl.kt`
- Modify: `app/src/main/java/com/peerlock/domain/policy/PolicyEngine.kt` (add verifyUnlockCode, recordUnlock)
- Read: `app/src/main/java/com/peerlock/domain/totp/TotpEngine.kt` (interface)
- Read: `app/src/main/java/com/peerlock/data/seed/SeedManager.kt` (retrieveSeed)
- Test: `app/src/test/java/com/peerlock/domain/policy/PolicyEngineImplTest.kt` (update existing)

**Design:**
- **Blacklist mode** (`isBlacklist=true`): listed apps are restricted; all others pass through
- **Whitelist mode** (`isBlacklist=false`): listed apps are the ONLY ones allowed; all others restricted
- **Unlock state**: stored in memory (map of packageName → unlockExpiryMs); expires automatically
- **TOTP unlock verification**: `verifyUnlockCode(code)` validates against unlock seed with ±1 window, 5 errors → 30s lockout
- `PolicyAction.Unlock(durationMs)` — new action for temporary unlock

**Step 1 prerequisite: Add Unlock to PolicyAction**

```kotlin
sealed class PolicyAction {
    data object Monitor : PolicyAction()
    data object Suspend : PolicyAction()
    data object Unsuspend : PolicyAction()
    data class Unlock(val durationMs: Long) : PolicyAction()
}
```

**Step 2 prerequisite: Add verifyUnlockCode to PolicyEngine interface**

```kotlin
interface PolicyEngine {
    suspend fun evaluate(packageName: String, currentTimeMillis: Long): PolicyAction
    suspend fun suspendApp(packageName: String)
    suspend fun unsuspendApp(packageName: String)
    suspend fun resetDailyUsage()
    suspend fun verifyUnlockCode(code: String): Boolean
    suspend fun recordUnlock(packageName: String, durationMinutes: Int)
    fun isUnlocked(packageName: String, currentTimeMillis: Long): Boolean
}
```

- [ ] **Step 1: Update PolicyAction — add Unlock**

- [ ] **Step 2: Update PolicyEngine interface — add verifyUnlockCode, recordUnlock, isUnlocked**

- [ ] **Step 3: Update PolicyEngineImpl — blacklist/whitelist logic**

In `evaluate()`, replace the current logic with:
- For `isBlacklist=true`: existing behavior (check listed apps against limits)
- For `isBlacklist=false` (whitelist): if package NOT in active policies → Suspend

Also add unlock state tracking:
```kotlin
private val unlockState = mutableMapOf<String, Long>() // package → expiry ms

override fun isUnlocked(packageName: String, currentTimeMillis: Long): Boolean {
    val expiry = unlockState[packageName] ?: return false
    return currentTimeMillis < expiry
}
```

- [ ] **Step 4: Implement verifyUnlockCode in PolicyEngineImpl**

```kotlin
override suspend fun verifyUnlockCode(code: String): Boolean {
    val now = System.currentTimeMillis()
    if (now < securePrefs.totpLockedUntil) return false // locked out

    val seed = seedManager.retrieveSeed("unlock") ?: return false
    val valid = totpEngine.verify(code, seed, windowSize = 1)
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
```

- [ ] **Step 5: Update PolicyEngineImplTest**

Add tests for:
- Blacklist mode: listed app → Suspend, unlisted → Monitor
- Whitelist mode: listed app → Monitor, unlisted → Suspend
- Unlock state: isUnlocked returns true within window, false after expiry
- TOTP unlock code: valid code → true, 5 errors → locked for 30s

- [ ] **Step 6: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.domain.policy.PolicyEngineImplTest" 2>&1 | tail -20`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/peerlock/domain/policy/PolicyAction.kt \
       app/src/main/java/com/peerlock/domain/policy/PolicyEngine.kt \
       app/src/main/java/com/peerlock/domain/policy/PolicyEngineImpl.kt \
       app/src/test/java/com/peerlock/domain/policy/PolicyEngineImplTest.kt
git commit -m "feat: 策略引擎增强（黑白名单切换 + 解锁状态 + TOTP 验证）"
```

---

### Task 5: PatrolLogic Enhancement — Time Sync + Safe Mode Integration

**Files:**
- Modify: `app/src/main/java/com/peerlock/system/service/PatrolLogic.kt`
- Test: `app/src/test/java/com/peerlock/system/service/PatrolLogicTest.kt` (update existing)

**Design:**
- Patrol now includes:
  1. Check time sync reliability → if unreliable, enter safe mode
  2. If in safe mode → suspend ALL restricted apps (including UNLOCKED ones)
  3. For each policy, evaluate and apply action (existing logic)
  4. Collect and store usage stats (existing from Phase 3)
  5. Trigger hourly aggregation if on the hour

- [ ] **Step 1: Update PatrolLogic**

```kotlin
class PatrolLogic(
    private val policyEngine: PolicyEngine,
    private val usageCollector: UsageStatsCollector,
    private val storageRepository: StorageRepository,
    private val deviceOwnerManager: DeviceOwnerManager,
    private val timeSyncManager: TimeSyncManager,
    private val safeModeManager: SafeModeManager,
    private val usageAggregator: UsageAggregator,
) {
    suspend fun executePatrol() {
        val now = System.currentTimeMillis()

        // 1. Check time reliability
        if (!timeSyncManager.isSystemTimeReliable()) {
            val syncResult = timeSyncManager.syncWithNtp()
            if (syncResult is SyncResult.Failed && !safeModeManager.isInSafeMode()) {
                safeModeManager.enterSafeMode("NTP 同步失败，系统时间不可靠")
            }
        } else if (safeModeManager.isInSafeMode()) {
            // Time is reliable again, exit safe mode
            val syncResult = timeSyncManager.syncWithNtp()
            if (syncResult is SyncResult.Success) {
                safeModeManager.exitSafeMode()
            }
        }

        // 2. Safe mode → suspend all
        if (safeModeManager.isInSafeMode()) {
            val policies = storageRepository.getActivePolicies()
            for (policy in policies) {
                policyEngine.suspendApp(policy.targetPackage)
            }
            return
        }

        // 3. Normal patrol
        val policies = storageRepository.getActivePolicies()
        for (policy in policies) {
            if (policyEngine.isUnlocked(policy.targetPackage, now)) continue
            val action = policyEngine.evaluate(policy.targetPackage, now)
            when (action) {
                is PolicyAction.Suspend -> policyEngine.suspendApp(policy.targetPackage)
                is PolicyAction.Unsuspend -> policyEngine.unsuspendApp(policy.targetPackage)
                is PolicyAction.Unlock -> { /* handled by verifyUnlockCode */ }
                is PolicyAction.Monitor -> { /* no action needed */ }
            }
        }

        // 4. Collect usage stats (Phase 3 logic, already in service)
        // 5. Hourly aggregation check (handled by AlarmManager, not in patrol)
    }
}
```

- [ ] **Step 2: Update PatrolLogicTest**

Update constructor calls with new dependencies, add tests for:
- Safe mode entered when NTP fails and not reliable
- Safe mode exited when NTP succeeds
- Safe mode suspends ALL apps
- Unlocked apps skipped during normal patrol

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.peerlock.system.service.PatrolLogicTest" 2>&1 | tail -20`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/peerlock/system/service/PatrolLogic.kt \
       app/src/test/java/com/peerlock/system/service/PatrolLogicTest.kt
git commit -m "feat: 巡检逻辑增强（时间同步检查 + 安全模式集成）"
```

---

### Task 6: DI Wiring + PeerLockService Integration

**Files:**
- Modify: `app/src/main/java/com/peerlock/di/AppModule.kt`
- Modify: `app/src/main/java/com/peerlock/data/db/DatabaseModule.kt`
- Modify: `app/src/main/java/com/peerlock/system/service/PeerLockService.kt`

**Design:**
- Wire `TimeSyncManagerImpl`, `SafeModeManagerImpl`, `UsageAggregatorImpl` via Hilt
- Update PeerLockService to use enhanced PatrolLogic with all new dependencies
- Add AlarmManager scheduling for hourly/daily aggregation triggers

- [ ] **Step 1: Update AppModule — add TimeSyncManager, SafeModeManager providers**

```kotlin
@Provides @Singleton
fun provideTimeSyncManager(securePrefs: SecurePrefs): TimeSyncManager =
    TimeSyncManagerImpl(securePrefs)

@Provides @Singleton
fun provideSafeModeManager(
    securePrefs: SecurePrefs,
    storageRepository: StorageRepository,
): SafeModeManager = SafeModeManagerImpl(securePrefs, storageRepository)

@Provides @Singleton
fun providePolicyEngine(
    storageRepository: StorageRepository,
    usageStatsCollector: UsageStatsCollector,
    deviceOwnerManager: DeviceOwnerManager,
    seedManager: SeedManager,
    totpEngine: TotpEngine,
    securePrefs: SecurePrefs,
): PolicyEngine = PolicyEngineImpl(
    storageRepository, usageStatsCollector, deviceOwnerManager,
    seedManager, totpEngine, securePrefs
)
```

- [ ] **Step 2: Update DatabaseModule — add UsageAggregator provider**

```kotlin
@Provides
@Singleton
fun provideUsageAggregator(
    usageRecordDao: UsageRecordDao,
    hourlySummaryDao: HourlySummaryDao,
    dailySummaryDao: DailySummaryDao,
    securePrefs: SecurePrefs,
): UsageAggregator = UsageAggregatorImpl(
    usageRecordDao, hourlySummaryDao, dailySummaryDao, securePrefs
)
```

- [ ] **Step 3: Update PeerLockService — use enhanced PatrolLogic**

Update constructor injection to include new dependencies. Add AlarmManager setup for hourly aggregation trigger.

- [ ] **Step 4: Run full test suite**

Run: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest 2>&1 | tail -30`

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/peerlock/di/AppModule.kt \
       app/src/main/java/com/peerlock/data/db/DatabaseModule.kt \
       app/src/main/java/com/peerlock/system/service/PeerLockService.kt
git commit -m "feat: DI 装配 + Service 集成（Phase 4 完成）"
```

---

## Verification

After all tasks complete:

1. **Unit tests**: `./gradlew :app:testDebugUnitTest` — all tests pass
2. **Build**: `./gradlew :app:assembleDebug` — no compilation errors
3. **Key behaviors to verify**:
   - NTP sync: queries time.google.com, stores offset with RTT compensation
   - Safe mode: entered on NTP failure, suspends all apps, exits on sync recovery
   - Aggregation: raw records → hourly summary → daily summary pipeline
   - Policy engine: blacklist/whitelist differentiation, unlock state with expiry, TOTP code verification
   - Patrol: checks time reliability, respects safe mode, skips unlocked apps
