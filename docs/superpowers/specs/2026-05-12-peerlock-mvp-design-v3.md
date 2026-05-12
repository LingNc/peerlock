# PeerLock MVP Design Spec

> **Version**: 3.0
> **Date**: 2026-05-12
> **Status**: Draft - Awaiting Review
> **Scope**: MVP 多设备管控版本，架构预留对等模式
> **基于**: v2.0 (2026-05-11) + 多配对/策略管理/统计统一/UI重构

---

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.0 | 2026-05-09 | 初始设计规范 |
| v2.0 | 2026-05-11 | 基于实际实现更新：配对支持字符串、DO 两阶段设置、全面屏适配、申请解锁双模式、L2 挑战码验证、BootReceiver 配对检查、nonce 格式校验、StatsScreen 图表交互、导航返回支持 |
| v3.0 | 2026-05-12 | 多设备配对支持、设备选择页、配对确认页拆分、策略管理双模式双Tab、统一统计页、接收指令页、种子删除/归档、DO软件内取消、终止码/申请解除移入配对信息页、L2版本号7次连点(5分钟窗口)、启动路由重构、管控端主页精简 |

---

## 1. 产品概述

### 1.1 产品愿景

一款 Android 应用，实现多人之间的屏幕时间管控。管控方可以同时管理多个被控设备，任何修改限制的操作都需要管控方提供基于时间的一次性验证码（TOTP），从而实现真正的"共同约束"。

### 1.2 MVP 范围

多设备单向控制流（一个管控端配对多个被控端），底层架构预留双向关系存储与多密钥管理能力，确保后续升级为对等模式时核心加密和验证逻辑无需重写。

### 1.3 目标用户场景

- 情侣/朋友互相监督减少手机沉迷
- 家长与青少年子女（替代系统级家长控制，可管理多个孩子的设备）
- 自我管控辅助（一人主动交出控制权，另一人协助监督）

### 1.4 关键技术决策

| 决策项 | 结论 |
|--------|------|
| TOTP 生成 | 嵌入 PeerLock 管控端 App 内 |
| 配对传输 | 静态 QR 码 + 字符串复制粘贴双通道 |
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
| UI 框架 | Material3 Scaffold + TopAppBar 处理全面屏 insets |
| 多配对架构 | 一个身份密钥，每个配对独立种子（3 TOTP 种子/会话） |

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
│   │   │   │   ├── onboarding/           # 首次启动、角色选择、配对流程、配对确认页
│   │   │   │   ├── controller/           # 管控端：设备选择、主页、审批、解锁应用、调整策略、终止码
│   │   │   │   ├── controlled/           # 被控端：主页、申请解锁、接收指令、申请解除
│   │   │   │   ├── common/               # 共享组件（TOTP 输入、图表等）
│   │   │   │   ├── stats/                # 使用统计（统一页面，双端共用）
│   │   │   │   ├── settings/             # 设置、配对信息、取消DO、种子管理
│   │   │   │   ├── navigation/           # NavHost 路由定义
│   │   │   │   └── theme/
│   │   │   ├── domain/                   # 业务逻辑层（纯 Kotlin，无 Android 依赖）
│   │   │   │   ├── pairing/              # 配对协议（密钥生成、加解密、多会话管理）
│   │   │   │   ├── totp/                 # TOTP 生成与验证（RFC 6238）+ Base32 工具
│   │   │   │   ├── policy/               # 策略引擎（时长计算、时段判断）
│   │   │   │   └── security/             # 时间偏移检测、安全模式
│   │   │   ├── data/                     # 数据层
│   │   │   │   ├── db/                   # Room + SQLCipher（多会话表结构）
│   │   │   │   ├── keystore/             # Android Keystore 封装（身份密钥 + 每会话种子）
│   │   │   │   ├── prefs/                # EncryptedSharedPreferences
│   │   │   │   └── usage/                # 使用统计数据采集与聚合
│   │   │   └── system/                   # 系统服务层
│   │   │       ├── deviceadmin/          # DeviceAdminReceiver + 策略执行
│   │   │       ├── receiver/             # BroadcastReceiver (Boot, Emergency)
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
│  │  · 角色选择    │  │  · 设备选择   │  │  · 使用报告             │ │
│  │  · 配对引导    │  │  · 策略管理   │  │  · 解锁申请             │ │
│  │  · DO 设置    │  │  · 使用报告   │  │  · 接收指令             │ │
│  │  · 电池优化   │  │  · TOTP 生成  │  │  · 策略管理             │ │
│  │  · 配对确认    │  │  · 审批请求   │  │  · 申请解除             │ │
│  └──────┬───────┘  │  · 解锁应用   │  └───────────┬────────────┘ │
│         │          │  · 调整策略   │              │              │
│         │          │  · 终止码     │              │              │
│         │          └──────┬───────┘              │              │
│         └─────────────────┴──────────────────────┘              │
│                            │                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    ViewModels                            │    │
│  │  OnboardingVM │ ControllerVM │ ControlledVM │ StatsVM    │    │
│  │  PairingVM    │ DeviceSelectVM│ SettingsVM              │    │
│  └─────────────────────────────┬───────────────────────────┘    │
│                                │                                │
├────────────────────────────────┴────────────────────────────────┤
│                        Domain Layer                             │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────┐  │
│  │ Pairing    │  │ Totp       │  │ Policy     │  │ Security │  │
│  │ Protocol   │  │ Engine     │  │ Engine     │  │ Monitor  │  │
│  │ · Multi    │  │ · 3 keys   │  │            │  │          │  │
│  │   session  │  │   /session │  │            │  │          │  │
│  └────────────┘  └────────────┘  └────────────┘  └──────────┘  │
│                                │                                │
├────────────────────────────────┴────────────────────────────────┤
│                         Data Layer                              │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ Room/SQLCipher │  │ Android        │  │ SharedPrefs      │  │
│  │                │  │ Keystore       │  │ Encrypted        │  │
│  │ · usage_records│  │ · Identity key │  │ · config         │  │
│  │ · hourly       │  │ · TOTP seeds   │  │ · session state  │  │
│  │ · daily        │  │   (per session)│  │ · error counts   │  │
│  │ · policies     │  │ · AES keys     │  │ · L2 unlock state│  │
│  │ · audit_log    │  │                │  │                  │  │
│  │ · pairing_sess │  │                │  │                  │  │
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
│  │ · polling loop │  │ · hourly agg   │  │   (配对状态检查)  │  │
│  │ · notification │  │ · daily reset  │  │ · EMERGENCY      │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 启动与路由逻辑

