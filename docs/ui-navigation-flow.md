# PeerLock UI 导航流程图

> **Version**: 3.0
> **更新日期**: 2026-05-11
> **配合**: [ui-pages-detail.md](ui-pages-detail.md) 详细页面图使用
> **变更**: 多配对设备选择、配对确认流程、申请解绑、单向控制、设置页扩展

---

## 导航架构总图

```mermaid
flowchart TD
    APP["App 启动"] --> CHECK{isPaired?}

    CHECK -->|未配对| RS[角色选择页]
    CHECK -->|已配对 + 管控端| DEV_SEL[设备选择页]
    CHECK -->|已配对 + 被控端| CDH[被控端主页]

    RS -->|我要管控对方| PAIR_CTRL[配对页 controller]
    RS -->|我需要被管控| DO_SETUP[DO设置页]

    DO_SETUP -->|Phase 1: DO设置| DO_P1[Device Owner 设置]
    DO_SETUP -->|Phase 2: 电池优化| DO_P2[电池优化白名单]
    DO_P1 -->|完成| DO_P2
    DO_P2 -->|完成| PAIR_CTRLLED[配对页 controlled]

    PAIR_CTRL -->|配对完成| CONFIRM_CTRL[配对确认页 controller]
    PAIR_CTRLLED -->|配对完成| CONFIRM_CTRLLED[配对确认页 controlled]

    CONFIRM_CTRL -->|5s冷却+确认| DEV_SEL
    CONFIRM_CTRLLED -->|5s冷却+确认| CDH

    DEV_SEL -->|选择设备| CH[管控端主页]
    DEV_SEL -->|添加新设备| PAIR_CTRL

    CH -->|审批请求| RA[审批请求页]
    CH -->|紧急逃生| EM[紧急逃生页]
    CH -->|使用统计| ST[使用统计页]
    CH -->|单向控制| SC[单向控制页]
    CH -->|设置图标| SETTINGS[设置页]

    CDH -->|受限应用卡片| UR[申请解锁页]
    CDH -->|使用统计| ST
    CDH -->|发送统计| PUSH_STATS[发送统计页]
    CDH -->|申请解绑| UNBIND_REQ[申请解绑页]
    CDH -->|设置图标| SETTINGS

    UR -->|快速码| QUICK[输入解锁码]
    UR -->|申请模式| REQ[生成申请码]
    RA -->|扫描/粘贴请求| REVIEW[审批详情]
    RA -->|单向解锁| AUTO[自动执行]

    SETTINGS -->|Device Owner| SET_DO[DO状态检测]
    SETTINGS -->|电池优化| SET_BAT[电池优化检测]
    SETTINGS -->|配对信息| SET_PAIR[配对信息页]
    SETTINGS -->|切换角色| RS
    SETTINGS -->|身份重置| RESET[身份重置确认]

    style APP fill:#E3F2FD,color:#000
    style RS fill:#E3F2FD,color:#000
    style DEV_SEL fill:#C8E6C9,color:#000
    style CH fill:#E8F5E9,color:#000
    style CDH fill:#FFF3E0,color:#000
    style SETTINGS fill:#EDE7F6,color:#000
    style EM fill:#FFEBEE,color:#000
    style UNBIND_REQ fill:#FFEBEE,color:#000
```

---

## 1. 启动与角色判断

```mermaid
flowchart TD
    APP["App 启动"] --> CHECK{isPaired?}

    CHECK -->|未配对| RS["角色选择页<br/>TopAppBar: 无<br/>[我要管控对方] Button<br/>[我需要被管控] OutlinedButton"]
    CHECK -->|已配对 + role=controller| DEV_SEL["设备选择页<br/>展示所有已配对被控端设备列表"]
    CHECK -->|已配对 + role=controlled| CDH["被控端主页"]

    style APP fill:#E3F2FD,color:#000
    style RS fill:#E3F2FD,color:#000
```

---

## 2. 被控端初始化流程

