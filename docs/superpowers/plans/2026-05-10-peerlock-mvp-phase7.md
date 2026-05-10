# PeerLock MVP 第七阶段：UI（主题、导航、引导、控制端、被控端）

> **给 AI 工作者的说明：** 推荐使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐步执行本计划。步骤使用 `- [ ]` 勾选语法追踪进度。

**目标：** 实现 PeerLock 的完整 UI 层——Material3 主题、Compose 导航、首次启动引导（角色选择 + 配对）、控制端主界面（TOTP 码展示 + 策略管理）、被控端主界面（使用统计 + 解锁申请 + 紧急逃生）。

**架构：** 单 Activity + Compose Navigation。`MainActivity` 设置 `NavHost`，根据 `SecurePrefs.role` + `SecurePrefs.isPaired` 决定起始路由。ViewModel 通过 Hilt 注入，调用 domain 层接口。UI 层不直接访问 data/system 层。

**技术栈：** Jetpack Compose (BOM 2024.12.01) + Material3 + Navigation Compose 2.8.5 + Hilt Navigation Compose + Lifecycle ViewModel Compose

**参考设计文档：** `docs/superpowers/specs/2026-05-09-peerlock-mvp-design.md` 第 4、7、10、13 节

---

## 文件结构（第七阶段新增）

```
app/src/main/java/com/peerlock/
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                       # Material3 主题
│   │   ├── Color.kt                       # 颜色定义
│   │   └── Type.kt                        # 字体定义
│   ├── navigation/
│   │   └── PeerLockNavHost.kt             # 导航图 + 路由定义
│   ├── onboarding/
│   │   ├── RoleSelectionScreen.kt         # 角色选择
│   │   ├── PairingScreen.kt              # 配对流程（QR 展示/扫描）
│   │   └── OnboardingViewModel.kt        # 引导流程 ViewModel
│   ├── controller/
│   │   ├── ControllerHomeScreen.kt        # 控制端主界面
│   │   └── ControllerViewModel.kt         # 控制端 ViewModel
│   ├── controlled/
│   │   ├── ControlledHomeScreen.kt        # 被控端主界面
│   │   ├── UnlockRequestScreen.kt         # 解锁申请
│   │   └── ControlledViewModel.kt         # 被控端 ViewModel
│   └── common/
│       ├── TotpInputField.kt              # 6位TOTP输入组件
│       ├── QrCodeDisplay.kt              # 二维码展示组件
│       └── StatusCard.kt                  # 状态卡片组件
├── MainActivity.kt                        # 修改：Compose 入口 + NavHost
```

---

## 任务 1：Theme — Material3 主题

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/theme/Color.kt`
- 新建: `app/src/main/java/com/peerlock/ui/theme/Type.kt`
- 新建: `app/src/main/java/com/peerlock/ui/theme/Theme.kt`

**设计：**
- 使用 Material3 动态配色（Material You），备选浅色/深色方案
- 主色调：蓝色（与启动图标 `#2196F3` 一致）
- 字体：默认 Material3 字体

- [ ] **步骤 1：创建 Color.kt**

新建 `app/src/main/java/com/peerlock/ui/theme/Color.kt`：

```kotlin
package com.peerlock.ui.theme

import androidx.compose.ui.graphics.Color

val Blue500 = Color(0xFF2196F3)
val Blue700 = Color(0xFF1976D2)
val Blue200 = Color(0xFF90CAF9)
val Orange500 = Color(0xFFFF9800)
val Red500 = Color(0xFFF44336)
val Green500 = Color(0xFF4CAF50)
val Gray100 = Color(0xFFF5F5F5)
val Gray800 = Color(0xFF424242)
```

- [ ] **步骤 2：创建 Type.kt**

新建 `app/src/main/java/com/peerlock/ui/theme/Type.kt`：

```kotlin
package com.peerlock.ui.theme

import androidx.compose.material3.Typography

val Typography = Typography()
```

- [ ] **步骤 3：创建 Theme.kt**

新建 `app/src/main/java/com/peerlock/ui/theme/Theme.kt`：

```kotlin
package com.peerlock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Blue200,
    secondary = Orange500,
    error = Red500,
    surface = Gray100,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Gray800,
    primaryContainer = Blue700,
    secondary = Orange500,
    error = Red500,
)

@Composable
fun PeerLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **步骤 4：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`
预期: BUILD SUCCESSFUL

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/ui/theme/
git commit -m "feat: Material3 主题（动态配色 + 蓝色主色调）"
```

---

## 任务 2：Navigation — 导航图 + 路由

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/navigation/PeerLockNavHost.kt`
- 修改: `app/src/main/java/com/peerlock/MainActivity.kt`（设置 NavHost）

