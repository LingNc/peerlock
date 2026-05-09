# PeerLock MVP Design Spec

> **Version**: 1.0
> **Date**: 2026-05-09
> **Status**: Draft - Awaiting Review
> **Scope**: MVP 单向控制版本，架构预留对等模式

---

## 1. 产品概述

### 1.1 产品愿景

一款 Android 应用，实现两人之间的对等屏幕时间管理。双方可以互相施加应用使用限制，任何修改限制的操作都需要对方提供基于时间的一次性验证码（TOTP），从而实现真正意义上的"共同约束"。

### 1.2 MVP 范围

单向控制流（一个控制端、一个被控端），底层架构预留双向关系存储与多密钥管理能力，确保后续升级为对等模式时核心加密和验证逻辑无需重写。

### 1.3 目标用户场景

- 情侣/朋友互相监督减少手机沉迷
- 家长与青少年子女（替代系统级家长控制）
- 自我管控辅助（一人主动交出控制权，另一人协助监督）

### 1.4 关键技术决策

| 决策项 | 结论 |
|--------|------|
| TOTP 生成 | 嵌入 PeerLock 控制端 App 内 |
| 配对传输 | 静态 QR 码（预留动态扩展） |
| 应用形态 | 单 APK，首次启动选角色 |
| minSdk | 30 (Android 11) |
| targetSdk | 35 |
| 技术栈 | Kotlin + Jetpack Compose |
| 架构模式 | MVVM + Hilt + Room |
| DO 初始化 | 无线 ADB + 有线 ADB 双通道 |
| ECC 曲线 | 双后端：API 33+ X25519 / API 30-32 secp256r1 |
| 进程模型 | 单进程 + 前台服务 |
| 安装方式 | 侧载（testOnly=true） |
| 数据序列化 | JSON（MVP），P1 考虑二进制 TLV |

---

## 2. 项目结构与模块划分

```
peerlock/
├── app/                          # 主模块
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # testOnly="true"
│   │   ├── java/com/peerlock/
│   │   │   ├── PeerLockApp.kt           # Application，DeviceOwner 持有者
│   │   │   ├── MainActivity.kt          # 单 Activity，Compose 入口
│   │   │   ├── di/                       # Hilt 依赖注入
│   │   │   ├── ui/                       # Compose UI
│   │   │   │   ├── onboarding/           # 首次启动、角色选择、配对流程
│   │   │   │   ├── controller/           # 控制端界面
│   │   │   │   ├── controlled/           # 被控端界面
│   │   │   │   ├── common/               # 共享组件（TOTP 输入、图表等）
│   │   │   │   └── theme/
│   │   │   ├── domain/                   # 业务逻辑层（纯 Kotlin，无 Android 依赖）
│   │   │   │   ├── pairing/              # 配对协议（密钥生成、加解密）
│   │   │   │   ├── totp/                 # TOTP 生成与验证（RFC 6238）
│   │   │   │   ├── policy/               # 策略引擎（时长计算、时段判断）
│   │   │   │   └── security/             # 时间偏移检测、安全模式
│   │   │   ├── data/                     # 数据层
│   │   │   │   ├── db/                   # Room + SQLCipher
│   │   │   │   ├── keystore/             # Android Keystore 封装
│   │   │   │   └── prefs/                # EncryptedSharedPreferences
│   │   │   └── system/                   # 系统服务层
│   │   │       ├── deviceadmin/          # DeviceAdminReceiver + 策略执行
│   │   │       ├── usagestats/           # UsageStatsManager 封装
│   │   │       └── service/              # 前台服务（策略巡检 + 时间监控）
│   │   └── res/
│   └── build.gradle.kts
├── docs/
├── build.gradle.kts               # 根构建文件
└── settings.gradle.kts
```

### 2.1 模块边界原则

- `domain/` 纯 Kotlin，零 Android 依赖，可独立单元测试
- `data/` 只做存储，不含业务判断
- `system/` 封装所有 Android 系统 API 调用，通过接口暴露给 domain
- `ui/` 只做渲染和用户交互，通过 ViewModel 调用 domain

### 2.2 依赖方向

```
UI → Domain → Data → System
  │      │       │
  │      │       └── 只依赖 Android API，不依赖其他层
  │      └── 纯 Kotlin，零 Android 依赖
  └── 通过 ViewModel 调用 Domain，不直接访问 Data/System
```

---

