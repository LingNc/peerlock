# PeerLock V4 变更日志

## 概述

V3-fix 后续修复 + 日志调试系统。涵盖 2 项问题修复和 1 项完整功能新增。

---

## 修复项

### 1. 前台服务通知持久化 + 后台重启

**文件**: `system/service/PeerLockService.kt`

- 通知渠道 `IMPORTANCE_LOW` → `IMPORTANCE_DEFAULT`（低优先级在模拟器/部分 ROM 被折叠不可见）
- 添加 `onTaskRemoved()` — 进程被杀后重新调度 AlarmManager 巡检
- 通知添加 `setAutoCancel(false)` 防止误触消失

### 2. ADB 配对服务进程清理

**文件**: `system/adb/AdbPairingService.kt`

- 添加 `onTaskRemoved()` — 取消配对并停止服务，避免残留状态

---

## 新增功能：日志调试系统

### 3. 核心日志器 PeerLockLogger

**新建**: `system/log/PeerLockLogger.kt`, `system/log/LogEntry.kt`

- 环形缓冲区 500 条日志（`synchronized` 线程安全）
- 标准日志方法：`v()` / `d()` / `i()` / `w()` / `e()`
- 加密专用方法：`dCrypto()` — 仅高级调试模式可见
- **敏感信息过滤**（`filterSensitive`）：
  - 键名含 `key/seed/token/password/secret/passphrase/private/pub/signPub` → 值替换为 `***`
  - Base64 长字符串 (>50 字符) → `[Base64:N chars]`
- **加密内容过滤**（`filterCrypto`）：
  - 非高级模式：Base64 串 (>20 字符) → `[encrypted:N chars]`
  - 高级模式：原文显示
- `formatTimestamp()` — `HH:mm:ss.SSS` 格式

### 4. 全局替换 android.util.Log

**修改**: 6 个文件

| 文件 | 变更 |
|------|------|
| `system/service/PeerLockService.kt` | `Log.i/e/w` → `PeerLockLogger.i/e/w` |
| `system/adb/AdbPairingService.kt` | 同上 |
| `system/adb/AdbMdns.kt` | 同上 |
| `system/receiver/BootReceiver.kt` | 同上 |
| `data/db/DatabaseModule.kt` | `Log.w` → `PeerLockLogger.w` |
| `ui/onboarding/DeviceOwnerSetupViewModel.kt` | 移除未使用的 `import android.util.Log` |

### 5. 日志查看页面

**新建**: `ui/settings/LogScreen.kt`, `ui/settings/LogViewModel.kt`

UI 组件：
- TopAppBar: "调试日志" + 返回按钮
- 启用调试日志 Switch
- 高级调试模式 Switch（仅 `l2Unlocked` 时显示，版本号连点 7 次解锁，5 分钟窗口）
- 级别过滤 FilterChip 行：ALL / V / D / I / W / E
- 日志列表 LazyColumn：时间 + 级别彩色标签 + Tag + 内容（等宽字体）
- 操作按钮：刷新 / 清除 / 复制到剪贴板
- ERROR 级别日志行红色背景高亮

ViewModel 逻辑：
- `isAdvanced` 标记的日志在高级模式关闭时自动隐藏
- `refreshLogs()` 支持级别过滤 + 高级模式过滤
- `copyToClipboard()` 格式化全部可见日志

### 6. 设置入口 + 导航路由

**修改**: `ui/settings/SettingsScreen.kt`, `ui/navigation/PeerLockNavHost.kt`

- 设置页新增 "调试日志" 行 → 导航到 LogScreen
- `Routes.LOG = "log"` + `?l2={l2}` 参数传递高级解锁状态
- SettingsScreen 新增 `onNavigateToLog` / `onL2UnlockedChange` 回调
- `LaunchedEffect(uiState.l2Unlocked)` 向上传递解锁状态

### 7. DEV_DEBUG BuildConfig

**修改**: `app/build.gradle.kts`, `PeerLockApp.kt`

- `buildFeatures { buildConfig = true }` 启用 BuildConfig 生成
- `buildConfigField("boolean", "DEV_DEBUG", "true")` 开发调试标志
- `PeerLockApp.onCreate()` 中 `if (BuildConfig.DEV_DEBUG) PeerLockLogger.setEnabled(true)` 自动启用日志

### 8. UI 导航图更新

**修改**: `docs/UI导航图.dot`

- 共用页面子图新增 `LOG` 节点（调试日志页）
- 设置页 → 调试日志 连线

---

## 提交记录

| 提交 | 内容 |
|------|------|
| `47ecbc8` | fix: 前台服务通知持久化 + onTaskRemoved 后台重启 |
| `296319a` | feat: 日志核心 PeerLockLogger — 环形缓冲区+敏感信息过滤+高级调试模式 |
| `45eccdb` | refactor: 全局替换 android.util.Log → PeerLockLogger |
| `0bdb75d` | feat: 新增日志查看页面 LogScreen + LogViewModel |
| `a6b5618` | feat: 日志系统集成 — 设置入口 + 导航路由 + DEV_DEBUG BuildConfig + 导航图 |

---

## 修改文件汇总

### 新建文件
| 文件 | 说明 |
|------|------|
| `system/log/LogEntry.kt` | LogLevel 枚举 + LogEntry 数据类 |
| `system/log/PeerLockLogger.kt` | 全局日志器（环形缓冲区 + 敏感过滤） |
| `ui/settings/LogScreen.kt` | 日志查看 Compose UI |
| `ui/settings/LogViewModel.kt` | 日志页 ViewModel |

### 修改文件
| 文件 | 变更 |
|------|------|
| `system/service/PeerLockService.kt` | IMPORTANCE_DEFAULT + onTaskRemoved + Log 替换 |
| `system/adb/AdbPairingService.kt` | onTaskRemoved + Log 替换 |
| `system/adb/AdbMdns.kt` | Log 替换 |
| `system/receiver/BootReceiver.kt` | Log 替换 |
| `data/db/DatabaseModule.kt` | Log 替换 |
| `ui/onboarding/DeviceOwnerSetupViewModel.kt` | 移除未使用 Log import |
| `ui/settings/SettingsScreen.kt` | 新增调试日志入口行 + onNavigateToLog 回调 |
| `ui/navigation/PeerLockNavHost.kt` | LOG 路由 + LogScreen composable |
| `app/build.gradle.kts` | BuildConfig + DEV_DEBUG 字段 |
| `PeerLockApp.kt` | DEV_DEBUG 时自动启用日志 |
| `docs/UI导航图.dot` | 新增日志页节点 |

---

## API 兼容性

- minSdk = 30 (Android 11)
- targetSdk = 35 (Android 15)
- 日志系统完全兼容 Android 11+，无版本限制
- DEV_DEBUG 为开发标志，正式发布时改 `false`

## 验证

```bash
JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk ./gradlew assembleDebug testDebugUnitTest
```

真机验证:
1. 设置页 → 调试日志 → 日志列表有内容（DEV_DEBUG 自动启用）
2. 版本号连点 7 次 → 日志页出现高级调试模式开关
3. 开启高级调试 → dCrypto 日志显示加密原文
4. 禁用日志 → 列表不再增长
5. 前台服务通知不可被滑动移除
6. 进程被杀后服务自动重启（AlarmManager 重新调度）