**设计：**
- 路由定义为 sealed class 或 object 常量
- 路由表：
  - `role_selection` — 角色选择（首次启动）
  - `pairing/{role}` — 配对流程
  - `controller_home` — 控制端主页
  - `controlled_home` — 被控端主页
  - `unlock_request` — 解锁申请
- 起始路由逻辑：未配对 → `role_selection`，已配对 + 控制方 → `controller_home`，已配对 + 被控方 → `controlled_home`

- [ ] **步骤 1：创建 PeerLockNavHost.kt**

新建 `app/src/main/java/com/peerlock/ui/navigation/PeerLockNavHost.kt`：

```kotlin
package com.peerlock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.ui.controlled.ControlledHomeScreen
import com.peerlock.ui.controlled.UnlockRequestScreen
import com.peerlock.ui.controller.ControllerHomeScreen
import com.peerlock.ui.onboarding.PairingScreen
import com.peerlock.ui.onboarding.RoleSelectionScreen

object Routes {
    const val ROLE_SELECTION = "role_selection"
    const val PAIRING = "pairing/{role}"
    const val CONTROLLER_HOME = "controller_home"
    const val CONTROLLED_HOME = "controlled_home"
    const val UNLOCK_REQUEST = "unlock_request"

    fun pairing(role: String) = "pairing/$role"
}

@Composable
fun PeerLockNavHost(
    navController: NavHostController,
    securePrefs: SecurePrefs,
) {
    val startDestination = remember {
        when {
            !securePrefs.isPaired -> Routes.ROLE_SELECTION
            securePrefs.role == "controller" -> Routes.CONTROLLER_HOME
            else -> Routes.CONTROLLED_HOME
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    navController.navigate(Routes.pairing(role)) {
                        popUpTo(Routes.ROLE_SELECTION) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Routes.PAIRING,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "controlled"
            PairingScreen(
                role = role,
                onPairingComplete = {
                    val destination = if (role == "controller") Routes.CONTROLLER_HOME
                    else Routes.CONTROLLED_HOME
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CONTROLLER_HOME) {
            ControllerHomeScreen(
                onNavigateToUnlockRequest = { navController.navigate(Routes.UNLOCK_REQUEST) }
            )
        }

        composable(Routes.CONTROLLED_HOME) {
            ControlledHomeScreen(
                onRequestUnlock = { navController.navigate(Routes.UNLOCK_REQUEST) }
            )
        }

        composable(Routes.UNLOCK_REQUEST) {
            UnlockRequestScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **步骤 2：更新 MainActivity.kt**

修改 `app/src/main/java/com/peerlock/MainActivity.kt`：

```kotlin
package com.peerlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.ui.navigation.PeerLockNavHost
import com.peerlock.ui.theme.PeerLockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var securePrefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PeerLockTheme {
                val navController = rememberNavController()
                PeerLockNavHost(
                    navController = navController,
                    securePrefs = securePrefs,
                )
            }
        }
    }
}
```

- [ ] **步骤 3：构建验证（需要占位 Screen）**

此时需要创建占位 Screen 文件以通过编译。在后续任务中实现具体内容。

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`

- [ ] **步骤 4：提交**

```bash
git add app/src/main/java/com/peerlock/ui/navigation/ \
  app/src/main/java/com/peerlock/MainActivity.kt
git commit -m "feat: Compose 导航（路由定义 + 起始路由逻辑）"
```

---

## 任务 3：Common Components — 共享 UI 组件

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/common/TotpInputField.kt`
- 新建: `app/src/main/java/com/peerlock/ui/common/QrCodeDisplay.kt`
- 新建: `app/src/main/java/com/peerlock/ui/common/StatusCard.kt`

**设计：**
- `TotpInputField`：6 位数字输入，每位一个方框，自动聚焦下一位，支持粘贴
- `QrCodeDisplay`：使用 ZXing 生成二维码 Bitmap 并显示
- `StatusCard`：通用状态卡片（图标 + 标题 + 副标题 + 颜色指示）

- [ ] **步骤 1：创建 TotpInputField.kt**

新建 `app/src/main/java/com/peerlock/ui/common/TotpInputField.kt`：

```kotlin
package com.peerlock.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 6 位 TOTP 输入组件。
 * 每位一个独立输入框，自动聚焦下一位。
 */
