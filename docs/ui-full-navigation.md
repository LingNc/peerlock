# PeerLock 完整导航图

---

## 全局导航总览

```mermaid
flowchart TD
    START([App 启动]) --> CHECK{isPaired?}
    CHECK -->|否| ROLE_SELECTION
    CHECK -->|是 controller| CONTROLLER_HOME
    CHECK -->|是 controlled| CONTROLLED_HOME

    subgraph 入网引导
        ROLE_SELECTION["角色选择页<br/>无TopAppBar<br/>我要管控对方 Button<br/>我需要被管控 Button"]
        ROLE_SELECTION -->|我要管控对方| PAIRING_C
        ROLE_SELECTION -->|我需要被管控| DO_SETUP

        DO_SETUP["DO + 电池优化设置页<br/>Phase1: DO状态检查/无线ADB引导/复制命令<br/>Phase2: 电池优化白名单<br/>TopAppBar: 返回箭头"]
        DO_SETUP -->|继续或跳过| PAIRING_CTRL

        PAIRING_C["配对页 controller<br/>扫描被控端QR或粘贴数据<br/>展示响应QR或复制数据<br/>TopAppBar: 返回箭头"]
        PAIRING_CTRL["配对页 controlled<br/>展示配对QR或复制数据<br/>扫描/粘贴控制方响应码<br/>TopAppBar: 返回箭头"]

        PAIRING_CTRL -->|配对完成| CONTROLLED_HOME
        PAIRING_C -->|配对完成| CONTROLLER_HOME
        PAIRING_C -.->|返回| ROLE_SELECTION
        DO_SETUP -.->|返回| ROLE_SELECTION
        PAIRING_CTRL -.->|返回| DO_SETUP
    end

    subgraph 管控端
        CONTROLLER_HOME["管控端主页<br/>TopAppBar: 标题 + 设置图标<br/>StatusCard 已配对<br/>验证码 管理/解锁/终止 每秒刷新<br/>策略列表<br/>审批请求/紧急逃生/使用统计 按钮"]
        CONTROLLER_HOME -->|设置图标| SETTINGS
        CONTROLLER_HOME -->|审批请求| REQUEST_APPROVAL
        CONTROLLER_HOME -->|紧急逃生| EMERGENCY
        CONTROLLER_HOME -->|使用统计| STATS
        CONTROLLER_HOME -->|解锁请求| UNLOCK_REQUEST

        REQUEST_APPROVAL["审批请求页<br/>TopAppBar: 返回箭头<br/>IDLE: 扫描请求QR<br/>REVIEWING: 详情+调整时长/模式+批准/拒绝<br/>SHOWING_RESPONSE: 展示响应QR"]
        EMERGENCY["紧急逃生页<br/>TopAppBar: 返回箭头<br/>FLAG_SECURE 防截屏<br/>L1 终止码: 验证+确认+执行<br/>L2 高级解除: 连点7次+挑战码+ADB命令"]
    end

    subgraph 被控端
        CONTROLLED_HOME["被控端主页<br/>TopAppBar: 标题 + 设置图标<br/>StatusCard 已配对<br/>受限应用列表 点击进入解锁<br/>使用统计 按钮"]
        CONTROLLED_HOME -->|设置图标| SETTINGS
        CONTROLLED_HOME -->|点击应用卡片| UNLOCK_REQUEST
        CONTROLLED_HOME -->|使用统计| STATS

        UNLOCK_REQUEST["申请解锁页<br/>TopAppBar: 返回箭头<br/>快速码模式: 6位TOTP直接解锁全部应用<br/>申请模式: 选应用+生成请求QR<br/>模式切换按钮"]
    end

    subgraph 通用子页
        STATS["使用统计页<br/>TopAppBar: 返回箭头<br/>今天/本周/本月 Tab<br/>小时柱状图 点击下钻<br/>日折线图 点击下钻<br/>应用明细列表"]
        SETTINGS["设置页<br/>TopAppBar: 返回箭头<br/>主题模式: 跟随系统/浅色/深色<br/>语言: 中文/English"]
    end

    STATS -.->|返回| BACK([返回上级])
    SETTINGS -.->|返回| BACK
    EMERGENCY -.->|返回| BACK
    REQUEST_APPROVAL -.->|返回| BACK
    UNLOCK_REQUEST -.->|返回| BACK

    style ROLE_SELECTION fill:#E3F2FD,color:#000
    style DO_SETUP fill:#FCE4EC,color:#000
    style PAIRING_C fill:#F3E5F5,color:#000
    style PAIRING_CTRL fill:#F3E5F5,color:#000
    style CONTROLLER_HOME fill:#E8F5E9,color:#000
    style CONTROLLED_HOME fill:#FFF3E0,color:#000
    style REQUEST_APPROVAL fill:#C8E6C9,color:#000
    style EMERGENCY fill:#FFCDD2,color:#000
    style UNLOCK_REQUEST fill:#FFE0B2,color:#000
    style STATS fill:#E3F2FD,color:#000
    style SETTINGS fill:#EDE7F6,color:#000
```

