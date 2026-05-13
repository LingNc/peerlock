# PeerLock V3 增量修复计划

## Context

V3 主体功能已实现完毕，真机验证中发现 9 个 UX/功能问题需要修复。本计划逐一分析问题根因并给出修复方案。

---

## 问题清单与修复方案

### Fix 1: 无线 ADB 一键设置 Device Owner（仿 Shizuku）

**问题**: 当前 DO 设置页的无线 ADB 引导描述的是"在电脑上执行 adb pair + adb shell dpm"的流程。用户希望像 Shizuku 那样在设备端直接完成无线 ADB 配对并执行 DO 命令（无需电脑）。

**方案**: 实现完整的轻量级 ADB 客户端，参考 `temp/Shizuku` 中的实现。

**1.1 ADB 客户端模块** — 新建 `system/adb/` 目录

| 文件 | 职责 |
|------|------|
| `AdbProtocol.kt` | ADB 协议常量（A_CNXN/A_AUTH/A_OPEN/A_OKAY/A_WRTE/A_CLSE/A_STLS） |
| `AdbMessage.kt` | ADB 消息结构：24 字节 header + payload，CRC32 校验 |
| `AdbKey.kt` | RSA 2048 密钥对生成/存储（AES-GCM 加密 SharedPreferences），ADB 公钥编码（524 字节小端格式），TLS 上下文（自签名 X.509 证书 + trust-all TrustManager） |
| `AdbClient.kt` | TCP 连接 → TLS 升级（Android 11+）→ RSA 签名认证 → `shell:dpm set-device-owner ...` 命令执行 |
| `AdbPairingClient.kt` | Android 11+ SPAKE2 配对：TLS 连接 → SPAKE2 握手 → AES-128-GCM 加密交换 RSA 公钥 |
| `AdbMdns.kt` | NsdManager 发现本机 `_adb-tls-connect._tcp` 和 `_adb-tls-pairing._tcp` 服务 |
| `AdbPairingService.kt` | 前台服务：mDNS 发现 → 通知栏 RemoteInput 输入配对码 → 执行配对 |

**1.2 JNI Native 代码** — SPAKE2 实现
- 参考 `temp/Shizuku/manager/src/main/jni/adb_pairing.cpp`
- 使用 OpenSSL 的 SPAKE2 API（`SPAKE2_CTX_new`、`SPAKE2_generate_msg`、`SPAKE2_process_msg`）
- HKDF 派生 AES-128-GCM 密钥
- JNI 接口：`nativeSpake2Create`、`nativeSpake2GenerateMsg`、`nativeSpake2ProcessMsg`、`nativeSpake2Encrypt`、`nativeSpake2Decrypt`
- 需要在 `CMakeLists.txt` 中链接 OpenSSL

**1.3 新增依赖**
- `app/build.gradle.kts`: 添加 Conscrypt 依赖（TLS 1.3 支持）
- `CMakeLists.txt`: 新建，配置 JNI + OpenSSL 编译
- `app/build.gradle.kts`: 添加 `externalNativeBuild` 配置
- OpenSSL: 需要预编译的 Android 静态库或使用系统 BoringSSL

**1.4 DO 设置页改造**
- DeviceOwnerSetupScreen 新增「一键设置 Device Owner」按钮
- 流程：检测无线调试是否开启 → mDNS 发现 → 启动配对服务 → 用户输入配对码 → 自动连接 + 执行 DO 命令 → 检查结果 → 显示成功/失败
- 保留手动 ADB 命令作为降级方案
- DeviceOwnerSetupViewModel 新增 `startAutoSetup()`、`onPairingCodeEntered()`、`checkResult()` 等方法

**1.5 AndroidManifest.xml**
- 新增权限：`FOREGROUND_SERVICE_CONNECTED_DEVICE`（Android 14+ 配对服务）
- 新增 `AdbPairingService` 声明