## 3. 系统架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │  Onboarding   │  │  Controller  │  │  Controlled            │ │
│  │  · 角色选择    │  │  · 策略管理   │  │  · 使用报告             │ │
│  │  · 配对引导    │  │  · 使用报告   │  │  · 解锁申请             │ │
│  │  · DO 设置    │  │  · TOTP 生成  │  │  · 紧急解除             │ │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘ │
│         │                 │                      │              │
│         └─────────────────┴──────────────────────┘              │
│                            │                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    ViewModels                            │    │
│  │  PairingViewModel │ PolicyViewModel │ StatsViewModel     │    │
│  └─────────────────────────────┬───────────────────────────┘    │
│                                │                                │
├────────────────────────────────┴────────────────────────────────┤
│                        Domain Layer                             │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────┐  │
│  │ Pairing    │  │ Totp       │  │ Policy     │  │ Security │  │
│  │ Protocol   │  │ Engine     │  │ Engine     │  │ Monitor  │  │
│  └────────────┘  └────────────┘  └────────────┘  └──────────┘  │
│                                │                                │
├────────────────────────────────┴────────────────────────────────┤
│                         Data Layer                              │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ Room/SQLCipher │  │ Android        │  │ SharedPrefs      │  │
│  │                │  │ Keystore       │  │ Encrypted        │  │
│  │ · usage_records│  │ · ECC keys     │  │ · config         │  │
│  │ · hourly       │  │ · TOTP seeds   │  │ · session state  │  │
│  │ · daily        │  │ · AES keys     │  │ · error counts   │  │
│  │ · policies     │  │                │  │                  │  │
│  │ · audit_log    │  │                │  │                  │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
│                                │                                │
├────────────────────────────────┴────────────────────────────────┤
│                        System Layer                             │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ DevicePolicy   │  │ UsageStats     │  │ NTP Client       │  │
│  │ Manager        │  │ Manager        │  │ · Google NTP     │  │
│  │ · suspend      │  │ · query stats  │  │ · Aliyun NTP     │  │
│  │ · set DO       │  │ · foreground   │  │ · RTT compensate │  │
│  │ · clear DO     │  │   time tracking│  │                  │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ Foreground     │  │ AlarmManager   │  │ BroadcastReceiver│  │
│  │ Service        │  │ · 30s poll     │  │ · BOOT_COMPLETED │  │
│  │ · polling loop │  │ · hourly agg   │  │ · CONNECTIVITY   │  │
│  │ · notification │  │ · daily reset  │  │                  │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 配对流程设计

### 4.1 流程时序

```
被控端 (A)                              控制端 (B)
    │                                       │
    │ 1. 选择角色"我需要被管控"              │
    │ → 生成 ECC 密钥对                     │
    │ → 私钥存入 Keystore                   │
    │ → 展示公钥二维码                       │
    │                                       │
    │ ──── 公钥二维码 (公钥 + 配对ID) ────→  │
    │                                       │ 2. 选择角色"我要管控对方"
    │                                       │ → 扫描二维码，获取 A 的公钥
    │                                       │ → 生成 3 个 TOTP 种子 (各 20B)
    │                                       │ → 生成 ECC 密钥对
    │                                       │ → 用 A 公钥加密 (种子 + B公钥)
    │                                       │ → 用 B 私钥签名
    │                                       │ → 展示回传二维码
    │                                       │
    │ ←──── 回传二维码 (加密数据) ────      │
    │                                       │
    │ 3. 扫描回传二维码                      │
    │ → 用 A 私钥解密                       │
    │ → 用 B 公钥验证签名                    │
    │ → 3 个种子加密存入 Keystore            │
    │ → 保存 B 公钥                         │
    │ → 配对完成，进入被控端主界面            │
    │                                       │ → 配对完成，进入控制端主界面
```

### 4.2 ECC 曲线双后端

| 条件 | 曲线 | 公钥长度 | 实现方式 |
|------|------|---------|---------|
| API 33+ | X25519 | 32 字节 | Keystore 原生 ECDH |
| API 30-32 | secp256r1 (NIST P-256) | 65 字节 | Keystore 原生硬件 |

Domain 层抽象为 `CryptoEngine` 接口，上层不感知曲线差异。

### 4.3 QR 码内容编码

**被控端 → 控制端（第一帧）**：
```json
{
  "v": 1,
  "type": "pair_req",
  "id": "a3f8-...",
  "pub": "BKyDx9...",
  "name": "Pixel 7"
}
```
总计约 150 字节，静态 QR 轻松承载。

**控制端 → 被控端（第二帧）**：
```json
// 加密前明文
{
  "v": 1,
  "type": "pair_resp",
  "id": "a3f8-...",
  "seeds": {
    "setting": "base64...",
    "unlock": "base64...",
    "destroy": "base64..."
  },
  "pub": "BKxYz7...",
  "name": "Galaxy S24"
}

// 加密过程
密钥 = ECDH(A_pub, B_priv)
密文 = AES-256-GCM(plaintext, key)
签名 = ECDSA(B_priv, ciphertext)

// 打包: [version:1B][ciphertext][iv:12B][signature:64B]
// Base64 编码后 ≈ 470-530 字符，静态 QR 容量内
```

### 4.4 配对安全要点

- 配对会话 ID 用于防止重放攻击（同一会话只能配对一次）
- 二维码有效期 5 分钟，过期需重新生成
- 配对过程中双方界面都显示对方设备标识，需人工确认一致
- 配对完成后，明文种子立即从内存清零（`Arrays.fill()`）
- `name` 字段为 `Build.MANUFACTURER + Build.MODEL`，仅用于 UX 确认，不参与加密

### 4.5 异常处理

| 异常场景 | 处理方式 |
|---------|---------|
| QR 码过期 | 提示"二维码已过期"，自动重新生成 |
| 签名验证失败 | 提示"配对数据可能被篡改"，中止配对 |
| 解密失败 | 提示"配对失败，请重试"，清除临时数据 |
| 会话 ID 不匹配 | 提示"请扫描正确的配对码" |

---

## 5. TOTP 三密钥验证体系

### 5.1 密钥语义与 UI 命名

| 密钥 | 内部标识 | UI 名称（控制端） | UI 名称（被控端） | 用途 |
|------|---------|-----------------|-----------------|------|
| Admin Key | `setting` | "管理码" | "调整码" | 修改策略（时长、黑白名单、时段） |
| Unlock Key | `unlock` | "解锁码" | "临时解锁码" | 申请临时解除限制 |
| Destroy Key | `destroy` | "终止码" | "终止码" | 解除配对关系（保留 DO） |