@Composable
fun TotpInputField(
    onCodeComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val digits = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(6) { index ->
            OutlinedTextField(
                value = digits[index],
                onValueChange = { value ->
                    if (value.length <= 1 && value.all { it.isDigit() }) {
                        digits[index] = value
                        if (value.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        }
                        if (digits.all { it.isNotEmpty() }) {
                            onCodeComplete(digits.joinToString(""))
                        }
                    } else if (value.length > 1) {
                        // 粘贴处理
                        val pasted = value.filter { it.isDigit() }.take(6)
                        pasted.forEachIndexed { i, c ->
                            if (index + i < 6) digits[index + i] = c.toString()
                        }
                        val nextEmpty = digits.indexOfFirst { it.isEmpty() }
                        if (nextEmpty >= 0) focusRequesters[nextEmpty].requestFocus()
                        else if (digits.all { it.isNotEmpty() }) {
                            onCodeComplete(digits.joinToString(""))
                        }
                    }
                },
                modifier = Modifier
                    .width(48.dp)
                    .focusRequester(focusRequesters[index]),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
    }
}
```

- [ ] **步骤 2：创建 QrCodeDisplay.kt**

新建 `app/src/main/java/com/peerlock/ui/common/QrCodeDisplay.kt`：

```kotlin
package com.peerlock.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码展示组件。
 * 使用 ZXing 生成 QR 码 Bitmap。
 */
@Composable
fun QrCodeDisplay(
    content: String,
    modifier: Modifier = Modifier,
    size: Int = 256,
) {
    val bitmap = remember(content) {
        generateQrBitmap(content, size)
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "二维码",
        modifier = modifier.size(size.dp),
    )
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
```

- [ ] **步骤 3：创建 StatusCard.kt**

新建 `app/src/main/java/com/peerlock/ui/common/StatusCard.kt`：

```kotlin
package com.peerlock.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 通用状态卡片组件。
 */
@Composable
fun StatusCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
```

- [ ] **步骤 4：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`
预期: BUILD SUCCESSFUL

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/ui/common/
git commit -m "feat: 共享 UI 组件（TOTP 输入、二维码、状态卡片）"
```

---

## 任务 4：Onboarding — 角色选择 + 配对流程

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/onboarding/RoleSelectionScreen.kt`
- 新建: `app/src/main/java/com/peerlock/ui/onboarding/PairingScreen.kt`
- 新建: `app/src/main/java/com/peerlock/ui/onboarding/OnboardingViewModel.kt`

**设计：**
- `RoleSelectionScreen`：两个大按钮——"我要管控对方"（controller）和"我需要被管控"（controlled）
- `PairingScreen`：
  - 被控端：显示自己的公钥 QR 码 → 等待扫描控制端响应 QR → 完成
  - 控制端：扫描被控端 QR → 生成种子并加密 → 显示响应 QR → 等待完成
- `OnboardingViewModel`：调用 `PairingProtocol` 接口驱动配对流程
- 考虑到 MVP 简化：被控端先展示公钥 QR，控制端扫描后展示响应 QR，被控端再扫描

- [ ] **步骤 1：创建 OnboardingViewModel.kt**

新建 `app/src/main/java/com/peerlock/ui/onboarding/OnboardingViewModel.kt`：

```kotlin
package com.peerlock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.domain.pairing.PairingProtocol
import com.peerlock.domain.pairing.PairingRequest
import com.peerlock.domain.pairing.PairingResponse
import com.peerlock.domain.pairing.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ROLE_SELECTION,
    val role: String = "",
    val pairRequest: PairingRequest? = null,
    val pairRequestQr: String = "",
    val pairResponseQr: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
)

enum class OnboardingStep {
    ROLE_SELECTION,
    SHOW_MY_QR,
    SCAN_PEER_QR,
    COMPLETED,
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val pairingProtocol: PairingProtocol,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectRole(role: String) {
        _uiState.value = _uiState.value.copy(role = role, step = OnboardingStep.SHOW_MY_QR)
        if (role == "controlled") {
            generatePairRequest()
        }
    }

    private fun generatePairRequest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val request = pairingProtocol.generatePairRequest("被控端")
                _uiState.value = _uiState.value.copy(
                    pairRequest = request,
                    pairRequestQr = request.pub,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "生成配对请求失败: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    fun onQrScanned(scannedData: String) {
        val state = _uiState.value
        when (state.role) {
            "controller" -> handleControllerScan(scannedData)
            "controlled" -> handleControlledScan(scannedData)
        }
    }

    private fun handleControllerScan(scannedPubKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val request = PairingRequest(
                    id = "",
                    pub = scannedPubKey,
                    name = "被控端",
                )
                val response = pairingProtocol.processPairRequest(request, "控制端")
                _uiState.value = _uiState.value.copy(
                    pairResponseQr = response.data,
                    step = OnboardingStep.SCAN_PEER_QR,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "处理配对请求失败: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    private fun handleControlledScan(scannedResponse: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = PairingResponse(
                    pub = "",
                    signPub = "",
                    data = scannedResponse,
                )
                val result = pairingProtocol.processPairResponse(response)
                when (result) {
                    is PairingResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            step = OnboardingStep.COMPLETED,
                            isLoading = false,
                        )
                    }
                    is PairingResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = result.message,
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "配对失败: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
```

- [ ] **步骤 2：创建 RoleSelectionScreen.kt**

新建 `app/src/main/java/com/peerlock/ui/onboarding/RoleSelectionScreen.kt`：

```kotlin
package com.peerlock.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectionScreen(
    onRoleSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "PeerLock",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "两人互相监督的屏幕时间管理工具",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onRoleSelected("controller") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("我要管控对方", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { onRoleSelected("controlled") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("我需要被管控", style = MaterialTheme.typography.titleMedium)
        }
    }
}
```

- [ ] **步骤 3：创建 PairingScreen.kt**

新建 `app/src/main/java/com/peerlock/ui/onboarding/PairingScreen.kt`：

```kotlin
package com.peerlock.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay

@Composable
fun PairingScreen(
    role: String,
    onPairingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState.step) {
            OnboardingStep.SHOW_MY_QR -> {
                if (role == "controlled") {
                    Text("请让控制方扫描此二维码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    if (uiState.pairRequestQr.isNotEmpty()) {
                        QrCodeDisplay(content = uiState.pairRequestQr)
                    } else {
                        CircularProgressIndicator()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "扫描完成后，请扫描控制方显示的响应码",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text("请扫描被控端的二维码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "扫描后将生成响应二维码供被控端扫描",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            OnboardingStep.SCAN_PEER_QR -> {
                if (role == "controller") {
                    Text("请让被控端扫描此响应码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    QrCodeDisplay(content = uiState.pairResponseQr)
                } else {
                    Text("请扫描控制方的响应码", style = MaterialTheme.typography.titleMedium)
                }
            }
            OnboardingStep.COMPLETED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("配对完成！", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onPairingComplete) {
                    Text("进入主页")
                }
            }
            else -> {}
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

- [ ] **步骤 4：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`
预期: BUILD SUCCESSFUL

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/ui/onboarding/
git commit -m "feat: 引导流程（角色选择 + 配对 QR 流程）"
```

---

## 任务 5：Controller — 控制端主界面

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/controller/ControllerHomeScreen.kt`
- 新建: `app/src/main/java/com/peerlock/ui/controller/ControllerViewModel.kt`

**设计：**
- 顶部：设备状态卡片（今日屏幕时间、受限应用数）
- 中部：TOTP 码展示（管理码、解锁码、终止码，30秒自动刷新）
- 底部：策略列表（受限应用 + 每日限额）
- `ControllerViewModel`：调用 `TotpEngine.generateCode()` + `SeedManager.retrieveSeed()` 生成当前码

- [ ] **步骤 1：创建 ControllerViewModel.kt**

新建 `app/src/main/java/com/peerlock/ui/controller/ControllerViewModel.kt`：

```kotlin
package com.peerlock.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.seed.SeedManager
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TotpCodeInfo(
    val keyType: KeyType,
    val label: String,
    val code: String,
    val remainingSeconds: Int,
)

data class ControllerUiState(
    val totpCodes: List<TotpCodeInfo> = emptyList(),
    val policies: List<RestrictionPolicy> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val totpEngine: TotpEngine,
    private val seedManager: SeedManager,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

    init {
        loadPolicies()
        startTotpRefreshLoop()
    }

    private fun loadPolicies() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            _uiState.value = _uiState.value.copy(policies = policies, isLoading = false)
        }
    }

    private fun startTotpRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                refreshTotpCodes()
                delay(1000)
            }
        }
    }

    private suspend fun refreshTotpCodes() {
        val codes = KeyType.entries.mapNotNull { keyType ->
            val seed = seedManager.retrieveSeed(keyType) ?: return@mapNotNull null
            val code = totpEngine.generateCode(seed)
            val step = totpEngine.currentStep()
            val remaining = ((step + 1) * 30 - System.currentTimeMillis() / 1000).toInt()
            TotpCodeInfo(
                keyType = keyType,
                label = keyType.label,
                code = code,
                remainingSeconds = remaining.coerceAtLeast(0),
            )
        }
        _uiState.value = _uiState.value.copy(totpCodes = codes)
    }
}
```

- [ ] **步骤 2：创建 ControllerHomeScreen.kt**

新建 `app/src/main/java/com/peerlock/ui/controller/ControllerHomeScreen.kt`：

```kotlin
package com.peerlock.ui.controller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerHomeScreen(
    onNavigateToUnlockRequest: () -> Unit = {},
    viewModel: ControllerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PeerLock 控制端") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                StatusCard(
                    title = "已配对",
                    subtitle = "策略巡检运行中",
                    icon = Icons.Default.Lock,
                )
            }

            item {
                Text(
                    text = "验证码",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(uiState.totpCodes) { codeInfo ->
                TotpCodeCard(codeInfo)
            }

            item {
                Text(
                    text = "策略列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (uiState.policies.isEmpty()) {
                item {
                    Text(
                        text = "暂无策略",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.policies) { policy ->
                    PolicyCard(policy)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TotpCodeCard(codeInfo: TotpCodeInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = codeInfo.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = codeInfo.code,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                    ),
                )
            }
            Text(
                text = "${codeInfo.remainingSeconds}s",
                style = MaterialTheme.typography.titleLarge,
                color = if (codeInfo.remainingSeconds <= 5) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PolicyCard(policy: com.peerlock.domain.policy.RestrictionPolicy) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = policy.targetPackage, style = MaterialTheme.typography.titleSmall)
            policy.dailyLimitMinutes?.let { limit ->
                Text(text = "每日限额: $limit 分钟", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (policy.isBlacklist) "黑名单模式" else "白名单模式",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

- [ ] **步骤 3：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`
预期: BUILD SUCCESSFUL

- [ ] **步骤 4：提交**

```bash
git add app/src/main/java/com/peerlock/ui/controller/
git commit -m "feat: 控制端主界面（TOTP 码展示 + 策略列表）"
```

---

## 任务 6：Controlled — 被控端主界面 + 解锁申请

**涉及文件：**
- 新建: `app/src/main/java/com/peerlock/ui/controlled/ControlledHomeScreen.kt`
- 新建: `app/src/main/java/com/peerlock/ui/controlled/UnlockRequestScreen.kt`
- 新建: `app/src/main/java/com/peerlock/ui/controlled/ControlledViewModel.kt`

**设计：**
- `ControlledHomeScreen`：今日使用统计、受限应用列表、紧急入口
- `UnlockRequestScreen`：选择应用 → 输入时长 → 生成申请码（加密信封）
- `ControlledViewModel`：调用 `RequestProtocol.generateUnlockRequest()` + `StorageRepository` 获取使用数据

- [ ] **步骤 1：创建 ControlledViewModel.kt**

新建 `app/src/main/java/com/peerlock/ui/controlled/ControlledViewModel.kt`：

```kotlin
package com.peerlock.ui.controlled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.request.DeviceInfo
import com.peerlock.domain.request.RequestProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlledUiState(
    val policies: List<RestrictionPolicy> = emptyList(),
    val todayScreenTimeMs: Long = 0L,
    val isLoading: Boolean = true,
    val requestQrCode: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ControlledViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val requestProtocol: RequestProtocol,
    private val policyEngine: PolicyEngine,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlledUiState())
    val uiState: StateFlow<ControlledUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val policies = storageRepository.getActivePolicies()
            val sessionId = securePrefs.sessionId ?: ""
            _uiState.value = _uiState.value.copy(
                policies = policies,
                isLoading = false,
            )
        }
    }

    fun generateUnlockRequest(targetPackage: String, durationMinutes: Int) {
        viewModelScope.launch {
            val sessionId = securePrefs.sessionId ?: return@launch
            val result = requestProtocol.generateUnlockRequest(
                sessionId = sessionId,
                targetPackage = targetPackage,
                requestedDuration = durationMinutes,
                durationMode = "cumulative",
                deviceInfo = DeviceInfo(
                    todayScreenTimeMs = _uiState.value.todayScreenTimeMs,
                    suspendedApps = _uiState.value.policies.map { it.targetPackage },
                    isInSafeMode = false,
                ),
            )
            if (result != null) {
                _uiState.value = _uiState.value.copy(requestQrCode = result)
            } else {
                _uiState.value = _uiState.value.copy(error = "生成请求失败（频率限制或密钥缺失）")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
```

- [ ] **步骤 2：创建 ControlledHomeScreen.kt**

新建 `app/src/main/java/com/peerlock/ui/controlled/ControlledHomeScreen.kt`：

```kotlin
package com.peerlock.ui.controlled

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledHomeScreen(
    onRequestUnlock: () -> Unit = {},
    viewModel: ControlledViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PeerLock 被控端") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                StatusCard(
                    title = "已配对",
                    subtitle = "接受控制方管理",
                    icon = Icons.Default.Phone,
                )
            }

            item {
                Text(
                    text = "受限应用",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (uiState.policies.isEmpty()) {
                item {
                    Text(
                        text = "暂无限制",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.policies) { policy ->
                    Card(
                        onClick = onRequestUnlock,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = policy.targetPackage, style = MaterialTheme.typography.titleSmall)
                                policy.dailyLimitMinutes?.let { limit ->
                                    Text(text = "每日限额: $limit 分钟", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
```

- [ ] **步骤 3：创建 UnlockRequestScreen.kt**

新建 `app/src/main/java/com/peerlock/ui/controlled/UnlockRequestScreen.kt`：

```kotlin
package com.peerlock.ui.controlled

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockRequestScreen(
    onBack: () -> Unit,
    viewModel: ControlledViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPackage by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableStateOf("30") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("申请解锁") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("选择应用", style = MaterialTheme.typography.titleMedium)
            uiState.policies.forEach { policy ->
                FilterChip(
                    selected = selectedPackage == policy.targetPackage,
                    onClick = { selectedPackage = policy.targetPackage },
                    label = { Text(policy.targetPackage) },
                )
            }

            OutlinedTextField(
                value = durationMinutes,
                onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                label = { Text("请求时长（分钟）") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    viewModel.generateUnlockRequest(selectedPackage, durationMinutes.toIntOrNull() ?: 30)
                },
                enabled = selectedPackage.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("生成申请码")
            }

            uiState.requestQrCode?.let { qr ->
                Text("请让控制方扫描此二维码", style = MaterialTheme.typography.titleMedium)
                QrCodeDisplay(content = qr, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            uiState.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```

- [ ] **步骤 4：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`
预期: BUILD SUCCESSFUL

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/peerlock/ui/controlled/
git commit -m "feat: 被控端主界面（受限应用 + 解锁申请）"
```

---

## 任务 7：全量验证 + DI 最终装配

**涉及文件：**
- 无需新增文件，验证全部构建和测试

- [ ] **步骤 1：运行全部测试**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:testDebugUnitTest 2>&1 | tail -15`
预期: ALL PASS

- [ ] **步骤 2：构建验证**

运行: `cd /home/lingnc/workspace/peerlock && ANDROID_HOME=/home/lingnc/workspace/peerlock/android-sdk JAVA_HOME=/home/lingnc/workspace/peerlock/jdk-17 ./gradlew :app:assembleDebug 2>&1 | tail -10`
预期: BUILD SUCCESSFUL

---

## 验证清单

1. **构建**: `./gradlew :app:assembleDebug` — 无编译错误
2. **测试**: `./gradlew :app:testDebugUnitTest` — 所有测试通过
3. **关键功能验证**：
   - 主题：Material3 动态配色，API 33+ 使用 Material You
   - 导航：根据 role + isPaired 自动选择起始路由
   - 引导：角色选择 → 配对 QR 流程 → 完成
   - 控制端：TOTP 码实时刷新（30秒周期）、策略列表展示
   - 被控端：受限应用展示、解锁申请生成 QR 码
   - 共享组件：TOTP 6位输入、QR 码展示、状态卡片