---

## 入网引导阶段

### 角色选择页

```mermaid
flowchart TD
    RS["角色选择页<br/>无TopAppBar 不可返回<br/>PeerLock headlineLarge<br/>两人互相监督的屏幕时间管理工具<br/>[我要管控对方] Button<br/>[我需要被管控] OutlinedButton"]
    RS -->|我要管控对方| PAIRING_C[配对页 controller]
    RS -->|我需要被管控| DO_SETUP[DO + 电池优化设置页]

    style RS fill:#E3F2FD,color:#000
```

### DO + 电池优化设置页

#### Phase 1: DO_SETUP

```mermaid
flowchart TD
    DO1["Phase 1: 设置 Device Owner<br/>TopAppBar: 返回箭头 + 标题<br/>返回: 角色选择页"] --> DO_CHECK{isDeviceOwner?}
    DO_CHECK -->|是| DO_ALREADY["Device Owner 已设置<br/>[继续] Button"]
    DO_CHECK -->|否| DO_NOT_YET["需要 DO 权限才能限制应用<br/>无线 ADB: 6步说明 + adb命令 + 复制命令<br/>USB ADB: adb命令 + 复制命令<br/>[检查设置状态] -> statusMessage 反馈<br/>[跳过功能受限]"]

    DO_ALREADY --> DO_ADVANCE["advanceToDoComplete"]
    DO_ADVANCE --> DO_BATTERY_CHECK{isBatteryExempt?}
    DO_BATTERY_CHECK -->|是| NAV_PAIRING2["navigate 配对页 controlled"]
    DO_BATTERY_CHECK -->|否| PHASE2[Phase 2]

    style DO1 fill:#FCE4EC,color:#000
```

#### Phase 2: BATTERY_OPTIMIZATION

```mermaid
flowchart TD
    DO2["Phase 2: 电池优化白名单<br/>TopAppBar: 返回箭头 + 标题<br/>返回: 角色选择页"] --> BO_CHECK{isBatteryExempt?}
    BO_CHECK -->|是| BO_DONE["已关闭电池优化<br/>[继续] 到配对页"]
    BO_CHECK -->|否| BO_NOT["需关闭电池优化<br/>[关闭电池优化] 系统对话框<br/>[检查是否已关闭]<br/>[跳过后台巡检可能不稳定]"]
    BO_NOT -->|跳过| NAV_PAIRING["navigate 配对页 controlled"]
    BO_NOT -->|关闭成功| NAV_PAIRING
    BO_DONE --> NAV_PAIRING

    style DO2 fill:#FCE4EC,color:#000
```

### 配对页

#### 被控端视角

```mermaid
flowchart TD
    P1["配对页 被控端<br/>TopAppBar: 返回箭头 + 配对<br/>返回: DO设置页"] --> P1_SHOW
    P1_SHOW["SHOW_MY_QR:<br/>请让控制方扫描此二维码<br/>QR码 pairRequestQr<br/>[复制配对数据] 剪贴板<br/>[扫描响应码] 相机"]
    P1_SCAN["SCAN_PEER_QR:<br/>请扫描控制方的响应码<br/>[扫描响应码] 相机<br/>[粘贴响应数据] 输入框 + 确认"]
    P1_DONE["COMPLETED:<br/>配对完成!<br/>[进入主页] navigate 被控端主页 清栈"]
    P1_SHOW -->|扫描或粘贴响应码| P1_SCAN
    P1_SCAN -->|验证通过| P1_DONE
    P1_SHOW -.->|返回| DO_PAGE[DO设置页]
    P1_SCAN -.->|返回| P1_SHOW

    style P1 fill:#F3E5F5,color:#000
```