三码不可互换——输入界面会明确标注当前操作需要哪种码。

### 5.2 TOTP 参数

```
算法: HMAC-SHA1 (RFC 6238 标准)
种子长度: 20 字节 (160 bit)
时间窗口: 30 秒
容差: ±1 窗口（当前窗口前后各一个，共 3 个有效码）
位数: 6 位数字
```

### 5.3 验证流程

```
用户输入 6 位码
       │
       ▼
  ┌─ 频率限制检查 ─┐
  │ 输错 ≥ 5 次    │──→ 锁定 30 秒（跳过当前 TOTP 窗口）
  └────────────────┘
       │ 通过
       ▼
  ┌─ 计算当前及前后窗口的 TOTP ─┐
  │ 与输入比对                   │
  └─────────────────────────────┘
       │ 匹配         │ 不匹配
       ▼              ▼
    操作执行      错误计数 +1
                  显示"验证码错误（N/5）"
```

**防暴力破解**：5 次错误后锁定 30 秒（刚好跳过当前窗口）。锁定结束后错误计数归零。30 秒窗口 × 5 次 = 每分钟最多 10 次，100 万组合理论破解需 ~69 天。

### 5.4 控制端 TOTP 展示

配对完成后，控制端展示三个种子的 otpauth URI，用户可选择性备份到外部验证器 App：

```
otpauth://totp/PeerLock:管理码?secret=BASE32&digits=6&period=30
otpauth://totp/PeerLock:解锁码?secret=BASE32&digits=6&period=30
otpauth://totp/PeerLock:终止码?secret=BASE32&digits=6&period=30
```

---

## 6. 签名信封协议（TOTP Envelope）

### 6.1 两种交互模式

| 模式 | 载体 | 适用场景 | 配置来源 |
|------|------|---------|---------|
| 快速码 | 6 位数字，口头告知 | 简单解锁、紧急操作 | 使用预设默认值 |
| 签名信封 | 二维码/字符串 | 携带详细信息、需审批、需调整 | 信封内 cfg 字段 |

### 6.2 信封加密协议

```
明文 payload:
{
  "t": "unlock",                 // 密钥类型：unlock / setting / destroy
  "c": "482901",                 // 当前窗口的 TOTP 6 位码
  "sid": "a3f8...",              // 配对会话 ID（防重放）
  "ts": 1746787200,              // 生成时间戳（秒级）
  "cfg": {                       // 可选配置
    "dur": 30,                   // 时长值
    "unit": "m",                 // 单位：m=分钟, h=小时, d=天
    "mode": "cumulative"         // cumulative=累计时长, absolute=绝对截止时间
  }
}

加密过程:
1. ECDH 密钥协商: shared = ECDH(发送方私钥, 接收方公钥)
2. AES-256-GCM 加密 payload → ciphertext
3. 签名 = ECDSA(发送方私钥, ciphertext)
4. 打包: [version:1B][ciphertext][iv:12B][signature:64B]
5. Base64 编码 → ~270-340 字符，静态 QR 码可承载
```

### 6.3 解锁时长配置语义

| mode | 含义 | 示例 |
|------|------|------|
| `cumulative` | 从本次解锁开始，累计可用 X 分钟/小时 | "解锁 30 分钟"→ 用满 30 分钟后重新暂停 |
| `absolute` | 在某个绝对时间点前可用 | "解锁到今天 22:00"→ 到点自动暂停 |

无 cfg 时的默认行为：使用策略中预设的默认解锁时长。

### 6.4 防篡改与防重放

- ECDSA 签名覆盖密文，任何修改都会导致验签失败
- 信封内含时间戳 + 会话 ID，被控端验证：
  - 时间戳在 ±5 分钟内（允许离线场景下略有延迟）
  - 同一信封不可重复使用（记录已消费的 ts+sid 组合）
- 过期信封扫码后提示"此码已过期，请向控制方索取新码"

---

## 7. 双向请求/响应信封协议

### 7.1 核心理念

```
之前的设计：控制方主动发起 → 被控方被动执行（单向）
现在的设计：被控方主动发起请求 → 控制方审批后返回响应（双向）

6 位 TOTP 数字码仍然保留 → 用于快速简单操作（口头告知）
签名信封 → 用于复杂操作（携带详细请求信息，需要审批）
```

### 7.2 请求信封（被控端 → 控制端）

```kotlin
data class RequestEnvelope(
    val v: Int = 1,
    val type: String,                  // "unlock" / "config"
    val sessionId: String,
    val requestId: String,             // 本次请求 ID（UUID），一次性
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val payload: RequestPayload,
)

data class DeviceInfo(
    val todayScreenTimeMs: Long,
    val suspendedApps: List<String>,
    val isInSafeMode: Boolean,
    val targetAppDetail: AppUsageDetail?,  // unlock 请求时附带
)

data class AppUsageDetail(
    val packageName: String,
    val appName: String,
    val todayTotalMs: Long,
    val sessions: List<UsageSession>,      // 今日各次使用会话
    val currentPolicy: PolicySummary?,     // 当前限制策略
    val todayRemainingMs: Long?,           // 今日剩余可用时长
)

sealed class RequestPayload {
    data class UnlockRequest(
        val targetPackage: String,
        val requestedDuration: Int,        // 请求时长（分钟）
        val durationMode: String,          // "cumulative" / "absolute"
        val absoluteEndTime: Long?,
        val reason: String?,
    ) : RequestPayload()

    data class ConfigRequest(
        val changes: List<PolicyChange>,
        val reason: String?,
    ) : RequestPayload()
}
```