```mermaid
flowchart TD
    RS["角色选择页"] -->|点击 我需要被管控| SEC_CHECK{身份安全检查}

    SEC_CHECK -->|该设备已是被控端<br/>且有 ACTIVE 配对| BLOCKED["提示: 该设备已被管控<br/>需先申请解绑才能重新配对<br/>[申请解绑] Button<br/>[返回] Button"]
    SEC_CHECK -->|无 ACTIVE 配对| DO_SETUP["DO设置页"]

    DO_SETUP --> P1["Phase 1: DO 设置<br/>TopAppBar: 返回箭头 + 标题<br/>检查 isDeviceOwner<br/>已设置 → ✓ + [继续]<br/>未设置 → ADB 命令引导 + [检查状态]<br/>[跳过(功能受限)]"]

    P1 -->|advance| BAT_CHECK{isBatteryExempt?}
    BAT_CHECK -->|是| PAIR["配对页 controlled"]
    BAT_CHECK -->|否| P2["Phase 2: 电池优化<br/>[关闭电池优化] → 系统对话框<br/>[检查是否已关闭]<br/>[跳过(后台巡检可能不稳定)]"]

    P2 -->|完成或跳过| PAIR

    PAIR --> PAIRING["配对流程<br/>展示 QR + [复制配对数据]<br/>[扫描控制方回执]<br/>[粘贴回执数据]"]

    PAIRING -->|回执验证通过| CONFIRM["配对确认页<br/>显示对方设备名 + 身份指纹<br/>[确认配对] 5s冷却后可点击<br/>[取消配对] 文字按钮"]

    CONFIRM -->|确认| CDH["被控端主页"]
    CONFIRM -->|取消| PAIR

    BLOCKED -->|申请解绑| UNBIND["申请解绑流程"]

    style RS fill:#E3F2FD,color:#000
    style DO_SETUP fill:#FCE4EC,color:#000
    style CONFIRM fill:#E8F5E9,color:#000
    style BLOCKED fill:#FFCDD2,color:#000
```

---

## 3. 管控端初始化流程

```mermaid
flowchart TD
    RS["角色选择页"] -->|点击 我要管控对方| PAIR["配对页 controller<br/>TopAppBar: 返回箭头 + 配对"]

    PAIR --> SCAN["扫描/粘贴被控端QR<br/>[扫描二维码] Button<br/>[粘贴配对数据] 展开输入框+确认"]

    SCAN -->|解析成功| CONFIRM["配对确认页<br/>显示对方设备名 + 身份指纹<br/>[确认配对] 5s冷却后可点击<br/>[取消配对] 文字按钮"]

    CONFIRM -->|确认| GEN["生成回执<br/>3个TOTP种子 + 加密打包<br/>展示回执QR + [复制回执数据]"]

    CONFIRM -->|取消| PAIR

    GEN -->|被控端确认后| DEV_SEL["设备选择页"]

    style RS fill:#E3F2FD,color:#000
    style PAIR fill:#F3E5F5,color:#000
    style CONFIRM fill:#E8F5E9,color:#000
    style DEV_SEL fill:#C8E6C9,color:#000
```

---

## 4. 设备选择页（管控端多配对）

```mermaid
flowchart TD
    DEV_SEL["设备选择页<br/>TopAppBar: PeerLock 管控端 + 设置图标<br/>设备列表: 每个已配对被控端<br/>  · 设备名称 (Build.MANUFACTURER+MODEL)<br/>  · 身份指纹摘要<br/>  · 配对状态 (ACTIVE/REVOKED)<br/>[添加新设备] Button<br/>[设置] IconButton"]

    DEV_SEL -->|点击设备卡片| CH["管控端主页<br/>当前设备: Galaxy S24"]
    DEV_SEL -->|添加新设备| PAIR["配对页 controller"]
    DEV_SEL -->|设置图标| SETTINGS["设置页"]

    CH -->|返回| DEV_SEL

    style DEV_SEL fill:#C8E6C9,color:#000
    style CH fill:#E8F5E9,color:#000
```

---

## 5. 管控端主页