### 4.1 安全检查与启动路由

```
App 启动
    │
    ▼
 安全检查
    │
    ├─ 仅管控端配对 → 设备选择页（直接进入管控端工作流）
    ├─ 仅被控端配对 → 被控端主页（直接进入被控端工作流）
    └─ 未配对 或 双端都配对过 → 角色选择页
```

**设计意图**：
- 只配对了一种角色时自动跳转，减少不必要的选择步骤
- 双端都配对过或未配对时进入角色选择，让用户自行决定本次使用哪个身份
- 角色选择页显示设置图标，可在此配置 DO、电池优化等（但只显示与当前角色无关的通用设置项）

### 4.2 导航路由定义

```kotlin
object Routes {
    const val ROLE_SELECTION = "role_selection"
    const val PAIRING = "pairing/{role}"           // role: "controller" | "controlled"
    const val CONTROLLER_HOME = "controller_home"
    const val CONTROLLED_HOME = "controlled_home"
    const val DEVICE_SELECTION = "device_selection"
    const val UNLOCK_APP = "unlock_app"
    const val ADJUST_POLICY = "adjust_policy"
    const val UNLOCK_REQUEST = "unlock_request"
    const val REQUEST_APPROVAL = "request_approval"
    const val RECEIVE_COMMAND = "receive_command"
    const val STRATEGY_MANAGEMENT = "strategy_management"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val PAIRING_INFO = "pairing_info"
    const val REVOKE_DO = "revoke_do"
    const val DEVICE_OWNER_SETUP = "device_owner_setup"
}
```

### 4.3 页面导航流总表

| 从页面 | 触发操作 | 目标页面 | 可返回 |
|--------|---------|---------|--------|
| App启动(仅管控) | -- | 设备选择页 | -- |
| App启动(仅被控) | -- | 被控端主页 | -- |
| App启动(未配对/双端) | -- | 角色选择页 | -- |
| 角色选择 | 我要管控对方 | 配对页(controller) | 可 |
| 角色选择 | 我需要被管控(无ACTIVE) | DO设置页 | 可 |
| 角色选择 | 我需要被管控(有ACTIVE) | 已绑定提示 → 配对信息页 | 可 |
| 角色选择 | 设置图标 | 设置页 | 可 |
| DO设置 | 继续 | 配对页(controlled) | 否(清栈) |
| DO设置 | 返回 | 角色选择页 | -- |
| 配对页(controller) | 扫描/粘贴被控端QR | 管控端配对确认页 | 可 |
| 管控端配对确认页 | 5s后确认 | 管控端生成回执页 | -- |
| 管控端配对确认页 | 取消 | 配对页(controller) | -- |
| 管控端生成回执页 | 完成 | 设备选择页 | 否(清栈) |
| 配对页(controlled) | 扫描/粘贴响应码 | 被控端配对确认页 | 可 |
| 被控端配对确认页 | 5s后确认 | 被控端主页 | 否(清栈) |
| 被控端配对确认页 | 取消 | 配对页(controlled) | -- |
| 设备选择页 | 点击设备卡片 | 管控端主页 | 可 |
| 设备选择页 | 添加新设备 | 配对页(controller) | 可 |
| 设备选择页 | 设置图标 | 设置页 | 可 |
| 管控端主页 | 审批请求 | 审批请求页 | 可 |
| 管控端主页 | 使用统计 | 使用统计页 | 可 |
| 管控端主页 | 解锁应用 | 解锁应用页 | 可 |
| 管控端主页 | 调整策略 | 调整策略页 | 可 |
| 管控端主页 | 设置图标 | 设置页 | 可 |
| 被控端主页 | 点击应用卡片 | 申请解锁页 | 可 |
| 被控端主页 | 使用统计 | 使用统计页 | 可 |
| 被控端主页 | 接收指令 | 接收指令页 | 可 |
| 被控端主页 | 管理策略 | 策略管理页 | 可 |
| 被控端主页 | 设置图标 | 设置页 | 可 |
| 设置页 | 配对信息 | 配对信息页 | 可 |
| 设置页 | DO已设置(非被控端管控) | 取消DO页 | 可 |
| 设置页 | DO未设置 | DO设置页 | 可 |
| 设置页 | 电池优化未关闭 | DO设置(Phase2) | 可 |
| 设置页 | 切换角色 | 角色选择页 | 否(清栈) |
| 配对信息页(管控端) | 显示终止码 | 终止码页 | 可 |
| 配对信息页(被控端) | 申请解除 | 申请解除页 | 可 |
| 配对信息页 | 历史记录-申请删除种子 | 删除种子流程 | 可 |
| 所有子页 | 返回 | 上级页面 | -- |

---

## 5. 配对流程设计

### 5.1 多设备配对架构

管控端可以同时配对多个被控端设备。每个配对关系称为一个"配对会话"（Pairing Session），独立管理：

- **身份密钥**：每个设备一个，持久存在，仅在身份重置时清除
- **配对会话种子**：每个配对独立的 3 个 TOTP 种子（管理码、解锁码、终止码）
- **同设备复用**：同一设备 24 小时内可复用已有会话种子
- **种子不可删除**：直到被控端申请删除，管控端确认后才可删除

### 5.2 流程时序