### 7.3 控制端处理流程

```
控制端扫描/输入请求信封
         │
         ▼
  验证签名 + 验证会话 + 检查时效
         │
         ▼
  ┌─────────────────────────────────────────┐
  │  收到解锁请求                             │
  │                                         │
  │  设备状态:                                │
  │  · 今日屏幕时间: 3h 42m                   │
  │  · 当前使用: 微信                         │
  │  · 已暂停: 抖音、B站                      │
  │                                         │
  │  抖音 使用详情:                           │
  │  · 今日使用: 58 分钟 / 限额 30 分钟       │
  │  · 使用记录: 08:30-08:45, 12:00-12:20... │
  │  · 今日剩余: 0 分钟                       │
  │                                         │
  │  请求: 解锁抖音 30 分钟（累计）            │
  │  理由: "需要查看一个视频链接"              │
  │                                         │
  │  控制方调整:                              │
  │  时长: [30] → [15] 分钟                   │
  │  模式: ●累计  ○绝对时间                   │
  │                                         │
  │       [拒绝]        [批准并生成响应码]     │
  └─────────────────────────────────────────┘
```

### 7.4 响应信封（控制端 → 被控端）

```kotlin
data class ResponseEnvelope(
    val v: Int = 1,
    val type: String,                  // "unlock_resp" / "config_resp"
    val sessionId: String,
    val requestId: String,             // 对应的请求 ID
    val timestamp: Long,
    val approved: Boolean,
    val payload: ResponsePayload,
)

sealed class ResponsePayload {
    data class UnlockResponse(
        val targetPackage: String,
        val duration: Int,             // 实际批准时长（可能被调整）
        val durationMode: String,
        val absoluteEndTime: Long?,
    ) : ResponsePayload()

    data class ConfigResponse(
        val changes: List<PolicyChange>,
    ) : ResponsePayload()

    data class Rejected(
        val reason: String?,
    ) : ResponsePayload()
}
```

### 7.5 完整交互流程

```
被控端                                    控制端
  │                                         │
  │ 1. 点击抖音 → 提示被暂停                 │
  │    → 「申请解锁」按钮                     │
  │                                         │
  │ 2. 本地申请表单                          │
  │    解锁应用: 抖音 (自动填)                │
  │    请求时长: 30 分钟                      │
  │    模式: 累计时长                         │
  │    理由: [查看视频链接]                   │
  │    [生成申请码]                          │
  │                                         │
  │ 3. 显示请求二维码/字符串                  │
  │ ──── 扫码或转达 ──────────────────────→  │
  │                                         │ 4. 扫描请求
  │                                         │    看到设备状态 + 请求详情
  │                                         │    调整: 30min → 15min
  │                                         │    点击「批准」
  │                                         │
  │                                         │ 5. 显示响应二维码/字符串
  │ ←──── 扫码或转达 ─────────────────────── │
  │                                         │
  │ 6. 扫描响应                              │
  │    验证签名 + 会话                        │
  │    approved=true, duration=15min         │
  │    → 解锁抖音 15 分钟                    │
  │                                         │
  │ 7. 显示:「已解锁抖音，15分钟后自动暂停」   │
```

### 7.6 配置变更同样支持请求/响应

```
被控端可发起配置变更请求：
  → 附带当前配置摘要
  → 附带请求的变更内容
  → 控制方可调整、批准或拒绝
  → 返回响应信封
```

### 7.7 频率限制

| 操作 | 频率限制 |
|------|---------|
| 解锁请求 | 30 秒 1 次 |
| 配置变更请求 | 30 秒 1 次 |
| 快速码输入 | 30 秒 1 次 + 错误 5 次锁定 30 秒 |
| 信封有效期 | 5 分钟 |
| 响应信封有效期 | 5 分钟 |

### 7.8 离线场景

```
方案 A：转达字符串
  被控端生成请求 → 复制字符串 → 微信发送给对方
  控制端收到 → 粘贴到 App → 审批 → 生成响应字符串
  响应字符串 → 微信发回 → 被控端粘贴执行

方案 B：视频通话扫码
  双方视频通话 → 屏幕对屏幕扫码
  更快但需要同步时间

两种方式都完全离线，不依赖 PeerLock 服务器。
```

---

## 8. 策略执行与应用暂停

### 8.1 策略数据模型

```kotlin
data class RestrictionPolicy(
    val id: Long = 0,
    val targetPackage: String,
    val dailyLimitMinutes: Int?,         // 每日时长上限，null = 不限
    val allowedTimeStart: LocalTime?,    // 允许使用起始时间
    val allowedTimeEnd: LocalTime?,      // 允许使用结束时间
    val isBlacklist: Boolean,            // true = 黑名单模式
    val isActive: Boolean,
    val createdAt: Long,
    val lastModified: Long
)
```

### 8.2 策略执行状态机