```mermaid
flowchart TD
    CH["管控端主页<br/>TopAppBar: 返回箭头 + 设备名 + 设置图标<br/>返回: 设备选择页<br/><br/>StatusCard: 已配对 设备名 策略巡检运行中<br/><br/>功能区:<br/>[审批请求] Button<br/>[紧急逃生] OutlinedButton<br/>[使用统计] OutlinedButton<br/>[单向控制] OutlinedButton<br/><br/>验证码区 每秒刷新:<br/>管理码 解锁码 终止码<br/>(仅当前设备的3个种子)<br/><br/>策略列表: com.app1 com.app2"]

    CH -->|审批请求| RA[审批请求页]
    CH -->|紧急逃生| EM[紧急逃生页]
    CH -->|使用统计| ST[使用统计页]
    CH -->|单向控制| SC[单向控制页]
    CH -->|设置图标| SETTINGS[设置页]
    CH -->|返回箭头| DEV_SEL[设备选择页]

    style CH fill:#E8F5E9,color:#000
```

---

## 6. 被控端主页

```mermaid
flowchart TD
    CDH["被控端主页<br/>TopAppBar: PeerLock 被控端 + 设置图标<br/>返回: 无 顶级页<br/><br/>StatusCard: 已配对 接受控制方管理<br/>管控方: 设备名 (身份指纹摘要)<br/><br/>受限应用列表:<br/>com.app1 Card (今日使用/限额)<br/>com.app2 Card (今日使用/限额)<br/><br/>功能区:<br/>[使用统计] OutlinedButton<br/>[发送统计] OutlinedButton<br/>[申请解绑] OutlinedButton (红色文字)"]

    CDH -->|应用卡片| UR[申请解锁页]
    CDH -->|使用统计| ST[使用统计页]
    CDH -->|发送统计| PUSH_STATS[发送统计页]
    CDH -->|申请解绑| UNBIND[申请解绑页]
    CDH -->|设置图标| SETTINGS[设置页]

    style CDH fill:#FFF3E0,color:#000
    style UNBIND fill:#FFEBEE,color:#000
```

---

## 7. 申请解锁流程（被控端）

```mermaid
flowchart TD
    UR["申请解锁页<br/>TopAppBar: 返回箭头 + 申请解锁<br/>返回: popBackStack"] --> MODE{模式切换}

    MODE -->|默认| QUICK
    MODE -->|手动切换| REQUEST

    QUICK["快速码模式:<br/>选择受限应用 FilterChip (必选)<br/>输入控制方提供的6位解锁码<br/>6位 TotpInputField<br/>解锁时长 30 分钟<br/>[切换到申请模式] Button<br/>成功: 已解锁 com.app1 30分钟后暂停"]

    REQUEST["申请模式:<br/>选择要解锁的应用 FilterChip (必选)<br/>请求时长 30 分钟<br/>附带统计信息 Checkbox<br/>  · 最近一天使用数据<br/>[生成申请码] Button<br/>生成后: 请让控制方扫描 QR码/复制字符串<br/>[切换到快速码模式] Button"]

    style UR fill:#FFF3E0,color:#000
    style QUICK fill:#FFE0B2,color:#000
    style REQUEST fill:#FFE0B2,color:#000
```

---

## 8. 审批请求流程（管控端）

```mermaid
flowchart TD
    RA["审批请求页<br/>TopAppBar: 返回箭头 + 审批请求<br/>返回: popBackStack"] --> MODE{入口选择}

    MODE -->|扫描被控端请求| SCAN["扫描/粘贴被控端申请码<br/>[开始扫描] Button<br/>[粘贴申请数据] 展开输入框"]

    MODE -->|单向解锁| SINGLE["单向解锁入口<br/>选择应用 → 直接生成解锁指令"]

    SCAN -->|解析成功| REVIEW["REVIEWING:<br/>请求详情: 类型 应用 时长<br/>设备状态: 屏幕时间 已暂停数<br/>附带统计: 今日使用图表 (如有)<br/>调整参数: 时长Slider 5-180分<br/>模式 FilterChip: 累计/单次<br/>[拒绝] Button | [批准] Button"]

    REVIEW -->|批准或拒绝| RESP["SHOWING_RESPONSE:<br/>请让被控端扫描此响应码<br/>QR码 + [复制响应数据]<br/>[完成] Button 重置回初始"]

    SINGLE -->|生成指令| CMD["显示解锁指令<br/>QR码/字符串<br/>[复制指令] Button<br/>被控端收到后自动执行"]

    RESP -->|完成| RA
    CMD -->|完成| RA

    style RA fill:#E8F5E9,color:#000
    style SINGLE fill:#C8E6C9,color:#000
```