```
被控端 (A)                              管控端 (B)
    │                                       │
    │ 1. 选择角色"我需要被管控"              │
    │ → DO 设置引导（两阶段，必须完成）      │
    │ → 生成 ECC 身份密钥对                  │
    │ → 私钥存入 Keystore                    │
    │ → 展示公钥二维码 + 复制数据按钮        │
    │                                       │
    │ ──── 公钥 (QR 或字符串) ──────────→   │
    │                                       │ 2. 选择角色"我要管控对方"
    │                                       │ → 扫描二维码 或 粘贴字符串
    │                                       │ → 获取 A 的公钥
    │                                       │ → 生成 3 个 TOTP 种子 (各 20B)
    │                                       │ → 生成 ECC 密钥对
    │                                       │ → 用 A 公钥加密 (种子 + B公钥)
    │                                       │ → 用 B 私钥签名
    │                                       │ → 进入管控端配对确认页（5s冷却）
    │                                       │ → 确认后生成回执
    │                                       │ → 展示回传二维码 + 复制数据按钮
    │                                       │
    │ ←──── 回传 (QR 或字符串) ────────     │
    │                                       │
    │ 3. 扫描/粘贴回传数据                   │
    │ → 进入被控端配对确认页（5s冷却）       │
    │ → 确认后用 A 私钥解密                  │
    │ → 用 B 公钥验证签名                    │
    │ → 3 个种子加密存入 Keystore            │
    │ → 保存 B 公钥                          │
    │ → 配对完成，进入被控端主界面            │
    │                                       │ → 配对完成，进入设备选择页
```

### 5.3 配对确认页（双端分离）

**管控端配对确认页**：
- 对方设备名: `Build.MANUFACTURER + Build.MODEL`
- 身份指纹: SHA256 前 8 位十六进制
- 配对会话 ID: `a3f8...`
- [确认配对] Button — 5s 冷却，灰色不可点
- [取消配对] 文字按钮，不起眼

**被控端配对确认页**：
- 对方设备名: `Build.MANUFACTURER + Build.MODEL`
- 身份指纹: SHA256 前 8 位十六进制
- 配对会话 ID: `a3f8...`
- 配对时间: 时间戳
- [确认配对] Button — 5s 冷却，灰色不可点
- [取消配对] 文字按钮，不起眼
- 确认后: 种子存入 Keystore，配对完成
- 取消后: 回到配对页，本次回执作废

### 5.4 配对传输双通道

| 通道 | 交互方式 | 适用场景 |
|------|---------|---------|
| QR 码扫描 | 相机扫码（`QrScanLauncher` 外部 Activity） | 面对面配对 |
| 字符串复制粘贴 | 复制到剪贴板 → 微信等发送 → 对方粘贴确认 | 远程配对、QR 码不便时 |

### 5.5 ECC 曲线双后端

| 条件 | 曲线 | 公钥长度 | 实现方式 |
|------|------|---------|---------|
| API 33+ | X25519 | 32 字节 | Bouncy Castle 内存管理 |
| API 30-32 | secp256r1 (NIST P-256) | 65 字节 | Keystore 原生硬件 |

Domain 层抽象为 `CryptoEngine` 接口，上层不感知曲线差异。`AdaptiveCryptoEngine` 自动选择后端。

### 5.6 配对安全要点

- 配对会话 ID 用于防止重放攻击（同一会话只能配对一次）
- 二维码有效期 5 分钟，过期需重新生成
- 配对过程中双方界面都显示对方设备标识，需人工确认一致
- 配对完成后，明文种子立即从内存清零（`Arrays.fill()`）
- `name` 字段为 `Build.MANUFACTURER + Build.MODEL`，仅用于 UX 确认，不参与加密
- **已被管控的被控端不能重新配对**：必须先解除当前配对才能发起新的配对流程

### 5.7 异常处理

| 异常场景 | 处理方式 |
|---------|---------|
| QR 码过期 | 提示"二维码已过期"，自动重新生成 |
| 签名验证失败 | 提示"配对数据可能被篡改"，中止配对 |
| 解密失败 | 提示"配对失败，请重试"，清除临时数据 |
| 会话 ID 不匹配 | 提示"请扫描正确的配对码" |
| 粘贴数据格式错误 | 提示错误信息，允许重新粘贴 |
| 被控端已有ACTIVE配对 | 提示"该设备已被管控，需先解除配对" |

---

## 6. TOTP 三密钥验证体系

### 6.1 密钥语义与 UI 命名

| 密钥 | 内部标识 | UI 名称（管控端） | UI 名称（被控端） | 用途 |
|------|---------|-----------------|-----------------|------|
| Admin Key | `setting` | "管理码" | "管理码" | 修改策略（时长、黑白名单、时段） |
| Unlock Key | `unlock` | "解锁码" | "解锁码" | 申请临时解除限制 |
| Destroy Key | `destroy` | "终止码" | "终止码" | 解除配对关系（保留 DO） |

三码不可互换——输入界面会明确标注当前操作需要哪种码。每个配对会话独立拥有三组种子。

### 6.2 TOTP 参数

```
算法: HMAC-SHA1 (RFC 6238 标准)
种子长度: 20 字节 (160 bit)
时间窗口: 30 秒
容差: ±1 窗口（当前窗口前后各一个，共 3 个有效码）
位数: 6 位数字
Base32 编码: 独立 Base32 工具类 (domain/totp/Base32.kt)
```

### 6.3 验证流程

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
  │ 与输入比对（常量时间比较）    │
  └─────────────────────────────┘
       │ 匹配         │ 不匹配
       ▼              ▼
    操作执行      错误计数 +1
                  显示"验证码错误（N/5）"
```

### 6.4 管控端 TOTP 展示

管控端主页 StatusCard 实时显示管理码和解锁码（每秒刷新），供被控端申请解锁时口头告知使用。终止码在配对信息页中通过 5s 警告后展示。

---

## 7. 签名信封协议（TOTP Envelope）

### 7.1 两种交互模式

| 模式 | 载体 | 适用场景 | 配置来源 |
|------|------|---------|---------|
| 快速码 | 6 位数字，口头告知 | 简单解锁 | 使用预设默认值 |
| 签名信封 | 二维码/字符串 | 携带详细信息、需审批、需调整 | 信封内 cfg 字段 |

### 7.2 信封加密协议

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
5. Base64 编码 → ~270-340 字符
```