```
              ┌─────────────┐
              │   MONITORING │ ← 正常监控，记录使用时长
              └──────┬──────┘
                     │ 触发条件（超时 / 非允许时段）
                     ▼
              ┌─────────────┐
              │  SUSPENDED   │ ← 应用被暂停，图标变灰
              └──────┬──────┘
                     │ 用户输入解锁码 / 收到解锁响应
                     ▼
              ┌─────────────┐
              │  UNLOCKED    │ ← 临时解锁，恢复使用
              └──────┬──────┘
                     │ 解锁期满 / 再次超时
                     ▼
              └→ SUSPENDED（回到暂停）
```

### 8.3 巡检逻辑（前台服务，30 秒间隔）

```
每次巡检：
  1. 读取当前所有策略
  2. 对每个受限应用检查：
     a. 当前时间是否在允许时段内 → 不在则 SUSPEND
     b. 今日累计使用时长是否超限 → 超则 SUSPEND
     c. 当前状态是否为 UNLOCKED 且期满 → 是则 SUSPEND
  3. 对状态变更的应用调用 DevicePolicyManager.setPackagesSuspended()
  4. 记录状态变更日志到审计表
  5. 检查时间偏移
  6. 采集使用统计数据
```

### 8.4 setPackagesSuspended 行为

```kotlin
devicePolicyManager.setPackagesSuspended(adminComponent,
    packages = arrayOf("com.ss.android.ugc.aweme", "com.zhiliaoapp.musically"),
    suspended = true
)
```

效果：应用图标变灰、启动时系统显示"此应用已被暂停"、由系统维护不受 PeerLock 进程存活状态影响。

---

## 9. 时间偏移检测与安全模式

### 9.1 核心思路

不依赖系统时间做判断，自维护一个"真实时间基准"：

```
真实时间 = 系统时间 + 已知偏移量(offset)
```

### 9.2 NTP 同步策略

| 时机 | 原因 |
|------|------|
| App 启动时 | 获取初始 offset |
| 检测到系统时间突变时 | 确认是否真实偏移 |
| 每 6 小时定期 | 防止长周期时钟漂移（晶振误差） |
| 网络恢复时 | 安全模式下恢复联网后立即校验 |

**NTP 服务器**：主 `time.google.com`，备 `ntp.aliyun.com`，超时 3 秒。

**RTT 补偿**：
```
queryStart = currentTimeMillis()
ntpTime = ntpClient.query()
queryEnd = currentTimeMillis()
rtt = queryEnd - queryStart
offset = ntpTime + rtt/2 - queryEnd
```

### 9.3 偏移检测与恢复

```
          系统时间突变检测（|新offset - 旧offset| > 30s）
               │
               ▼
        ┌──────────────┐
        │  NTP 校验中   │
        └──────┬───────┘
               │
        ┌──────┴──────┐
        │              │
    NTP 成功        NTP 失败（无网络）
        │              │
        ▼              ▼
   更新 offset      进入 SAFE_MODE
   恢复正常         → 等待网络恢复
                    → 网络恢复后自动 NTP 校验
                    → 成功则更新 offset + 恢复
                    → 或手动输入管理码恢复
```

### 9.4 安全模式行为

- 所有受限应用立即 SUSPEND（包括 UNLOCKED 状态的）
- 状态栏通知："⚠️ PeerLock 检测到时间异常，设备已锁定"
- 主界面显示原因说明
- 解锁方式：网络恢复自动校准 / 手动输入管理码

---

## 10. 紧急逃生通道

### 10.1 三级逃生体系

```
┌─────────────────────────────────────────────────────────────┐
│  L1  终止码（应用内正常入口）                                  │
│      需要：终止码密钥 + 扫码/输入信封                          │
│      效果：解绑配对，保留 DO，保留数据                          │
│      门槛：最低（有密钥即可）                                   │
│                                                             │
│  L2  一次性 ADB broadcast（高级解除，7次连点进入）               │
│      需要：连点7次 → 输入挑战码 → 获取含nonce的命令 → 电脑执行   │
│      效果：解除 DO + 清除密钥，保留数据                         │
│      门槛：中等（多层隐藏 + 一次性命令）                        │
│                                                             │
│  L3  固定 ADB 命令（仅开发文档）                                │
│      需要：知道命令格式 + 电脑                                  │
│      效果：pm clear 全清                                      │
│      门槛：最高（需要主动查阅文档）                              │
└─────────────────────────────────────────────────────────────┘
```

### 10.2 各级操作对比

| | L1 终止码 | L2 一次性 broadcast | L3 固定命令 |
|---|---|---|---|
| 入口 | App 主界面 | App 内隐藏页面 | 仅开发者文档 |
| 需要密钥 | 是（终止码） | 否 | 否 |
| 需要 App 运行 | 是 | 是 | 否 |
| 需要电脑 | 否 | 是 | 是 |
| DO 状态 | 保留 | 解除 | 解除 |
| 密钥材料 | 销毁 | 销毁 | 全清 |
| 配对关系 | 解除 | 解除 | 全清 |
| 使用数据 | 保留 | 保留 | 全清 |
| 命令是否变化 | 每次不同（信封） | 每次不同（nonce） | 固定不变 |

### 10.3 L1 终止码流程

```
输入 / 扫描终止码
       │
       ▼
  TOTP + 签名验证通过
       │
       ▼
  ┌──────────────────────────────────┐
  │  ⚠️ 确认解除配对？                │
  │                                  │
  │  此操作将：                       │
  │  · 解除与控制方的配对关系          │
  │  · 删除所有密钥和种子             │
  │  · 设备管理员权限保留              │
  │  · 使用数据和配置保留              │
  │                                  │
  │       [取消]    [确认]            │
  └──────────────────────────────────┘
       │
       ▼
  执行解除流程，App 进入「已授权未配对」状态
```