#### 管控端视角

```mermaid
flowchart TD
    P2["配对页 管控端<br/>TopAppBar: 返回箭头 + 配对<br/>返回: 角色选择页"] --> P2_SHOW
    P2_SHOW["SHOW_MY_QR:<br/>请扫描被控端的二维码<br/>[扫描二维码] 相机<br/>[粘贴配对数据] 输入框 + 确认"]
    P2_RESP["SCAN_PEER_QR:<br/>请让被控端扫描此响应码<br/>QR码 pairResponseQr<br/>[复制响应数据] 剪贴板"]
    P2_DONE["COMPLETED:<br/>配对完成!<br/>[进入主页] navigate 管控端主页 清栈"]
    P2_SHOW -->|扫描或粘贴被控端QR| P2_RESP
    P2_RESP -->|被控端扫码后自动完成| P2_DONE
    P2_SHOW -.->|返回| ROLE[角色选择页]
    P2_RESP -.->|返回| P2_SHOW

    style P2 fill:#F3E5F5,color:#000
```

---

## 管控端

### 管控端主页

```mermaid
flowchart TD
    CH["管控端主页<br/>TopAppBar: PeerLock 控制端 + 设置图标<br/>返回: 无 顶级页<br/>StatusCard: 已配对 策略巡检运行中<br/>[审批请求] Button<br/>[紧急逃生] OutlinedButton<br/>[使用统计] OutlinedButton<br/>验证码 每秒刷新:<br/>管理码 解锁码 终止码<br/>策略列表: com.app1 com.app2"]
    CH -->|设置图标| SETTINGS[设置页]
    CH -->|审批请求| RA[审批请求页]
    CH -->|紧急逃生| EM[紧急逃生页]
    CH -->|使用统计| ST[使用统计页]

    style CH fill:#E8F5E9,color:#000
```

### 审批请求页

```mermaid
flowchart TD
    RA["审批请求页<br/>TopAppBar: 返回箭头 + 审批请求<br/>返回: popBackStack 重置流程"] --> RA_IDLE

    RA_IDLE["IDLE:<br/>扫描被控端的请求二维码<br/>[开始扫描] 相机"]
    RA_REVIEW["REVIEWING:<br/>请求详情: 类型 应用 时长<br/>设备状态: 屏幕时间 已暂停数<br/>调整参数: 时长Slider 5-180分<br/>模式 FilterChip: 累计/单次<br/>[拒绝] | [批准]"]
    RA_RESP["SHOWING_RESPONSE:<br/>请让被控端扫描此响应码<br/>QR码<br/>[完成] 重置回 IDLE"]

    RA_IDLE -->|扫描成功| RA_REVIEW
    RA_REVIEW -->|批准或拒绝| RA_RESP
    RA_RESP -->|完成| RA_IDLE

    style RA fill:#E8F5E9,color:#000
```

### 紧急逃生页

```mermaid
flowchart TD
    EM["紧急逃生页<br/>TopAppBar: 返回箭头 + 紧急逃生<br/>返回: popBackStack<br/>FLAG_SECURE 防截屏"] --> EM_L1
    EM --> EM_L2

    EM_L1["L1 终止码:<br/>解除配对+清除加密材料 保留DO<br/>[6位终止码] OutlinedTextField<br/>[验证终止码] Button 红色<br/>验证通过: AlertDialog 确认终止<br/>执行后: L1 终止已执行"]

    EM_L2["L2 高级解除 连点7次设备信息展开:<br/>需ADB命令 移除DO和密钥<br/>[开始验证] Button<br/>显示挑战码红色<br/>[挑战码] 输入框<br/>[验证] Button<br/>通过: ADB命令 monospace<br/>执行后: L2 解除已执行"]

    style EM fill:#FFEBEE,color:#000
    style EM_L1 fill:#FFCDD2,color:#000
    style EM_L2 fill:#FFCDD2,color:#000
```