---

## 9. 单向控制流程（管控端 → 被控端）

```mermaid
flowchart TD
    SC["单向控制页<br/>TopAppBar: 返回箭头 + 单向控制<br/>返回: popBackStack"] --> OPS{操作类型}

    OPS -->|解锁应用| UNLOCK["解锁指令<br/>选择受限应用 FilterChip<br/>解锁时长 30 分钟<br/>[生成解锁指令] Button<br/>生成: QR/字符串<br/>被控端收到后自动执行<br/>执行结果: 回执可选"]

    OPS -->|调整设置| ADJUST["调整指令<br/>选择目标应用<br/>新时长/时段配置<br/>[生成调整指令] Button<br/>被控端需确认后执行<br/>回执: 已确认/已拒绝"]

    OPS -->|请求统计| REQ_STATS["统计请求<br/>选择时间范围: 最近1天/7天/30天<br/>[生成请求指令] Button<br/>被控端自动发送统计数据<br/>回执: 统计数据包"]

    UNLOCK --> RESULT["指令已生成<br/>发送给被控端执行"]
    ADJUST --> RESULT
    REQ_STATS --> RESULT

    style SC fill:#E8F5E9,color:#000
    style UNLOCK fill:#C8E6C9,color:#000
    style ADJUST fill:#C8E6C9,color:#000
    style REQ_STATS fill:#C8E6C9,color:#000
```

---

## 10. 申请解绑流程（被控端发起 → 管控端终止码确认）

```mermaid
flowchart TD
    UNBIND["申请解绑页<br/>TopAppBar: 返回箭头 + 申请解绑<br/>FLAG_SECURE 防截屏<br/>返回: popBackStack"]

    UNBIND --> INFO["显示当前配对信息:<br/>管控方: 设备名<br/>身份指纹: XX:XX:...<br/>配对时间: 2026-05-02<br/>会话ID: a3f8...<br/><br/>⚠️ 此操作需管控方输入终止码确认<br/>解绑后管控关系将解除<br/>Device Owner 权限保留"]

    INFO -->|生成申请| GEN_REQ["生成解绑请求<br/>包含会话ID + 设备身份签名<br/>展示 QR码 + [复制请求数据]<br/>发送给管控方"]

    GEN_REQ --> WAIT["等待管控方确认...<br/>QR码持续展示<br/>[取消] Button"]

    style UNBIND fill:#FFEBEE,color:#000
    style INFO fill:#FFCDD2,color:#000
    style GEN_REQ fill:#FFCDD2,color:#000
```

**管控端处理申请解绑（在审批请求页）：**
```mermaid
flowchart TD
    RA["审批请求页<br/>扫描/粘贴解绑请求"] --> PARSE{请求类型?}

    PARSE -->|unlock/config| NORMAL["正常审批流程"]
    PARSE -->|unbind| UNBIND_REVIEW["解绑审批:<br/>被控端: 设备名<br/>请求解绑配对关系<br/><br/>请输入终止码确认:<br/>6位输入框 TotpInputField<br/>[取消] Button | [确认解绑] Button (红色)"]

    UNBIND_REVIEW -->|终止码验证通过| EXEC["执行解绑<br/>双方同时解除配对<br/>清除加密材料<br/>显示: 解绑已执行"]

    UNBIND_REVIEW -->|终止码错误| ERR["验证码错误 (N/5)"]
    UNBIND_REVIEW -->|取消| RA

    style UNBIND_REVIEW fill:#FFCDD2,color:#000
    style EXEC fill:#FFEBEE,color:#000
```

