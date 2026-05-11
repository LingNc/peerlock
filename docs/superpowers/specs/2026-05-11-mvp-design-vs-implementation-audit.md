# PeerLock MVP 设计规范 vs 实现核对报告

> **对照文档**: `docs/superpowers/specs/2026-05-09-peerlock-mvp-design.md`
> **核对日期**: 2026-05-11
> **当前分支**: develop

---

## 1. 产品概述 — ✅ 完成

| 决策项 | 设计要求 | 实现状态 |
|--------|---------|---------|
| minSdk 30 | ✅ | `build.gradle.kts` 确认 |
| targetSdk 35 | ✅ | 确认 |
| Kotlin + Compose | ✅ | 确认 |
| MVVM + Hilt + Room | ✅ | 确认 |
| 侧载 testOnly=true | ✅ | `AndroidManifest.xml` 确认 |
| 单进程 + 前台服务 | ✅ | `PeerLockService` 确认 |

---

## 2. 项目结构与模块划分 — ⚠️ 基本完成，有偏差

| 设计要求 | 实现状态 |
|---------|---------|
| `domain/` 纯 Kotlin，零 Android 依赖 | ✅ |
| `data/` 只做存储 | ✅ |
| `system/` 封装系统 API | ✅ |
| `ui/` 通过 ViewModel 调 Domain | ✅ |
| `system/usagestats/` 包路径 | ⚠️ 实际在 `data/usage/`，逻辑合理但与 spec 路径不同 |

---

## 3. 系统架构总览 — ⚠️ 基本完成，有缺失

| 设计组件 | 实现状态 |
|---------|---------|
| Room/SQLCipher 存储 | ✅ |
| Android Keystore 密钥存储 | ✅ |
| EncryptedSharedPreferences | ✅ |
| DevicePolicyManager | ✅ |
| UsageStatsManager | ✅ |
| NTP Client (Google + Aliyun) | ✅ |
| Foreground Service (30s 巡检) | ✅ |
| AlarmManager 精确闹钟 | ✅ |
| BOOT_COMPLETED Receiver | ✅ |
| **CONNECTIVITY Receiver** | ❌ **缺失** — 未实现网络恢复监听 |
| **电池优化白名单** | ❌ **缺失** — 未实现 DO 自动加白名单 |

---

## 4. 配对流程设计 — ✅ 完成

| 设计要求 | 实现状态 |
|---------|---------|
| 被控端生成 ECC 密钥对 + 展示 QR | ✅ |
| 控制端扫码 + 生成 3 个 TOTP 种子 + 加密回传 | ✅ |
| 被控端解密 + 验签 + 存储种子 | ✅ |
| ECC 双后端 (X25519/P256) 在 domain 层 | ✅ `AdaptiveCryptoEngine` |
| QR 码 JSON 格式 (pair_req/pair_resp) | ✅ `PairingModels.kt` |
| 5 分钟过期 | ✅ `exp` 字段 + 校验 |
| 配对会话 ID 防重放 | ✅ |
| 设备名 `Build.MANUFACTURER + Build.MODEL` | ⚠️ Gap-filling 已标记但未提交 |

**Keystore 层 X25519 实现**：Domain 层有 `X25519CryptoEngine`（Bouncy Castle），但 `KeystoreManager`（data 层）仅支持 P-256。X25519 密钥不由 Android Keystore 管理，而是由 Bouncy Castle 内存管理。这是有意的架构选择（X25519 在 Keystore 中不直接支持），不算缺失。

---

## 5. TOTP 三密钥验证体系 — ✅ 完成

| 设计要求 | 实现状态 |
|---------|---------|
| 3 种密钥 (Admin/Unlock/Destroy) | ✅ `KeyType` 枚举 |
| HMAC-SHA1, 20 字节种子, 30s 窗口, ±1 容差, 6 位 | ✅ `TotpEngineImpl` |
| 常量时间比较 | ✅ `constantTimeEquals()` |
| 5 次错误锁定 30 秒 | ✅ 在 `PolicyEngineImpl` + `EmergencyManagerImpl` 中 |
| 控制端展示 otpauth URI | ✅ `ControllerViewModel.getOtpauthUris()` |