### 10.4 L2 高级解除流程

```
设置 → 关于 PeerLock → 连点 7 次「版本号」
       │
       ▼
  Toast: "已开启高级操作"
  返回设置，出现「高级解除」选项
       │
       ▼
  点击「高级解除」
  → 页面显示随机挑战码（4位 hex，如 7A3F）
  → 输入框（禁止粘贴，FLAG_SECURE 防截屏）
  → 手动输入挑战码 → 验证通过
       │
       ▼
  显示含 nonce 的一次性 ADB 命令

  ┌───────────────────────────────────────┐
  │ $ adb shell am broadcast \            │
  │     -a com.peerlock.ACTION_EMERGENCY  │
  │     -n com.peerlock/.EmergencyUnlock  │
  │     --es unlock_nonce a7f3b29c4e8f1d  │
  └───────────────────────────────────────┘

  命令不可复制，请手动输入或拍照
  有效期 5 分钟，一次性使用
```

**nonce 参数**：16 位随机 hex，5 分钟过期，一次性使用。App 的 `EmergencyUnlockReceiver` 收到 broadcast 后验证 nonce 并执行解除。

### 10.5 L3 固定命令

仅存在于开发文档：

```
adb shell dpm remove-active-admin com.peerlock/.DeviceAdminReceiver
adb shell pm clear com.peerlock
```

依赖 `testOnly="true"` 允许 ADB 移除 Device Owner。

### 10.6 安全假设

```
L3 固定命令的安全假设：
用户必须：
  1. 知道 PeerLock 使用 Device Owner 机制
  2. 知道 remove-active-admin 这个 ADB 命令
  3. 知道 PeerLock 的包名和 Receiver 类名
  4. 有电脑 + USB 线 + ADB 环境
  5. 愿意在终端手动敲命令
→ 等同于"直接问控制方要终止码"甚至更难
→ 符合威胁模型
```

### 10.7 三级解锁机制（L2 隐藏入口）

```
层级  隐藏机制             目的
L1    默认隐藏入口          普通用户不知道存在
L2    连点 7 次解锁         需要知道技巧才能找到
L3    随机挑战码 + 手动输入  确保主动操作
L4    命令不可复制          防止截图分享一键执行
L5    页面 FLAG_SECURE      防止截屏传播命令
```

---

## 11. 前台服务与后台保活

### 11.1 前台服务职责

```
PeerLockService (Foreground Service)
  ├── 策略巡检（30s 间隔，AlarmManager 精确闹钟）
  ├── 时间偏移检测
  ├── 使用统计采集（UsageStatsManager）
  └── 状态栏通知更新
```

### 11.2 保活策略

| 层级 | 机制 | 说明 |
|------|------|------|
| L1 | 前台服务通知 | 系统优先级提升 |
| L2 | Device Owner 特权 | 系统不会杀死 DO 应用的前台服务 |
| L3 | 电池优化白名单 | DO 可自动加入白名单 |
| L4 | 重启自启 | BOOT_COMPLETED + Application.onCreate() 启动服务 |

### 11.3 状态栏通知

```
┌──────────────────────────────────────┐
│ 🔒 PeerLock 运行中                    │
│ 今日屏幕时间: 3h42m | 2 个应用受限     │
└──────────────────────────────────────┘
```

不可滑动关闭，点击打开 App 主界面。

### 11.4 重启恢复流程

```
设备重启
    │
    ▼
系统启动 → Device Owner 自动激活
    │
    ▼
PeerLockApp.onCreate()
    ├─ 1. 加载加密材料（Keystore）
    ├─ 2. 读取策略配置（Room）
    ├─ 3. 启动前台服务
    ├─ 4. 立即执行一次巡检（重新 SUSPEND 应用）
    ├─ 5. NTP 时间同步
    └─ 6. 恢复时间偏移监控
```

---

## 12. Device Owner 初始化流程

### 12.1 整体流程

```
首次启动 PeerLock
      │
      ▼
  角色选择
      │
      ├─ 「我要管控对方」→ 控制端初始化（无需 DO）
      │
      └─ 「我需要被管控」→ 被控端初始化
            │
            ▼
      DO 设置引导
      ├─ 无线 ADB（主路径，仿 Shizuku UX）
      └─ 有线 ADB（备用路径）
            │
            ▼
      DO 设置成功 → 进入配对流程
```

### 12.2 无线 ADB 配对流程（仿 Shizuku）

```
Step 1: 点击「开启无线调试」→ 跳转到开发者选项，高亮引导
Step 2: 用户开启「无线调试」→ 进入无线调试子页面
Step 3: 用户点击「配对」→ 系统弹出配对码通知
Step 4: 用户在通知栏输入配对码完成配对
Step 5: PeerLock 自动检测配对完成 → 执行 set-device-owner
Step 6: 显示成功页面 → 进入下一步
```

**前台通知要求**：配对过程中 PeerLock 必须保持前台（`set-device-owner` 要求 App 在前台或有前台服务）。

### 12.3 有线 ADB 备用路径

```
$ adb shell dpm set-device-owner \
    com.peerlock/.DeviceAdminReceiver
```

注意事项：设备不能已有其他账户（Google 账户、工作资料等），如有需先移除。