**涉及文件**:
- 新建: `system/adb/AdbProtocol.kt`, `AdbMessage.kt`, `AdbKey.kt`, `AdbClient.kt`, `AdbPairingClient.kt`, `AdbMdns.kt`, `AdbPairingService.kt`
- 新建: `src/main/jni/adb_pairing.cpp`, `CMakeLists.txt`
- 修改: `ui/onboarding/DeviceOwnerSetupScreen.kt` — 重写无线 ADB 引导 UI + 一键设置按钮
- 修改: `ui/onboarding/DeviceOwnerSetupViewModel.kt` — 新增自动设置逻辑
- 修改: `app/build.gradle.kts` — 添加 Conscrypt + JNI 配置
- 修改: `AndroidManifest.xml` — 新增服务声明和权限

---

### Fix 2: 电池优化设置后状态不刷新

**问题**: `SettingsViewModel.refreshBatteryStatus()` 已定义但从未被调用。用户从系统电池设置返回后，页面状态不会更新。另外当电池优化已关闭时点击行会闪一下（无操作但触发了 clickable）。

**方案**:
- SettingsScreen 添加 `DisposableEffect` 监听 `ON_RESUME` 生命周期事件，调用 `viewModel.refreshBatteryStatus()`
- 电池优化行：已关闭时设置 `enabled = false`（不响应点击）或用 `if` 条件控制 clickable

**涉及文件**:
- 修改: `ui/settings/SettingsScreen.kt` — 添加 onResume 刷新 + 修复已关闭时点击闪烁
- 已有方法: `SettingsViewModel.refreshBatteryStatus()` 无需修改

---

### Fix 3: 主题深色/浅色切换无效

**问题**: `PeerLockTheme` 的 `darkTheme` 参数默认为 `isSystemInDarkTheme()`，完全忽略 SettingsViewModel 中用户选择的 `ThemeMode`。主题切换除了持久化值外无实际效果。

**方案**:
- `MainActivity` 中从 `SharedPreferences("peerlock_settings")` 读取 `theme_mode` 值
- 根据 ThemeMode 决定 `darkTheme` 参数：`SYSTEM` → `isSystemInDarkTheme()`，`LIGHT` → `false`，`DARK` → `true`
- 需要在 `setContent` 中使用 `remember` + 读取 prefs，或使用 `Flow` 监听变化

**涉及文件**:
- 修改: `MainActivity.kt` — 读取 theme_mode 并传递给 PeerLockTheme
- 修改: `ui/theme/Theme.kt` — PeerLockTheme 接受 `themeMode` 参数（可选，或在 MainActivity 层解决）

---

### Fix 4: DO 设置成功后「继续」按钮无反应

**问题**: 从设置页进入 `DEVICE_OWNER_SETUP_SETTINGS` 路由时，`onContinue = { navController.popBackStack() }`。但当 DO 检测成功后点击「继续」调用 `advanceToDoComplete()` → 如果电池已豁免则标记完成，然后调用 `onContinue()` → `popBackStack()`。问题可能是：(1) 导航栈为空导致 popBackStack 无效；(2) `advanceToDoComplete` 中 `checkBatteryOptimization()` 同步更新状态后立即读取，存在时序问题。

**方案**:
- `DeviceOwnerSetupScreen` 的 `onContinue` 回调在设置路由中改为 `navController.popBackStack()` 确保有效（检查当前栈状态）
- `advanceToDoComplete()` 改为将电池检查结果存入局部变量再判断
- 从设置页进入时不需要电池优化步骤（那是 onboarding 流程），DO 设置完成后直接 popBackStack

**涉及文件**:
- 修改: `ui/navigation/PeerLockNavHost.kt` — `DEVICE_OWNER_SETUP_SETTINGS` 的 onContinue 确保可靠返回
- 修改: `ui/onboarding/DeviceOwnerSetupViewModel.kt` — 修复 `advanceToDoComplete()` 时序

---

### Fix 5: 设置页 DO 行移除「跳过」按钮

**问题**: 从设置页进入 DO 设置界面时显示「跳过（功能受限）」按钮。设置页的 DO 界面不需要跳过——用户可以直接按返回键退出。