### 7.3 防篡改与防重放

- ECDSA 签名覆盖密文，任何修改都会导致验签失败
- 信封内含时间戳 + 会话 ID，被控端验证：
  - 时间戳在 ±5 分钟内（允许离线场景下略有延迟）
  - 同一信封不可重复使用（记录已消费的 ts+sid 组合）
- 过期信封扫码后提示"此码已过期，请向管控方索取新码"

---

## 8. 双向请求/响应信封协议

### 8.1 请求信封（被控端 → 管控端）

```kotlin
data class RequestEnvelope(
    val v: Int = 1,
    val type: String,                  // "unlock" / "config" / "stats"
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
    val targetAppDetail: AppUsageDetail?,
)
```

### 8.2 管控端审批流程

```
管控端扫描/粘贴请求信封
         │
         ▼
  验证签名 + 验证会话 + 检查时效
         │
         ▼
  ┌─────────────────────────────────────────┐
  │  审批界面 (RequestApprovalScreen)        │
  │                                         │
  │  IDLE → SCANNING → REVIEWING            │
  │                          │              │
  │                    ┌─────┴─────┐        │
  │                    │           │        │
  │                 批准         拒绝        │
  │                    │           │        │
  │                    ▼           ▼        │
  │              SHOWING_RESPONSE           │
  │                    │                    │
  │                    ▼                    │
  │                  IDLE（重置）            │
  │                                         │
  │  REVIEWING 界面元素:                     │
  │  · 请求详情: 类型 应用 时长              │
  │  · 设备状态: 屏幕时间 已暂停数           │
  │  · 附带统计: 今日使用图表 (如有)         │
  │  · 调整参数: 时长 Slider 5-180分         │
  │  · 模式 FilterChip: 累计/单次           │
  │  · [拒绝] | [批准]                       │
  └─────────────────────────────────────────┘
```

### 8.3 响应信封（管控端 → 被控端）

```kotlin
data class ResponseEnvelope(
    val v: Int = 1,
    val type: String,                  // "unlock_resp" / "config_resp" / "stats_resp"
    val sessionId: String,
    val requestId: String,
    val timestamp: Long,
    val approved: Boolean,
    val payload: ResponsePayload,
)
```

### 8.4 频率限制

| 操作 | 频率限制 |
|------|---------|
| 解锁请求 | 30 秒 1 次 |
| 配置变更请求 | 30 秒 1 次 |
| 快速码输入 | 30 秒 1 次 + 错误 5 次锁定 30 秒 |
| 信封有效期 | 5 分钟 |
| 响应信封有效期 | 5 分钟 |

---

## 9. 策略管理（双端双模式）

### 9.1 策略管理架构

策略管理页在管控端和被控端共享相同的 UI 结构，但权限和操作不同：

| | 管控端 | 被控端 |
|---|---|---|
| 入口 | 主页 → [调整策略] | 主页 → [管理策略] |
| Tab | 应用策略 + 配置参数 | 应用策略 + 配置参数 |
| 模式 | 管理码模式 / 申请模式 | 管理码模式 / 申请模式 |
| 管理码模式 | 验证管理码后直接修改 | 验证管理码后直接修改 |
| 申请模式 | 生成调整指令(QR/字符串)，被控端确认后执行 | 生成配置变更请求(QR/字符串)，管控方审批后执行 |

### 9.2 双Tab结构

**应用策略 Tab**：
- 当前策略列表（应用名、时长、时段）
- FilterChip 多选目标应用
- 或 [选择全部策略] 全选

**配置参数 Tab**：
- 默认解锁时长（可调）
- 申请模式默认时长（可调）
- 统计默认附带范围（可调）

### 9.3 双模式

**管理码模式**：
- 选中项可直接修改参数
- 修改高亮显示本次变更
- [不保存] [保存修改]
- 保存需输入管理码 6位 TotpInputField 验证

**申请模式**：
- 选中项调整参数，高亮显示本次变更
- [不保存] [生成调整指令/请求]
- 管控端：生成 QR/字符串 → 被控端接收指令页确认执行
- 被控端：生成 QR/字符串 → 管控端审批页审批

### 9.4 管控端 vs 被控端策略管理差异

**管控端**：
- 管理码模式：验证后直接保存并同步到被控端（通过生成指令）
- 申请模式：生成调整指令（QR/字符串），被控端确认后执行

**被控端**：
- 管理码模式：验证后直接保存本地策略
- 申请模式：生成配置变更请求，管控方审批后返回响应，被控端执行

---

## 10. 解锁应用（管控端）

### 10.1 页面结构

管控端主页 → [解锁应用] → 解锁应用页

**页面元素**：
- 选择受限应用 FilterChip（多选必选）
- 解锁时长 5分钟（默认，可配置）
- [生成解锁指令] → QR/字符串
- 被控端收到后自动执行（无需确认）

---

## 11. 申请解锁（被控端）

### 11.1 两种模式

UnlockRequestScreen 支持两种可切换模式：

**快速码模式（默认）**：
- 选择受限应用 FilterChip（多选必选）
- 输入管控方提供的 6 位 TOTP 解锁码
- 6 位 `TotpInputField` 自动触发验证
- 解锁时长可配置（默认 5 分钟）
- [切换到申请模式] 按钮

**申请模式**：
- 选择要解锁的应用 FilterChip（多选必选）
- 请求时长可配置（默认 5 分钟）
- ☐ 附带统计数据（默认附带范围可配置）
- 顶部栏确认: [生成申请码]
- 生成后显示 `QrCodeDisplay` + "请让管控方扫描此二维码"
- [复制申请数据] 按钮
- [切换到快速码模式] 按钮

---

## 12. 接收指令页（被控端）

### 12.1 概述

被控端通过接收指令页接收管控端主动发送的指令（解锁、调整/策略、统计请求）。

### 12.2 页面结构

