# PeerLock UI 导航流程图

## 启动逻辑

```mermaid
flowchart TD
    START([App 启动]) --> CHECK{isPaired?}
    CHECK -->|否| ROLE_SELECTION
    CHECK -->|是 + controller| CONTROLLER_HOME
    CHECK -->|是 + controlled| CONTROLLED_HOME

    subgraph 入网引导
        ROLE_SELECTION[角色选择页] -->|"我要管控对方"| PAIRING_C[配对页 controller]
        ROLE_SELECTION -->|"我需要被管控"| DO_SETUP[DO + 电池优化设置页]
        DO_SETUP -->|"继续 / 跳过"| PAIRING_CTRL[配对页 controlled]
        PAIRING_CTRL -->|配对完成| CONTROLLED_HOME
        PAIRING_C -->|配对完成| CONTROLLER_HOME
        PAIRING_C -.->|返回| ROLE_SELECTION
        DO_SETUP -.->|返回| ROLE_SELECTION
        PAIRING_CTRL -.->|返回| DO_SETUP
    end

    subgraph 管控端主页
        CONTROLLER_HOME --> STATS[使用统计页]
        CONTROLLER_HOME --> SETTINGS[设置页]
        CONTROLLER_HOME --> EMERGENCY[紧急逃生页]
        CONTROLLER_HOME --> REQUEST_APPROVAL[审批请求页]
        CONTROLLER_HOME --> UNLOCK_REQUEST[申请解锁页]
    end

    subgraph 被控端主页
        CONTROLLED_HOME --> STATS
        CONTROLLED_HOME --> SETTINGS
        CONTROLLED_HOME --> UNLOCK_REQUEST
    end

    subgraph 通用子页
        STATS -.->|返回| BACK([返回上级])
        SETTINGS -.->|返回| BACK
        EMERGENCY -.->|返回| BACK
        REQUEST_APPROVAL -.->|返回| BACK
        UNLOCK_REQUEST -.->|返回| BACK
    end

    style ROLE_SELECTION fill:#E3F2FD
    style CONTROLLER_HOME fill:#E8F5E9
    style CONTROLLED_HOME fill:#FFF3E0
    style DO_SETUP fill:#FCE4EC
    style PAIRING_C fill:#F3E5F5
    style PAIRING_CTRL fill:#F3E5F5
```

## 各页面功能说明

### 入网引导阶段

| 页面 | TopAppBar | 功能 |
|------|-----------|------|
| 角色选择页 | 无 | 两个按钮：管控方 / 被控方 |
| DO + 电池优化设置页 | 有返回箭头 | DO 状态检查、无线 ADB 引导、电池优化白名单 |
| 配对页 | 有返回箭头 | QR 码展示/扫描 + 字符串复制/粘贴 |

### 管控端

| 页面 | TopAppBar | 功能 |
|------|-----------|------|
| 管控端主页 | 设置图标 | TOTP 验证码、策略列表、审批/逃生/统计入口 |
| 审批请求页 | 有返回箭头 | 扫描被控端请求 QR → 审批/拒绝 → 生成响应 QR |
| 紧急逃生页 | 有返回箭头 | L1 终止码 / L2 ADB 解除 / L3 文档 |

### 被控端

| 页面 | TopAppBar | 功能 |
|------|-----------|------|
| 被控端主页 | 设置图标 | 受限应用列表、申请解锁入口 |
| 申请解锁页 | 有返回箭头 | 快速码模式（直接输入 TOTP）+ 申请模式（选应用 + 生成 QR） |

### 通用

| 页面 | TopAppBar | 功能 |
|------|-----------|------|
| 使用统计页 | 有返回箭头 | 小时柱状图、日折线图、点击下钻 |
| 设置页 | 有返回箭头 | 主题切换、语言切换 |