---

## 被控端

### 被控端主页

```mermaid
flowchart TD
    CDH["被控端主页<br/>TopAppBar: PeerLock 被控端 + 设置图标<br/>返回: 无 顶级页<br/>StatusCard: 已配对 接受控制方管理<br/>受限应用: com.app1 com.app2<br/>[使用统计] OutlinedButton"]
    CDH -->|设置图标| SETTINGS[设置页]
    CDH -->|点击应用卡片| UR[申请解锁页]
    CDH -->|使用统计| ST[使用统计页]

    style CDH fill:#FFF3E0,color:#000
```

### 申请解锁页

```mermaid
flowchart TD
    UR["申请解锁页<br/>TopAppBar: 返回箭头 + 申请解锁<br/>返回: popBackStack"] --> UR_MODE{模式切换}
    UR_MODE -->|默认| QUICK
    UR_MODE -->|手动切换| REQUEST

    QUICK["快速码模式:<br/>输入6位解锁码 解锁全部受限应用<br/>[6位TotpInputField]<br/>[解锁时长] 30 分钟<br/>[切换到申请模式]<br/>成功: 已解锁全部应用 30分钟后暂停"]

    REQUEST["申请模式:<br/>选择要解锁的应用<br/>com.app1 FilterChip<br/>com.app2 FilterChip 选中<br/>[请求时长] 30 分钟<br/>[生成申请码] 需选中应用<br/>生成后: 请让控制方扫描 QR码<br/>[切换到快速码模式]"]

    style UR fill:#FFF3E0,color:#000
    style QUICK fill:#FFE0B2,color:#000
    style REQUEST fill:#FFE0B2,color:#000
```

---

## 通用子页

### 使用统计页

```mermaid
flowchart TD
    ST["使用统计页<br/>TopAppBar: 返回箭头 + 使用统计<br/>返回: popBackStack"] --> ST_UI

    ST_UI["[今天] [本周] [本月] FilterChip 标签<br/>小时柱状图 Canvas 今天:<br/>X轴 0-23时 Y轴 分钟<br/>点击柱子显示时段详情<br/>日折线图 Canvas 本周/本月:<br/>X轴 日期 Y轴 分钟<br/>点击节点显示当日详情<br/>应用明细: com.app1 120分 启动8次"]

    style ST fill:#E3F2FD,color:#000
```

### 设置页

```mermaid
flowchart TD
    SET["设置页<br/>TopAppBar: 返回箭头 + 设置<br/>返回: popBackStack"] --> SET_UI

    SET_UI["主题模式:<br/>[跟随系统] [浅色] [深色] FilterChip<br/>语言:<br/>[中文] [English] FilterChip"]

    style SET fill:#EDE7F6,color:#000
```

---

## 完整页面流转表

| 从页面 | 触发操作 | 目标页面 | 可返回 |
|--------|---------|---------|--------|
| App 启动(未配对) | -- | 角色选择页 | -- |
| App 启动(已配对) | -- | 对应主页 | -- |
| 角色选择 | 我要管控对方 | 配对页(controller) | 可 |
| 角色选择 | 我需要被管控 | DO设置页 | 可 |
| DO设置 | 继续/跳过 | 配对页(controlled) | 否(清栈) |
| DO设置 | 返回 | 角色选择页 | -- |
| 配对页(controller) | 配对完成 | 管控端主页 | 否(清栈) |
| 配对页(controller) | 返回 | 角色选择页 | -- |
| 配对页(controlled) | 配对完成 | 被控端主页 | 否(清栈) |
| 配对页(controlled) | 返回 | DO设置页 | -- |
| 管控端主页 | 设置图标 | 设置页 | 可 |
| 管控端主页 | 审批请求 | 审批请求页 | 可 |
| 管控端主页 | 紧急逃生 | 紧急逃生页 | 可 |
| 管控端主页 | 使用统计 | 使用统计页 | 可 |
| 被控端主页 | 设置图标 | 设置页 | 可 |
| 被控端主页 | 应用卡片 | 申请解锁页 | 可 |
| 被控端主页 | 使用统计 | 使用统计页 | 可 |
| 所有子页 | 返回 | 上级页面 | -- |