```
接收指令页 (被控端)
    │
    ├─ IDLE: [扫描指令] 相机 / [粘贴指令数据] 输入框 + 确认
    │
    ├─ 解锁指令:
    │   目标应用: com.app1
    │   解锁时长: 5分钟
    │   [确认执行] Button → 自动执行解锁
    │   [拒绝] Button
    │
    ├─ 调整/策略指令:
    │   目标应用: com.app1
    │   新时长/时段: xxx
    │   或 批量策略配置变更
    │   [确认执行] Button
    │   [拒绝] Button
    │
    └─ 统计请求:
        时间范围: 最近7天
        自动执行: 生成统计报告
        展示 QR/字符串 + [复制数据]
```

### 12.3 指令类型

| 指令类型 | 来源 | 被控端行为 |
|---------|------|-----------|
| 解锁指令 | 管控端 → 解锁应用页生成 | 自动执行（无需确认） |
| 调整/策略指令 | 管控端 → 调整策略页生成 | 需确认后执行 |
| 统计请求 | 管控端 → 统计页请求统计 | 自动生成报告 |

---

## 13. 统一统计页

### 13.1 概述

管控端和被控端共用同一个统计页面，根据角色显示不同的操作按钮。

### 13.2 页面结构

```
使用统计页
TopAppBar: 返回箭头 + 设备名
右上角操作:
  管控端: [请求统计] → 生成QR/字符串 → 审批接收
  被控端: [发送统计] → 生成QR/字符串/文件

[今天] [本周] [本月] FilterChip

今天视图:
  小时柱状图 (Canvas)
  X轴 0-23时  Y轴 分钟
  点击柱子下钻到30秒详情

本周/本月视图:
  日折线图 (Canvas)
  X轴 日期  Y轴 分钟
  点击数据点下钻

应用明细:
  com.app1  120分  启动8次

时间范围: 最近1天/7天/30天/全部 FilterChip
数据量大时QR不可承载则切换为加密文件导出
```

### 13.3 双端差异

| | 管控端 | 被控端 |
|---|---|---|
| 右上角按钮 | [请求统计] | [发送统计] |
| 操作结果 | 生成 QR/字符串 → 通过审批功能接收 | 生成 QR/字符串/文件 → 发送给管控方 |
| 数据来源 | 当前设备（管控端自身统计） | 当前设备（被控端自身统计） |

---

## 14. 设备选择页（管控端）

### 14.1 概述

管控端配对完成后进入设备选择页，可管理多个已配对的被控设备。

### 14.2 页面结构

```
设备选择页
TopAppBar: PeerLock 管控端 + 设置图标

已配对设备列表:
  · Galaxy S24  ACTIVE  身份指纹
  · Pixel 7     WAITING 身份指纹

[添加新设备] Button
[设置] IconButton

长按设备卡片: 重新展示回执QR / 查看信息
```

### 14.3 设备状态

| 状态 | 含义 |
|------|------|
| ACTIVE | 已配对，被控端已确认 |
| WAITING | 已配对，等待被控端确认回执 |

---

## 15. 管控端主页

### 15.1 页面结构

```
管控端主页
TopAppBar: 返回箭头 + 设备名 + 设置图标
返回: 设备选择页

StatusCard:
  已配对  设备名  策略巡检运行中
  验证码每秒刷新: 管理码  解锁码

策略列表: com.app1  com.app2

[审批请求] Button
[使用统计] OutlinedButton
[解锁应用] OutlinedButton
[调整策略] OutlinedButton
```

---

## 16. 被控端主页

### 16.1 页面结构

```
被控端主页
TopAppBar: PeerLock 被控端 + 设置图标
返回: 无（顶级页）

StatusCard:
  已配对  接受控制方管理
  管控方: 设备名 (身份指纹摘要)

受限应用: com.app1  com.app2

[使用统计] OutlinedButton
[接收指令] OutlinedButton
[管理策略] OutlinedButton
```

---

## 17. 配对信息页

### 17.1 概述

配对信息页位于设置 → 配对信息，使用 `FLAG_SECURE` 防截屏。根据当前角色显示不同内容，未配对角色的选项不显示。

### 17.2 管控端视角

```
配对信息页 (FLAG_SECURE)
当前配对:
  对方设备名: Galaxy S24
  身份指纹: B7:1E:...
  配对时间: 2026-05-02
  会话ID: a3f8-...
  状态: ACTIVE

[查看验证码]
[申请删除种子] — 被控端确认后可操作

底部红色: [显示终止码]
  → 5s警告 → 终止码页（实时刷新显示）

历史配对记录:
  · Pixel 7  REVOKED  可申请删除种子
  · 管控端: 可查看收到的删除申请及处理结果
  · [归档记录] 查看已归档会话
```

### 17.3 被控端视角

```
配对信息页 (FLAG_SECURE)
当前配对:
  对方设备名: Galaxy S24
  身份指纹: B7:1E:...
  配对时间: 2026-05-02
  会话ID: a3f8-...
  状态: ACTIVE

底部红色: [申请解除]
  → 5s警告 → 输入终止码
  → (L2高级解除: 版本号连点7次后此处展开)

历史配对记录:
  · Pixel 7  REVOKED  可申请删除种子
  · [归档记录] 查看已归档会话
```

---

## 18. 终止码与解除配对

### 18.1 管控端 — 显示终止码

入口：设置 → 配对信息 → 底部红色 [显示终止码]

```
终止码页 (管控端)

⚠️ 警告信息:
终止码用于解除与被控端的配对关系
使用后被控端的管控将被解除
加密材料将被清除
此操作不可逆

[确认显示] Button — 5s冷却灰色不可点
    │
    ▼
实时显示终止码:
  6位数字 每30秒刷新
  当前设备: Galaxy S24
  [复制终止码] Button
  [返回] Button
```

### 18.2 被控端 — 申请解除

入口：设置 → 配对信息 → 底部红色 [申请解除]

```
申请解除 (被控端)

⚠️ 警告信息:
申请解除将解除与管控方的配对关系
管控方的管控将被解除
加密材料将被清除
Device Owner 权限保留

[确认] Button — 5s冷却灰色不可点
    │
    ▼
输入终止码:
  管控方提供的终止码
  6位 TotpInputField
  验证通过后立刻执行解绑
  清除加密材料 返回主页
  提示: 配对已解除

(L2高级解除: 设置版本号连点7次后此处展开显示)
```

