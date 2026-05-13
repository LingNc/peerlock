# PeerLock V3-fix 变更日志

## 概述

V3 主体功能完成后的增量修复，涵盖 12 项问题修复和 1 项功能增强。

---

## 修复项

### 1. 无线 ADB 一键设置 Device Owner（仿 Shizuku）

**文件**: `system/adb/AdbProtocol.kt`, `AdbMessage.kt`, `AdbKey.kt`, `AdbClient.kt`, `AdbPairingClient.kt`, `AdbMdns.kt`, `AdbPairingService.kt`, `Ed25519Spake2.kt`, `AdbExceptions.kt`

实现完整的轻量级 ADB 客户端，支持本机无线 ADB 配对：
- 纯 Kotlin SPAKE2 over Ed25519 实现（无 NDK 依赖）
- RSA 2048 密钥生成 + AES-GCM 加密存储
- TLS 1.3 自签名证书上下文
- NsdManager mDNS 发现 `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp`
- 前台服务 `AdbPairingService` + 通知栏 RemoteInput 输入配对码

### 2. ADB 配对码改用通知栏输入

**文件**: `system/adb/AdbPairingService.kt`, `ui/onboarding/DeviceOwnerSetupViewModel.kt`, `ui/onboarding/DeviceOwnerSetupScreen.kt`

配对码通过通知栏 RemoteInput 输入（而非应用内 AlertDialog），避免退出应用导致配对重置。ViewModel 改为观察 Service 的 StateFlow。

### 3. 扫码界面权限修复

**文件**: `ui/common/QrScannerScreen.kt`

- 添加 `rememberLauncherForActivityResult(RequestPermission())` 请求摄像头权限
- 权限拒绝时显示提示 + "授予权限"按钮 + "从相册选择"降级
- 相册图标改为 `Icons.Default.PhotoLibrary`
- 按钮位置调整：取消居左，相册图标右下角

### 4. 主题切换深浅色修复

**文件**: `MainActivity.kt`

- 添加 `SharedPreferences.OnSharedPreferenceChangeListener` 监听 `theme_mode`
- 变化时更新 `themeMode.intValue` 并调用 `recreate()`
- `PeerLockTheme(darkTheme)` 根据 ThemeMode 正确映射

### 5. DO 设置页流程修复

**文件**: `ui/onboarding/DeviceOwnerSetupScreen.kt`, `ui/onboarding/DeviceOwnerSetupViewModel.kt`, `ui/navigation/PeerLockNavHost.kt`

- `advanceToDoComplete()` 返回 Boolean：true=已豁免（调用方导航），false=需电池步骤
- 设置页 DO 路由：`showSkip = false, showRevokeDo = true`
- DO 已设置时显示"取消 Device Owner"按钮

### 6. PairingInfoScreen 崩溃修复

**文件**: `ui/settings/PairingInfoViewModel.kt`, `ui/settings/PairingInfoScreen.kt`

- TOTP 刷新循环添加 try-catch 异常保护
- `computeFingerprint` Base64 解码失败时返回 "--------" 而非回退到文本哈希
- role="" 时默认显示"申请解除"按钮兜底

### 7. 导航过渡动画

**文件**: `ui/navigation/PeerLockNavHost.kt`

NavHost 添加默认过渡动画：200ms slide-in/fade（含 pop 方向反转）。

### 8. 电池优化首次返回不刷新

**文件**: `ui/settings/SettingsViewModel.kt`

`refreshBatteryStatus()` 检测到未豁免时，延迟 500ms 再次检查（系统可能延迟更新状态）。

### 9. 编译警告消除

**文件**: `system/adb/AdbMdns.kt`, `ui/settings/SettingsScreen.kt`

- `AdbMdns`: `resolveService()` 和 `host` 弃用 API 添加 `@Suppress("DEPRECATION")`
- `SettingsScreen`: `LocalLifecycleOwner` 弃用 API 添加 `@Suppress("DEPRECATION")`

### 10. ADB 配对服务崩溃修复

**文件**: `AndroidManifest.xml`, `system/adb/AdbPairingService.kt`

- `foregroundServiceType` 从 `connectedDevice` 改为 `specialUse`（避免权限问题）
- 移除未使用的 `android.app.Notification` import

### 11. PairingInfoScreen 数据库异常崩溃防护

**文件**: `ui/settings/PairingInfoViewModel.kt`, `ui/settings/PairingInfoScreen.kt`

- `loadPairingInfo()` 添加 try-catch，数据库异常时优雅降级而非崩溃
- Screen 添加 `isLoading` 状态守卫，加载完成前显示提示文本

### 12. 跨设备配对 EC 公钥格式不兼容

**文件**: `domain/crypto/CryptoEngine.kt`, `P256CryptoEngine.kt`, `X25519CryptoEngine.kt`, `AdaptiveCryptoEngine.kt`, `domain/pairing/PairingModels.kt`, `PairingProtocolImpl.kt`, `ui/onboarding/OnboardingViewModel.kt`, `data/db/entity/PairingSessionEntity.kt`, `data/db/PeerLockDatabase.kt`, `data/pairing/PairingRepository.kt`, `PairingRepositoryImpl.kt`

- 根因：`AdaptiveCryptoEngine` 在 API 33+ 使用 X25519（32 字节公钥），API 30-32 使用 P256（65 字节公钥），跨设备配对时密钥格式不兼容
- `PairingRequest`/`PairingResponse` 新增 `curve` 字段标识公钥曲线类型
- `CryptoEngine` 接口新增 `curveName` 属性
- `PairingProtocolImpl` 使用对端曲线引擎加密/验签，自身引擎解密
- QR 码传输完整 JSON 对象（含 curve），向后兼容纯公钥格式
- `P256CryptoEngine.ecParamSpec` 改为 lazy 初始化（verify 无需 generateKeyPair）
- `PairingSessionEntity` 新增 `peerCurve` 字段 + DB Migration 2→3

---

## 新增依赖

- `org.bouncycastle:bcpkix-jdk18on` — X.509 证书生成（ADB TLS）
- `androidx.compose.material:material-icons-extended` — 扩展图标（PhotoLibrary）

## API 兼容性

- minSdk = 30 (Android 11)
- targetSdk = 35 (Android 15)
- 无线 ADB 一键设置需要 Android 11+（`Build.VERSION_CODES.R`）
- SPAKE2 配对需要 Android 11+（TLS 1.3 + Conscrypt）