**方案**:
- `PeerLockNavHost` 中 `DEVICE_OWNER_SETUP_SETTINGS` 路由已设置 `showSkip = false`（已正确）
- 但还需确认 `DEVICE_OWNER_SETUP`（onboarding 路由）在被控端角色选择后进入时也是 `showSkip = false`（当前已正确）
- 验证当前代码是否已正确处理

**涉及文件**: 验证即可，可能无需修改

---

### Fix 6: DO 未设置时点击「检查设置状态」无反馈

**问题**: `checkDeviceOwnerStatus()` 在 DO 未设置时设置 `statusMessage = "Device Owner 未设置，请先在电脑上执行命令"`。但 init 时已调用过一次，用户再次点击时如果状态没变化，Compose 不会重组（值没变）。实际上状态值是有更新的（`isChecked = true`），所以应该有反馈。

**进一步分析**: 可能的问题是 `statusMessage` 在 init 时已设置，再次点击时值相同，`_uiState.value.copy()` 产生新对象但内容相同，Compose 的 `collectAsState` 可能不触发重组。

**方案**:
- 每次点击「检查设置状态」时先清空 `statusMessage = null`，再设置新值（强制触发重组）
- 或者改为：先设置 `statusMessage = "正在检查..."`（带加载状态），再异步检查并更新结果

**涉及文件**:
- 修改: `ui/onboarding/DeviceOwnerSetupViewModel.kt` — checkDeviceOwnerStatus() 先清空再设置

---

### Fix 7: 「取消 Device Owner」移入 DO 详情页

**问题**: 当前「取消 Device Owner」红色按钮直接显示在设置页底部。用户希望它在 DO 行的详情页中——当 DO 已设置时，点击 DO 行进入一个界面显示「已设置」状态，附带「取消 Device Owner」按钮。

**方案**:
- 新建 `DeviceOwnerDetailScreen`（或复用现有 DO 设置页，根据 DO 状态显示不同内容）
- 设置页 DO 行点击：始终跳转 DO 设置/详情页
- DO 已设置时：详情页显示「已设置」状态 + 「取消 Device Owner」红色按钮 → 跳转 RevokeDoScreen
- DO 未设置时：详情页显示当前的 ADB 引导流程
- 从设置页移除底部的「取消 Device Owner」TextButton
- 导航：移除 `onNavigateToRevokeDo` 回调（改为从 DO 详情页内部导航）

**涉及文件**:
- 修改: `ui/settings/SettingsScreen.kt` — 移除「取消 DO」按钮，DO 行始终跳转 DO 详情
- 修改: `ui/onboarding/DeviceOwnerSetupScreen.kt` — DO 已设置时显示「取消 DO」入口
- 修改: `ui/navigation/PeerLockNavHost.kt` — 调整路由回调
- 修改: `docs/UI导航图.dot` — 更新导航图

---

### Fix 8: PairingInfoScreen 交互逻辑修复

**问题**: 用户反馈之前崩溃过（现在不复现），但交互逻辑有问题：`deleteSeedsForSession` 无确认直接执行；`computeFingerprint` 对 Base64 文本而非解码字节做哈希。

**方案**:
- `deleteSeedsForSession` 添加确认对话框（AlertDialog）
- `computeFingerprint` 修复为先 Base64 解码再 SHA-256
- TOTP 刷新循环添加 `onCleared()` 取消机制（或使用 `viewModelScope` 自动取消——已使用，但 while 循环需检查 isActive）

**涉及文件**:
- 修改: `ui/settings/PairingInfoScreen.kt` — 添加删除确认对话框
- 修改: `ui/settings/PairingInfoViewModel.kt` — 修复 computeFingerprint + TOTP 循环

---

### Fix 9: deprecated clearDeviceOwnerApp 警告

**问题**: `DeviceOwnerManagerImpl.removeDeviceOwner()` 调用的 `dpm.clearDeviceOwnerApp()` 在 API 34 已弃用，公共 SDK 中无替代方法。

**方案**: 添加 `@Suppress("DEPRECATION")` 注解。公共 SDK 中无未弃用的替代品。