### 18.3 L2 高级解除

入口：设置页 → 版本号连点 7 次（无任何提示）

- 解锁后在被控端配对信息页的申请解除区域内展开显示
- **5 分钟窗口期**：超过 5 分钟自动恢复隐藏，需重新点击版本号
- 需 ADB 命令移除 DO 和密钥

```
L2 高级解除 (版本号连点7次解锁 5分钟窗口期)

[开始验证] Button
    │
    ▼
挑战码红色 [挑战码] 输入框
[验证] Button
    │ 通过
    ▼
ADB命令 monospace
执行后: L2 解除已执行
```

---

## 19. 种子删除与归档

### 19.1 被控端申请删除种子

入口：设置 → 配对信息 → 历史配对记录 → REVOKED/WAITING 状态的会话

- 仅限 REVOKED 或等待状态的会话，正在被管控的无法操作（只能申请解除）
- 生成删除请求：包含会话 ID + 身份签名
- 展示 QR/字符串发送给管控方

### 19.2 管控端处理删除申请

管控端收到删除申请后：
- 自动删除对应会话种子
- 移除该设备配对记录
- 通知告知已删除

### 19.3 归档

管控方删除种子后，被控端可选择归档隐藏该会话记录。归档后仍可查看，仍可发删除申请。

---

## 20. Device Owner 管理

### 20.1 DO 设置流程（被控端必须）

被控端必须完成 DO + 电池优化设置才能进入配对。**无跳过选项**。

```
Phase 1: 设置 Device Owner
├─ 检查 isDeviceOwner
├─ 已设置 → 显示 ✓ + [继续] 按钮
├─ 未设置 → 展示 ADB 命令引导
│   ├─ 无线 ADB 配对引导（展开/收起）
│   ├─ 传统 USB ADB 命令 + 复制按钮
│   ├─ [检查设置状态] 按钮（带状态反馈）
│   └─ (无跳过选项，被控端必须完成)
└─ 设置成功 → advanceToDoComplete()
      │
      ▼
电池优化检查
├─ 已豁免 → 直接进入配对
└─ 未豁免 → Phase 2
      │
      ▼
Phase 2: 电池优化白名单
├─ 已豁免 → 显示 ✓ + [继续] 按钮
├─ 未豁免 → 展示说明 + 操作按钮
│   ├─ [关闭电池优化] → 跳转系统对话框
│   ├─ [检查是否已关闭] → onResume 重新检查
│   └─ (无跳过选项，被控端必须完成)
└─ 完成 → 进入配对流程
```

### 20.2 DO 软件内取消

设置页中，Device Owner 状态显示为可点击时，可跳转取消 DO 页面。

**前置条件**：当前非被控端被管控配对状态（即管控端、未配对、已解除配对）

**取消 DO 页面**：
```
取消 Device Owner
TopAppBar: 返回箭头 + 取消DO

⚠️ 警告: 取消后应用限制功能将失效
需重新ADB设置才能恢复

[确认取消] 红色Button — 10s冷却灰色不可点
[返回]
```

### 20.3 设置页 DO 状态显示

| 状态 | 显示 | 可操作 |
|------|------|--------|
| 未设置 | ✗ 未设置 | 可点击 → DO设置流程 |
| 已设置 + 被管控中 | ✓ 已设置 | 灰色不可操作 |
| 已设置 + 非被管控 | ✓ 已设置 | 可点击 → 取消DO页 |

---

## 21. 设置页面

### 21.1 完整设置项

```
设置页
TopAppBar: 返回箭头 + 设置

Device Owner:  ✓已设置(灰) / ✗未设置(可点击) / 非被控端管控时可点击取消DO(10s确认)
电池优化:      ✓已关闭(灰) / ✗未关闭(可点击)
[配对信息]     点击进入详情页
主题模式:      [跟随系统] [浅色] [深色] FilterChip
语言:          [中文] [English] FilterChip
[切换角色]     回角色选择页（原配置保持运行）
[身份重置]     红色  条件满足时可用  10s确认
版本号:        连点7次解锁L2高级解除（无任何提示）
```

### 21.2 设置项角色感知

- 角色选择页进入设置时：只显示通用设置（主题、语言等），DO/电池优化/配对信息等依赖身份的选项不显示
- 管控端/被控端进入设置时：显示完整设置项

### 21.3 L2 解锁机制

设置页底部显示当前版本号。连点 7 次解锁 L2 高级解除功能：
- **无任何提示**：连点过程中不显示进度或反馈
- **5 分钟窗口期**：超过自动恢复隐藏
- 解锁后在被控端配对信息页的申请解除区域内展开 L2 操作

---

## 22. 策略执行与应用暂停

### 22.1 策略数据模型

```kotlin
data class RestrictionPolicy(
    val id: Long = 0,
    val targetPackage: String,
    val dailyLimitMinutes: Int?,
    val allowedTimeStart: LocalTime?,
    val allowedTimeEnd: LocalTime?,
    val isBlacklist: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val lastModified: Long
)
```

### 22.2 策略执行状态机

```
              ┌─────────────┐
              │   MONITORING │ ← 正常监控，记录使用时长
              └──────┬──────┘
                     │ 触发条件（超时 / 非允许时段）
                     ▼
              ┌─────────────┐
              │  SUSPENDED   │ ← 应用被暂停，图标变灰
              └──────┬──────┘
                     │ 用户输入解锁码 / 收到解锁指令 / 收到批准的解锁响应
                     ▼
              ┌─────────────┐
              │  UNLOCKED    │ ← 临时解锁，恢复使用
              └──────┬──────┘
                     │ 解锁期满 / 再次超时
                     ▼
              └→ SUSPENDED（回到暂停）
```

### 22.3 巡检逻辑（前台服务，30 秒间隔）

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

---

## 23. 时间偏移检测与安全模式