### 12.4 常见问题处理

| 错误 | 原因 | 处理 |
|------|------|------|
| Not allowed to set device owner | 设备上存在账户 | 引导暂时移除账户 |
| Unknown admin | Receiver 未正确注册 | 提示更新 App |
| 无线调试配对超时 | 配对码有效期短 | 提示重新生成 |

---

## 13. 使用统计与数据可视化

### 13.1 数据采集与聚合

```
30 秒巡检 → raw_records（增量写入）
每小时整点 → usage_hourly_summary（从 raw 聚合）
每日 00:00 → usage_daily_summary（从 hourly 聚合）
```

### 13.2 数据库设计

```sql
-- 30 秒增量记录（默认保留 30 天，用户可配置）
CREATE TABLE usage_records (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    package_name TEXT NOT NULL,
    start_time  INTEGER NOT NULL,
    end_time    INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    date        TEXT NOT NULL,
    INDEX idx_date_package (date, package_name)
);

-- 每小时聚合（永久保留）
CREATE TABLE usage_hourly_summary (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    package_name TEXT NOT NULL,
    date        TEXT NOT NULL,
    hour        INTEGER NOT NULL,
    total_ms    INTEGER NOT NULL,
    UNIQUE (package_name, date, hour)
);

-- 每日聚合（永久保留）
CREATE TABLE usage_daily_summary (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    package_name TEXT NOT NULL,
    date        TEXT NOT NULL,
    total_ms    INTEGER NOT NULL,
    launch_count INTEGER DEFAULT 0,
    UNIQUE (package_name, date)
);

-- 审计日志（保留 90 天）
CREATE TABLE audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp   INTEGER NOT NULL,
    action      TEXT NOT NULL,
    target_pkg  TEXT,
    detail      TEXT,
    INDEX idx_timestamp (timestamp)
);
```

### 13.3 用户视角图表层级

```
查看「今天」：
  → 小时级柱状图（永久数据）
  → 点击某小时 → 30 秒级详情（需 raw_records，30 天内）

查看「本周某天」：
  → 选中日期 → 小时级柱状图
  → 点击展开详情

查看「本月」：
  → 日级折线图
  → 点击某天 → 进入该天小时级视图

查看「往年」：
  → 日级/周级聚合（只有 daily_summary）
```

### 13.4 用户可配置清理

```
设置 → 存储管理：
  详细使用记录保留期: 7天 / 30天(默认) / 90天 / 永久
  [立即清理旧数据]
  当前存储占用: 详细 2.3MB + 小时 1.1MB + 日 0.2MB + 审计 0.5MB = 4.1MB
```

### 13.5 控制端 vs 被控端视角

```
被控端：我的屏幕时间、应用排行、本周趋势
控制端：对方的使用情况、当前状态、受限应用、[调整策略] [临时解锁]
```

---

## 14. 数据保留与解除流程

### 14.1 数据分类

| 数据类别 | 终止码 | 高级解除 | 固定命令 |
|---------|--------|---------|---------|
| A. 加密材料（私钥、种子、对端公钥） | 销毁 | 销毁 | 全清 |
| B. 用户数据（使用统计、策略、日志、偏好） | 保留 | 保留 | 全清 |
| C. 会话元数据（配对 ID、设备标识） | 销毁 | 销毁 | 全清 |

### 14.2 重新配对后的数据处理

```
重新配对完成
      │
      ▼
控制端看到：「检测到已有策略配置」
      ├─ 清空旧策略，从零开始
      ├─ 保留旧策略，继续使用
      └─ 逐条查看，选择性保留
```

使用统计数据始终保留，策略由新控制方决定。

### 14.3 「已授权未配对」状态

终止码执行后，App 进入此状态：
- DO 权限保留，下次配对无需重新走 ADB 流程
- 历史数据和策略配置保留
- 提供「开始新的配对」「查看历史数据」「导出数据」入口

---

## 15. 关键接口定义

```kotlin
// Domain 层接口（纯 Kotlin，无 Android 依赖）

interface CryptoEngine {
    suspend fun generateKeyPair(): KeyPair
    suspend fun encrypt(data: ByteArray, peerPublicKey: ByteArray): ByteArray
    suspend fun decrypt(data: ByteArray): ByteArray
    suspend fun sign(data: ByteArray): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray, peerPublicKey: ByteArray): Boolean
}

interface TotpEngine {
    fun generateCode(seed: ByteArray, timeStep: Long = currentStep()): String
    fun verifyCode(seed: ByteArray, input: String, tolerance: Int = 1): Boolean
    fun generateEnvelope(type: KeyType, config: EnvelopeConfig? = null): TotpEnvelope
    fun verifyEnvelope(envelope: TotpEnvelope): EnvelopeResult
}

interface PolicyEngine {
    suspend fun evaluate(packageName: String, currentTime: Long): PolicyAction
    suspend fun suspendApp(packageName: String)
    suspend fun unsuspendApp(packageName: String)
    suspend fun resetDailyUsage()
}

interface TimeSyncManager {
    suspend fun getCurrentRealTime(): Long
    suspend fun syncWithNtp(): Boolean
    fun getStoredOffset(): Long
    fun isInSafeMode(): Boolean
}

interface StorageRepository {
    suspend fun insertUsageRecord(record: UsageRecord)
    suspend fun getUsageByDate(date: String): List<UsageRecord>
    suspend fun getHourlySummary(date: String): List<HourlySummary>
    suspend fun getDailySummary(startDate: String, endDate: String): List<DailySummary>
    suspend fun getAllPolicies(): List<RestrictionPolicy>
    suspend fun upsertPolicy(policy: RestrictionPolicy)
    suspend fun deletePolicy(id: Long)
    suspend fun insertAuditLog(log: AuditLog)
}
```