---

## 6. 签名信封协议 — ✅ 完成

| 设计要求 | 实现状态 |
|---------|---------|
| ECDH + AES-256-GCM + ECDSA 加密 | ✅ `EnvelopeCrypto` |
| 二进制打包 `[version][ciphertext][iv][signature]` | ✅ `EnvelopeCodec` |
| 信封有效期 5 分钟 | ✅ `verifyEnvelope` 中 maxAgeSeconds=300 |
| 防重放 (ts+sid 组合) | ✅ `RequestGuard.isConsumed/markConsumed` |

---

## 7. 双向请求/响应协议 — ✅ 完成

| 设计要求 | 实现状态 |
|---------|---------|
| RequestEnvelope 数据模型 | ✅ |
| ResponseEnvelope 数据模型 | ✅ |
| RequestPayload sealed class (Unlock/Config) | ✅ |
| ResponsePayload sealed class (Unlock/Config/Rejected) | ✅ |
| DeviceInfo 附带设备状态 | ✅（但 `todayScreenTimeMs` 硬编码 0） |
| generateUnlockRequest / generateConfigRequest | ✅ |
| generateUnlockResponse / generateConfigResponse | ✅ |
| processRequest / processResponse | ✅ |
| executeResponse (应用解锁/配置) | ✅ |
| 频率限制 30 秒/次 | ✅ `RequestGuard.RATE_LIMIT_MS = 30_000L` |
| 信封有效期 5 分钟 | ✅ |
| 解锁时长配置 (cumulative/absolute) | ✅ `EnvelopeConfig.durationMode` |

---

## 8. 策略执行与应用暂停 — ⚠️ 基本完成

| 设计要求 | 实现状态 |
|---------|---------|
| RestrictionPolicy 数据模型 | ✅ 所有字段匹配 |
| 状态机 MONITORING → SUSPENDED → UNLOCKED | ✅ |
| 巡检逻辑 (30s 间隔) | ✅ `PatrolLogic.executePatrol()` |
| setPackagesSuspended | ✅ `DeviceOwnerManagerImpl` |
| **审计日志记录状态变更** | ❌ **缺失** — 巡检中 suspend/unsuspend 未写 audit_log |

---

## 9. 时间偏移检测与安全模式 — ✅ 完成

| 设计要求 | 实现状态 |
|---------|---------|
| 自维护 offset = NTP 时间 - 系统时间 | ✅ `TimeSyncManagerImpl` |
| NTP 服务器 time.google.com + ntp.aliyun.com | ✅ |
| RTT 补偿 | ✅ |
| 每 6 小时定期同步 | ✅ `SYNC_VALIDITY_MS = 6h` |
| 偏移突变检测 (>30s) | ✅ `detectTimeMutation()` |
| 安全模式: 所有应用 SUSPEND | ✅ `PatrolLogic` 检查 safeMode |
| 安全模式: 通知提示 | ✅ 通知更新 |
| 安全模式: 网络恢复自动校验 | ⚠️ 无 CONNECTIVITY Receiver，依赖下次巡检 |
| 安全模式: 手动管理码恢复 | ✅ 通过 UI |

---

## 10. 紧急逃生通道 — ⚠️ 基本完成

| 设计要求 | 实现状态 |
|---------|---------|
| L1 终止码 (TOTP 验证 + 解绑配对保留 DO) | ✅ `EmergencyManager.executeDestroy()` |
| L1 二次确认对话框 | ✅ `EmergencyScreen` AlertDialog |
| L2 一次性 ADB broadcast (7次连点 + nonce) | ✅ |
| L2 nonce 16 位 hex + 5 分钟过期 | ✅ |
| L2 移除 DO | ✅ `deviceOwnerManager.removeDeviceOwner()` |
| L2 FLAG_SECURE 防截屏 | ✅ |
| L3 固定命令 (仅文档) | ✅ 不需要代码实现 |
| **L2 挑战码显示** | ❌ **缺失** — 设计要求先显示随机挑战码验证，再显示 ADB 命令 |