### 23.1 核心思路

不依赖系统时间做判断，自维护一个"真实时间基准"：

```
真实时间 = 系统时间 + 已知偏移量(offset)
```

### 23.2 NTP 同步策略

| 时机 | 原因 |
|------|------|
| App 启动时 | 获取初始 offset |
| 检测到系统时间突变时 | 确认是否真实偏移 |
| 每 6 小时定期 | 防止长周期时钟漂移 |
| 网络恢复时 | 安全模式下恢复联网后立即校验 |

### 23.3 安全模式行为

- 所有受限应用立即 SUSPEND（包括 UNLOCKED 状态的）
- 状态栏通知："⚠️ PeerLock 检测到时间异常，设备已锁定"
- 解锁方式：网络恢复自动校准 / 手动输入管理码

---

## 24. 紧急逃生通道

### 24.1 三级逃生体系

```
┌─────────────────────────────────────────────────────────────┐
│  L1  终止码（配对信息页正常入口）                               │
│      需要：终止码密钥                                        │
│      效果：解绑配对，保留 DO，保留数据                          │
│      门槛：最低（有密钥即可）                                   │
│                                                             │
│  L2  一次性 ADB broadcast（版本号7次连点进入）                  │
│      需要：连点版本号7次 → 5分钟窗口 → 显示挑战码 → 输入验证    │
│      效果：解除 DO + 清除密钥，保留数据                         │
│      门槛：中等（多层隐藏 + 一次性命令 + 时间窗口）              │
│                                                             │
│  L3  固定 ADB 命令（仅开发文档）                                │
│      需要：知道命令格式 + 电脑                                  │
│      效果：pm clear 全清                                      │
│      门槛：最高（需要主动查阅文档）                              │
└─────────────────────────────────────────────────────────────┘
```

### 24.2 各级操作对比

| | L1 终止码 | L2 一次性 broadcast | L3 固定命令 |
|---|---|---|---|
| 入口 | 配对信息页 | 设置版本号7次 → 配对信息页展开 | 仅开发者文档 |
| 需要密钥 | 是（终止码） | 否 | 否 |
| 需要 App 运行 | 是 | 是 | 否 |
| 需要电脑 | 否 | 是 | 是 |
| DO 状态 | 保留 | 解除 | 解除 |
| 密钥材料 | 销毁 | 销毁 | 全清 |
| 配对关系 | 解除 | 解除 | 全清 |
| 使用数据 | 保留 | 保留 | 全清 |
| 命令是否变化 | 每次不同（信封） | 每次不同（nonce） | 固定不变 |

---

## 25. 前台服务与后台保活

### 25.1 前台服务职责

```
PeerLockService (Foreground Service)
  ├── 策略巡检（30s 间隔，AlarmManager 精确闹钟）
  ├── 时间偏移检测
  ├── 使用统计采集（UsageStatsManager）
  └── 状态栏通知更新
```

### 25.2 保活策略

| 层级 | 机制 | 说明 |
|------|------|------|
| L1 | 前台服务通知 | 系统优先级提升 |
| L2 | Device Owner 特权 | 系统不会杀死 DO 应用的前台服务 |
| L3 | 电池优化白名单 | DO 可自动加入白名单 |
| L4 | 重启自启 | BOOT_COMPLETED + Application.onCreate() 启动服务 |

### 25.3 重启恢复流程

```
设备重启
    │
    ▼
系统启动 → BOOT_COMPLETED 广播
    │
    ▼
BootReceiver.onReceive()
    ├─ 检查 securePrefs.isPaired
    ├─ 未配对 → 跳过，不启动服务
    └─ 已配对 → startForegroundService(PeerLockService)
```

---

## 26. 使用统计与数据可视化

### 26.1 数据采集与聚合

```
30 秒巡检 → raw_records（增量写入）
每小时整点 → usage_hourly_summary（从 raw 聚合）
每日 00:00 → usage_daily_summary（从 hourly 聚合）
```

### 26.2 统计页面交互层级

**今天（TODAY）**：
- `HourlyBarChart`（Canvas 绘制 0-23 时柱状图）
- X 轴标签：0/6/12/18/23
- Y 轴：分钟
- 点击柱子：选中该小时，下方显示 30 秒级使用详情明细

**本周/本月（WEEK/MONTH）**：
- `DailyLineChart`（Canvas 折线图 + 数据点圆圈）
- X 轴：日期，Y 轴：分钟
- 点击数据点：选中日期，下方显示该日小时分布柱状图

**应用明细**：
- 包名 + 总时长 + 启动次数

---

## 27. 关键接口定义

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

interface PairingSessionManager {
    suspend fun createSession(peerPublicKey: ByteArray): PairingSession
    suspend fun getSession(sessionId: String): PairingSession?
    suspend fun getActiveSessions(): List<PairingSession>
    suspend fun revokeSession(sessionId: String)
    suspend fun archiveSession(sessionId: String)
    suspend fun deleteSession(sessionId: String)  // 需被控端申请
    suspend fun requestSeedDeletion(sessionId: String): SeedDeletionRequest
}