---

## 16. P0 / P1 功能边界

### 16.1 P0（MVP 必须交付）

| # | 功能 | 验收标准 |
|---|------|---------|
| 1 | Device Owner 初始化 | 无线 ADB 30 秒完成；有线 ADB 备用可用 |
| 2 | 防卸载、防强制停止 | 系统设置无法卸载；强制停止后策略仍生效 |
| 3 | 应用黑白名单 + 强制暂停 | setPackagesSuspended 正确执行 |
| 4 | 使用时长统计 | 30 秒粒度采集；与系统设置误差 < 5% |
| 5 | 按时段限制 | 非允许时段自动 SUSPEND |
| 6 | TOTP 三密钥验证 | 独立种子；±1 窗口容差；5次错误锁30秒 |
| 7 | 签名信封协议 | ECDH + AES-GCM + ECDSA；防重放 |
| 8 | 双向请求/响应 | 被控方发请求，控制方审批返回响应 |
| 9 | 配对流程 | 二维码双向扫描；一次性完成 |
| 10 | 加密存储 | Keystore + SQLCipher + EncryptedSharedPreferences |
| 11 | 紧急逃生 - 终止码 | 解绑配对保留 DO + 二次确认 |
| 12 | 紧急逃生 - 一次性 broadcast | 7次连点 + 挑战码 + nonce 命令 |
| 13 | 紧急逃生 - 固定命令 | testOnly=true + 仅文档 |
| 14 | 角色分离 UI | 控制端/被控端不同视角 |
| 15 | 时间偏移检测 | 自维护 offset + NTP；无网锁定 |
| 16 | 前台服务保活 | DO 特权 + 常驻通知 + 重启自启 |
| 17 | 数据可视化 | 小时/日/周/月图表 |

### 16.2 P1（后续迭代）

| # | 功能 | 说明 |
|---|------|------|
| 1 | 本地 VPN 网络过滤 | 按应用过滤网络；VPN 独占冲突处理 |
| 2 | 对等双向控制 | 利用双向公钥；新增 UI；冲突解决 |
| 3 | 导入导出 | 加密签名导出；安全导入与合并 |
| 4 | 远程实时修改 | 轻量级服务器；公钥加密通信 |
| 5 | 动态二维码视频传输 | 多帧动画 QR |
| 6 | 加密统计报告 | 周/月报告；加密发送 |
| 7 | 位置/轨迹监控 | 数据库预留字段；合规评估 |

---

## 17. 技术风险评估

### 17.1 高风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 厂商 ROM 杀后台 | 前台服务被杀，策略失效 | DO 特权 + 电池优化白名单 + 重启自启 |
| Device Owner 行为差异 | 不同厂商 API 行为不一致 | 重点测试 MIUI/ColorOS/HarmonyOS |
| Keystore 不可用 | 加密材料无法存储 | 降级到 EncryptedSharedPreferences |
| 系统时间被篡改 | TOTP 验证失效 | 自维护 offset + NTP 校验 |

### 17.2 中风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| QR 码数据超限 | 配对失败 | 压缩 JSON / 升级动态 QR |
| UsageStats 延迟 | 统计不准确 | 30 秒轮询 + 增量计算补偿 |
| 时区切换 | offset 异常 | 检测时区变化，自动重新同步 |

### 17.3 低风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| SQLCipher 性能 | 查询慢 | 合理索引 + 聚合缓存 |
| 前台通知被用户关闭 | 保活失效 | DO 可设置通知不可关闭 |

---

## 18. 关键验收场景

```
场景 1：完整生命周期
  配对 → 设置策略 → 使用限制生效 → 请求解锁 → 控制方审批 → 解除管控
  每一步都记录到审计日志

场景 2：防逃避
  尝试卸载 PeerLock → 被系统阻止
  强制停止 PeerLock → 已暂停应用仍不可用
  重启设备 → 策略自动恢复
  修改系统时间 → 触发安全模式
  断网修改时间 → 锁定

场景 3：逃生通道
  终止码扫码 → 二次确认 → 解绑（DO 保留）
  高级解除 → 挑战码 → 一次性命令 → 解除 DO
  直接 ADB 命令 → pm clear → 完全重置

场景 4：双向请求/响应
  被控方请求解锁 → 控制方看到详情 → 调整参数 → 批准 → 被控方执行
  被控方请求配置变更 → 控制方审批 → 响应 → 被控方应用

场景 5：边界条件
  TOTP 窗口边界（第 29 秒和第 1 秒）→ 验证通过
  信封过期（> 5 分钟）→ 提示过期
  requestId 重复使用 → 拒绝
  存储空间满 → 优雅降级
  时区切换 → offset 自动调整
```

---

## 19. 后续设计待定项（P1）

- 导入导出：数据分类、签名验证、冲突合并、跨设备迁移
- 对等模式：冲突解决策略、双向 UI、权限协商
- VPN 网络过滤：TUN 接口实现、VPN 独占冲突处理、DO 始终开启 VPN
- 远程服务器：通信协议、密钥轮换、离线队列