---

## 11. 前台服务与后台保活 — ⚠️ 基本完成

| 设计要求 | 实现状态 |
|---------|---------|
| 前台服务通知 | ✅ |
| 30s AlarmManager 巡检 | ✅ `setExactAndAllowWhileIdle` |
| 通知显示屏幕时间 + 受限数量 | ✅ |
| 不可滑动关闭 | ✅ `IMPORTANCE_LOW` + ONGOING |
| 点击打开 App | ✅ `contentIntent` |
| START_STICKY 重启自启 | ✅ |
| **电池优化白名单 (DO 自动加入)** | ❌ **缺失** |
| **CONNECTIVITY_ACTION 恢复监听** | ❌ **缺失** |

---

## 12. Device Owner 初始化流程 — ⚠️ 基本完成

| 设计要求 | 实现状态 |
|---------|---------|
| 角色选择后引导 DO 设置 | ✅ `DeviceOwnerSetupScreen` |
| 无线 ADB 配对引导 (仿 Shizuku) | ✅ 步骤引导 UI |
| 有线 ADB 备用路径 | ✅ 命令显示 + 复制按钮 |
| 检查 DO 状态 | ✅ |
| 跳过选项 | ✅ |
| **自动检测配对完成 → 执行 set-device-owner** | ❌ **缺失** — 仅手动指引，无自动执行 |

---

## 13. 使用统计与数据可视化 — ⚠️ 基本完成，有缺失

| 设计要求 | 实现状态 |
|---------|---------|
| 30s 巡检 → raw_records | ✅ `PatrolLogic.collectUsageRecords()` |
| 每小时 → hourly_summary | ✅ `UsageAggregator.aggregateHourly()` |
| 每日 → daily_summary | ✅ `UsageAggregator.aggregateDaily()` |
| Room 表结构匹配 spec | ⚠️ 复合主键 vs 自增 ID + UNIQUE（功能等价） |
| 小时级柱状图 | ✅ `HourlyBarChart` Canvas 绘制 |
| 日级折线图 | ✅ `DailyLineChart` Canvas 绘制 |
| 30 秒详情下钻 | ⚠️ 有数据加载逻辑，但 **Canvas 无点击交互** |
| 今天/本周/本月 Tab | ✅ |
| **launchCount 统计** | ❌ 始终写 0 |
| **审计日志 90 天自动清理** | ❌ `deleteOlderThan` 存在但未调用 |
| **用户可配置清理** | ❌ 无设置 UI |

---

## 14. 数据保留与解除流程 — ❌ 有关键缺失

| 设计要求 | 实现状态 |
|---------|---------|
| L1 终止码: 销毁加密材料 + 保留 DO + 保留数据 | ⚠️ `clearPairing()` 调用 `clear()` **会清除所有偏好包括 DO 标志和使用数据** |
| L2 高级解除: 销毁材料 + 解除 DO + 保留数据 | ⚠️ 同上问题 |
| L3 固定命令: 全清 | ✅ 不需要代码 |
| **`clearPairing()` 应选择性清除** | ❌ **关键缺失** — 当前是全量 clear |

---

## 15. 关键接口定义 — ✅ 完成

| 接口 | 实现状态 |
|------|---------|
| `CryptoEngine` | ✅ 7 个方法全部实现 |
| `TotpEngine` | ✅ generateCode/verifyCode/generateEnvelope/verifyEnvelope |
| `PolicyEngine` | ✅ evaluate/suspend/unsuspend/resetDailyUsage |
| `TimeSyncManager` | ✅ getCurrentRealTime/syncWithNtp/getStoredOffset/isInSafeMode |
| `StorageRepository` | ✅ 全部 CRUD 方法 |