interface PolicyEngine {
    suspend fun evaluate(packageName: String, currentTime: Long): PolicyAction
    suspend fun suspendApp(packageName: String)
    suspend fun unsuspendApp(packageName: String)
    suspend fun resetDailyUsage()
    fun recordUnlock(packageName: String, durationMinutes: Int)
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

## 28. 技术风险评估

### 28.1 高风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 厂商 ROM 杀后台 | 前台服务被杀，策略失效 | DO 特权 + 电池优化白名单 + 重启自启 |
| Device Owner 行为差异 | 不同厂商 API 行为不一致 | 重点测试 MIUI/ColorOS/HarmonyOS |
| Keystore 不可用 | 加密材料无法存储 | 降级到 EncryptedSharedPreferences |
| 系统时间被篡改 | TOTP 验证失效 | 自维护 offset + NTP 校验 |

### 28.2 中风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| QR 码数据超限 | 配对/指令传输失败 | 压缩 JSON / 字符串传输备用通道 / 文件导出 |
| UsageStats 延迟 | 统计不准确 | 30 秒轮询 + 增量计算补偿 |
| 时区切换 | offset 异常 | 检测时区变化，自动重新同步 |

---

## 29. 关键验收场景

```
场景 1：完整生命周期（多设备）
  管控端配对设备A → 配对设备B → 设备选择页切换
  → 设备A主页设置策略 → 设备B主页设置策略
  → 设备A解锁应用 → 设备B调整策略
  → 解除设备A配对 → 归档 → 删除种子

场景 2：防逃避
  尝试卸载 PeerLock → 被系统阻止
  强制停止 PeerLock → 已暂停应用仍不可用
  重启设备 → 策略自动恢复
  修改系统时间 → 触发安全模式
  被控端尝试重新配对 → 被阻止（已有ACTIVE配对）

场景 3：逃生通道
  终止码输入（配对信息页） → 5s警告 → 确认 → 解绑（DO 保留）
  高级解除 → 设置版本号7次 → 5分钟窗口 → 挑战码验证 → 一次性命令 → 解除 DO
  直接 ADB 命令 → pm clear → 完全重置

场景 4：策略管理双模式
  管控端管理码模式 → 验证 → 直接修改 → 同步到被控端
  管控端申请模式 → 生成指令 → 被控端确认执行
  被控端管理码模式 → 验证 → 直接修改本地
  被控端申请模式 → 生成请求 → 管控端审批 → 响应执行

场景 5：统计统一
  被控端查看统计 → 右上角发送统计 → 生成QR/文件
  管控端查看统计 → 右上角请求统计 → 通过审批接收

场景 6：DO管理
  被控端: DO + 电池优化必须完成（无跳过）→ 配对
  管控端: 设置页可取消DO（非被控端管控时）→ 10s确认
  设置页DO状态: 三种显示（未设置/已设置不可操作/已设置可取消）

场景 7：种子删除
  被控端历史记录 → REVOKED会话 → 生成删除请求
  管控端收到 → 自动删除种子+记录
  被控端 → 归档隐藏

场景 8：启动路由
  仅管控端配对 → 直接进入设备选择页
  仅被控端配对 → 直接进入被控端主页
  未配对/双端 → 角色选择页
```

---

## 30. P0 / P1 功能边界

### 30.1 P0（MVP 必须交付）

| # | 功能 | 验收标准 |
|---|------|---------|
| 1 | Device Owner 初始化（被控端必须） | 无线/有线 ADB 双通道，无跳过选项 |
| 2 | DO 软件内取消 | 非被控端管控时可用，10s确认 |
| 3 | 电池优化白名单（被控端必须） | 设置后引导关闭，无跳过选项 |
| 4 | 防卸载、防强制停止 | 系统设置无法卸载；强制停止后策略仍生效 |
| 5 | 应用黑白名单 + 强制暂停 | setPackagesSuspended 正确执行 |
| 6 | 使用时长统计 | 30 秒粒度采集；与系统设置误差 < 5% |
| 7 | 按时段限制 | 非允许时段自动 SUSPEND |
| 8 | TOTP 三密钥验证（每会话独立） | 独立种子；±1 窗口容差；5次错误锁30秒 |
| 9 | 签名信封协议 | ECDH + AES-GCM + ECDSA；防重放 |
| 10 | 双向请求/响应 | 被控方发请求，管控方审批返回响应 |
| 11 | 多设备配对 | 管控端可配对多个被控端，设备选择页管理 |
| 12 | 配对流程（双通道 + 双确认页） | QR + 字符串；管控端/被控端各自独立确认页 |
| 13 | 加密存储 | Keystore + SQLCipher + EncryptedSharedPreferences |
| 14 | 策略管理双模式双Tab | 管理码/申请模式；应用策略/配置参数Tab |
| 15 | 解锁应用（管控端） | 多选应用 + 生成指令，被控端自动执行 |
| 16 | 申请解锁双模式（被控端） | 快速码/申请模式；多选应用必选 |
| 17 | 接收指令页（被控端） | 解锁自动执行/调整需确认/统计自动生成 |
| 18 | 统一统计页 | 双端共用；管控端请求统计/被控端发送统计 |
| 19 | 配对信息页（角色感知） | 管控端：终止码/删除种子；被控端：申请解除 |
| 20 | 终止码 | 配对信息页入口；5s警告；实时刷新 |
| 21 | 申请解除 + L2 | 5s警告 → 输入终止码；版本号7次 → 5分钟窗口 |
| 22 | 种子删除/归档 | 被控端申请 → 管控端自动删除 → 可归档 |
| 23 | 启动路由 | 单角色自动跳转/双角色或未配对→角色选择 |
| 24 | 已绑定提示 | 有ACTIVE配对时阻止重新配对，引导解除 |
| 25 | 时间偏移检测 | 自维护 offset + NTP；无网锁定 |
| 26 | 前台服务保活 | DO 特权 + 常驻通知 + 重启自启 |
| 27 | 数据可视化 | 小时柱状图/日折线图 + 点击下钻 |
| 28 | 全面屏适配 | Scaffold + TopAppBar 处理 insets |
| 29 | 导航返回支持 | 所有子页面可返回上级 |

### 30.2 P1（后续迭代）

| # | 功能 | 说明 |
|---|------|------|
| 1 | 本地 VPN 网络过滤 | 按应用过滤网络；VPN 独占冲突处理 |
| 2 | 对等双向控制 | 利用双向公钥；新增 UI；冲突解决 |
| 3 | 导入导出 | 加密签名导出；安全导入与合并 |
| 4 | 远程实时修改 | 轻量级服务器；公钥加密通信 |
| 5 | 动态二维码视频传输 | 多帧动画 QR |
| 6 | 加密统计报告文件 | 大数据量时加密文件导出 |
| 7 | 位置/轨迹监控 | 数据库预留字段；合规评估 |
| 8 | 存储管理设置 UI | 用户可配置数据保留期 |
| 9 | 审计日志自动清理 | 90 天定期清理 |