---

## 11. 设置页完整结构

```mermaid
flowchart TD
    SET["设置页<br/>TopAppBar: 返回箭头 + 设置<br/>返回: popBackStack"]

    SET --> DO["Device Owner 状态<br/>右侧: ✓已设置 (灰色不可点)<br/>右侧: ✗未设置 (可点击进入设置引导)"]
    SET --> BAT["电池优化<br/>右侧: ✓已关闭 (灰色不可点)<br/>右侧: ✗未关闭 (可点击进入设置引导)"]
    SET --> PAIR_INFO["配对信息<br/>显示当前配对详情<br/>点击进入配对信息页"]
    SET --> STATS_SEND["发送统计设置<br/>选择附带/主动发送的默认范围"]
    SET --> THEME["主题模式<br/>跟随系统/浅色/深色 FilterChip"]
    SET --> LANG["语言<br/>中文/English FilterChip"]
    SET --> SWITCH["切换角色<br/>回到角色选择页<br/>(不影响已建立的配对)"]
    SET --> RESET["身份重置<br/>条件满足时可用<br/>10秒确认对话框"]

    DO -->|未设置点击| DO_GUIDE["DO设置引导<br/>同初始化流程 Phase 1"]
    BAT -->|未关闭点击| BAT_GUIDE["电池优化引导<br/>同初始化流程 Phase 2"]
    PAIR_INFO --> PAIR_DETAIL["配对信息页"]
    SWITCH --> RS["角色选择页<br/>(已有ACTIVE配对的角色<br/>显示提示但仍可选择)"]

    style SET fill:#EDE7F6,color:#000
    style DO_GUIDE fill:#FCE4EC,color:#000
    style BAT_GUIDE fill:#FCE4EC,color:#000
    style SWITCH fill:#E3F2FD,color:#000
    style RESET fill:#FFEBEE,color:#000
```

---

## 12. 配对信息页

```mermaid
flowchart TD
    PAIR_DETAIL["配对信息页<br/>TopAppBar: 返回箭头 + 配对信息<br/>FLAG_SECURE 防截屏"]

    PAIR_DETAIL --> INFO["本机信息:<br/>角色: 管控端/被控端<br/>身份指纹: A3:5F:...<br/>设备名: Pixel 7<br/><br/>对方信息:<br/>设备名: Galaxy S24<br/>身份指纹: B7:1E:...<br/>配对时间: 2026-05-02 14:20<br/>会话ID: a3f8-xxxx<br/>状态: ACTIVE"]

    INFO --> CTRL_ACTIONS["管控端额外显示:<br/>TOTP种子状态: 已生成<br/>[查看验证码] → 跳转管控主页<br/>[申请解绑] 需输入终止码"]

    INFO --> CTRLLED_ACTIONS["被控端额外显示:<br/>种子状态: 已存储<br/>[申请解绑] 生成请求QR"]

    style PAIR_DETAIL fill:#EDE7F6,color:#000
```

---

## 13. 紧急逃生页（管控端）

```mermaid
flowchart TD
    EM["紧急逃生页<br/>TopAppBar: 返回箭头 + 紧急逃生<br/>返回: popBackStack<br/>FLAG_SECURE 防截屏"]

    EM --> L1
    EM --> L2

    L1["L1 终止码:<br/>选择要解绑的被控端 (多配对时)<br/>解绑配对+清除加密材料 保留DO<br/>6位终止码 OutlinedTextField<br/>[验证终止码] Button 红色<br/>验证通过: AlertDialog 确认终止<br/>执行后: L1 终止已执行"]

    L2["L2 高级解除 连点7次设备信息展开:<br/>需ADB命令 移除DO和密钥<br/>[开始验证] Button<br/>显示挑战码红色<br/>挑战码输入框<br/>[验证] Button<br/>通过: ADB命令 monospace<br/>执行后: L2 解除已执行"]

    style EM fill:#FFEBEE,color:#000
    style L1 fill:#FFCDD2,color:#000
    style L2 fill:#FFCDD2,color:#000
```