---

## 16. P0 功能完成度

| # | P0 功能 | 状态 |
|---|---------|------|
| 1 | Device Owner 初始化 | ✅ |
| 2 | 防卸载、防强制停止 | ✅ |
| 3 | 应用黑白名单 + 强制暂停 | ✅ |
| 4 | 使用时长统计 (30s 粒度) | ✅ |
| 5 | 按时段限制 | ✅ |
| 6 | TOTP 三密钥验证 | ✅ |
| 7 | 签名信封协议 | ✅ |
| 8 | 双向请求/响应 | ✅ |
| 9 | 配对流程 (QR 双向扫描) | ✅ |
| 10 | 加密存储 (Keystore+SQLCipher+EncryptedSharedPrefs) | ✅ |
| 11 | 紧急逃生 - 终止码 | ✅ |
| 12 | 紧急逃生 - 一次性 broadcast | ✅ |
| 13 | 紧急逃生 - 固定命令 | ✅ |
| 14 | 角色分离 UI | ✅ |
| 15 | 时间偏移检测 | ✅ |
| 16 | 前台服务保活 | ⚠️ 缺电池优化白名单 |
| 17 | 数据可视化 | ⚠️ 图表有但点击交互未接通 |

---

## 17. 技术风险评估 — 缓解状态

| 高风险 | 缓解状态 |
|--------|---------|
| 厂商 ROM 杀后台 | ⚠️ DO + 前台服务已做，电池优化白名单未做 |
| Device Owner 行为差异 | 未测试（需要真机） |
| Keystore 不可用 | ❌ 无降级方案 |
| 系统时间被篡改 | ✅ NTP 校验 + 安全模式 |

---

## 汇总：缺失清单

### 🔴 关键问题（影响核心功能/安全）

| # | 问题 | 对应 Spec 章节 |
|---|------|---------------|
| 1 | **`clearPairing()` 全量清除**而非选择性清除，会销毁 DO 标志和用户数据 | §14.1 |
| 2 | **巡检 suspend/unsuspend 未写审计日志** | §8.3 step 4 |
| 3 | **Keystore 无降级方案**（高风险项未缓解） | §17.1 |

### 🟡 中等问题（功能不完整）

| # | 问题 | 对应 Spec 章节 |
|---|------|---------------|
| 4 | 统计图表 Canvas 无点击交互，30 秒详情下钻无法触发 | §13.3 |
| 5 | `launchCount` 始终写 0 | §13.2 |
| 6 | 审计日志 90 天自动清理未接入 | §13.2 |
| 7 | L2 紧急解除缺少挑战码验证步骤 | §10.4 |
| 8 | `todayScreenTimeMs` 硬编码 0 | §7.2 DeviceInfo |
| 9 | CONNECTIVITY Receiver 未实现 | §3, §11.2 |
| 10 | 电池优化白名单未实现 | §11.2 L3 |
| 11 | 控制端审批界面缺少时长/模式调整控件 | §7.3 |
| 12 | `Typography()` 为空默认值 | UI 细节 |

### 🟢 低优先级

| # | 问题 |
|---|------|
| 13 | QR 扫描是外部 Activity 而非内联预览 |
| 14 | `base32Encode()` 手动实现在 ViewModel 中 |
| 15 | HourlySummary/DailySummary 用复合主键而非自增 ID + UNIQUE |
| 16 | BootReceiver 无配对状态检查即启动服务 |
| 17 | `EmergencyUnlockReceiver` 无 nonce 格式校验 |
| 18 | 被控端策略卡片点击不传递 targetPackage |

---

## 结论

设计规范的 **17 个 P0 功能中 13 个完全实现，4 个部分实现**。

核心加密、配对、TOTP、策略执行、请求/响应协议均已正确落地。

最关键的 3 个问题是：
1. `clearPairing()` 的选择性清除逻辑
2. 巡检审计日志缺失
3. Keystore 降级方案缺失

其余为中等/低优先级的交互和细节问题。