**涉及文件**:
- 修改: `system/deviceadmin/DeviceOwnerManagerImpl.kt` — 添加 @Suppress

---

## 补充: 应用自启动与后台保活设计

**当前架构**（已实现）:
- `BootReceiver` 监听 `BOOT_COMPLETED`，配对后自动启动 `PeerLockService`
- `PeerLockService` 是前台服务（`foregroundServiceType="specialUse"`），返回 `START_STICKY`
- `AlarmManager.setExactAndAllowWhileIdle` 每 30 秒调度巡检
- 已声明 `RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE`、`SCHEDULE_EXACT_ALARM` 权限
- 已请求电池优化豁免

**当前不足**:
- 用户手动杀死进程后，`START_STICKY` 在部分厂商 ROM 上不生效
- 没有 `onTaskRemoved()` 处理
- 没有 WorkManager/JobScheduler 作为 AlarmManager 的备份
- 没有 Device Owner 的 `setKeepUninstalledPackages` 或 `setLockTaskMode` 等系统级保活

**建议增强**（后续迭代，不在本次修复范围）:
1. 添加 `onTaskRemoved()` 重新调度 AlarmManager
2. 添加 WorkManager `PeriodicWorkRequest` 作为二级保活（每 15 分钟检查服务是否存活）
3. DO 权限下可使用 `setLockTaskMode` 或 `setAlwaysOnVpnPackage` 等系统级机制增强保活
4. 引导用户将 PeerLock 加入厂商白名单（MIUI 自启动、华为后台保护等）

---

## 修改文件汇总

### 新建文件（ADB 客户端模块）
| 文件 | 说明 |
|------|------|
| `system/adb/AdbProtocol.kt` | ADB 协议常量 |
| `system/adb/AdbMessage.kt` | ADB 消息结构 |
| `system/adb/AdbKey.kt` | RSA 密钥管理 + TLS 上下文 |
| `system/adb/AdbClient.kt` | ADB 连接 + shell 命令执行 |
| `system/adb/AdbPairingClient.kt` | SPAKE2 配对客户端 |
| `system/adb/AdbMdns.kt` | mDNS 服务发现 |
| `system/adb/AdbPairingService.kt` | 配对前台服务 |
| `src/main/jni/adb_pairing.cpp` | JNI SPAKE2 native 实现 |
| `CMakeLists.txt` | JNI 编译配置 |

### 修改文件
| 文件 | 变更类型 |
|------|----------|
| `ui/onboarding/DeviceOwnerSetupScreen.kt` | 重写无线 ADB 引导 + 一键设置 + DO 已设置时显示取消入口 |
| `ui/onboarding/DeviceOwnerSetupViewModel.kt` | 修复 advanceToDoComplete 时序 + 自动设置逻辑 |
| `ui/settings/SettingsScreen.kt` | onResume 刷新电池状态 + 移除底部取消 DO 按钮 + 修复电池行闪烁 |
| `ui/settings/PairingInfoScreen.kt` | 添加删除确认对话框 |
| `ui/settings/PairingInfoViewModel.kt` | 修复 computeFingerprint |
| `MainActivity.kt` | 读取 theme_mode 传递给 PeerLockTheme |
| `ui/navigation/PeerLockNavHost.kt` | 调整 DO 设置路由回调 |
| `system/deviceadmin/DeviceOwnerManagerImpl.kt` | @Suppress("DEPRECATION") |
| `app/build.gradle.kts` | 添加 Conscrypt + JNI 配置 |
| `AndroidManifest.xml` | 新增 AdbPairingService + 权限 |
| `docs/UI导航图.dot` | 更新 DO 设置相关导航 |

## 验证

```bash
JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk ./gradlew assembleDebug testDebugUnitTest
```

真机验证:
1. 设置页主题切换即时生效
2. 电池优化设置后返回自动刷新状态
3. DO 未设置时检查按钮有提示
4. DO 已设置时点击 DO 行进入详情页，可取消 DO
5. 配对信息页删除种子需确认
6. 无线 ADB 引导改为本机操作风格