---

## 14. 完整页面流转表

| 从页面 | 触发操作 | 目标页面 | 可返回 |
|--------|---------|---------|--------|
| App 启动(未配对) | -- | 角色选择页 | -- |
| App 启动(已配对+管控端) | -- | 设备选择页 | -- |
| App 启动(已配对+被控端) | -- | 被控端主页 | -- |
| 角色选择 | 我要管控对方 | 配对页(controller) | 可 |
| 角色选择 | 我需要被管控 | DO设置页 | 可 |
| 角色选择 | 被控端有ACTIVE配对 | 提示已管控+申请解绑 | 可 |
| DO设置 Phase1 | 继续/跳过 | Phase2 电池优化 | 可 |
| DO设置 Phase2 | 完成/跳过 | 配对页(controlled) | 否(清栈) |
| DO设置 | 返回 | 角色选择页 | -- |
| 配对页 | 回执验证通过 | 配对确认页 | 可 |
| 配对确认页 | 确认(5s后) | 设备选择页/被控端主页 | 否(清栈) |
| 配对确认页 | 取消 | 配对页 | 可 |
| 设备选择页 | 点击设备 | 管控端主页 | 可 |
| 设备选择页 | 添加新设备 | 配对页(controller) | 可 |
| 设备选择页 | 设置图标 | 设置页 | 可 |
| 管控端主页 | 审批请求 | 审批请求页 | 可 |
| 管控端主页 | 紧急逃生 | 紧急逃生页 | 可 |
| 管控端主页 | 使用统计 | 使用统计页 | 可 |
| 管控端主页 | 单向控制 | 单向控制页 | 可 |
| 管控端主页 | 返回 | 设备选择页 | -- |
| 管控端主页 | 设置图标 | 设置页 | 可 |
| 被控端主页 | 应用卡片 | 申请解锁页 | 可 |
| 被控端主页 | 使用统计 | 使用统计页 | 可 |
| 被控端主页 | 发送统计 | 发送统计页 | 可 |
| 被控端主页 | 申请解绑 | 申请解绑页 | 可 |
| 被控端主页 | 设置图标 | 设置页 | 可 |
| 审批请求页 | 扫描/粘贴请求 | 审批详情 | -- |
| 审批请求页 | 扫描解绑请求 | 解绑审批(终止码) | 可 |
| 申请解绑页 | 生成请求 | 展示QR等待确认 | 可 |
| 申请解绑页 | 取消 | 被控端主页 | -- |
| 设置页 | DO状态(未设置) | DO设置引导 | 可 |
| 设置页 | 电池优化(未关闭) | 电池优化引导 | 可 |
| 设置页 | 配对信息 | 配对信息页 | 可 |
| 设置页 | 切换角色 | 角色选择页 | 可 |
| 所有子页 | 返回 | 上级页面 | -- |

---

## 15. 安全检查点汇总

| 检查点 | 位置 | 逻辑 |
|--------|------|------|
| 被控端已绑定检查 | 角色选择 → 我需要被管控 | 有 ACTIVE 配对则阻止，引导申请解绑 |
| 配对确认冷却 | 配对确认页 | 确认按钮 5 秒灰色不可点 |
| 终止码验证 | 紧急逃生 L1 / 解绑审批 | 6位TOTP + 5次错误锁30秒 |
| 挑战码验证 | 紧急逃生 L2 | 手动输入挑战码 |
| nonce 格式校验 | EmergencyUnlockReceiver | 16位hex正则校验 |
| FLAG_SECURE | 紧急逃生页 + 申请解绑页 + 配对信息页 | 防截屏 |
| 信封时效 | 所有签名信封 | 5分钟过期 |
| 频率限制 | 解锁/配置/解绑请求 | 30秒1次 |
