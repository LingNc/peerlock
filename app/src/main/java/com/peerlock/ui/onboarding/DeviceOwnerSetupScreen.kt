package com.peerlock.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.peerlock.system.adb.AdbPairingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOwnerSetupScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToRevokeDo: () -> Unit = {},
    showSkip: Boolean = false,
    showRevokeDo: Boolean = false,
    viewModel: DeviceOwnerSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onActivityResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (uiState.phase) {
        SetupPhase.DO_SETUP -> DeviceOwnerStep(
            uiState = uiState,
            adbCommand = viewModel.adbCommand,
            supportsWirelessAdb = viewModel.supportsWirelessAdb,
            clipboardManager = clipboardManager,
            onCheckStatus = { viewModel.checkDeviceOwnerStatus() },
            onToggleGuide = { viewModel.toggleWirelessGuide() },
            onDoComplete = {
                if (viewModel.advanceToDoComplete()) {
                    onContinue()
                }
            },
            onSkip = onSkip,
            onBack = onBack,
            onNavigateToRevokeDo = onNavigateToRevokeDo,
            showSkip = showSkip,
            showRevokeDo = showRevokeDo,
            onStartAutoSetup = { viewModel.startAutoSetup() },
            onStopAutoSetup = { viewModel.stopAutoSetup() },
        )
        SetupPhase.BATTERY_OPTIMIZATION -> BatteryOptimizationStep(
            isExempt = uiState.isBatteryExempt,
            requested = uiState.batteryRequested,
            onRequest = { viewModel.requestBatteryExemption() },
            onVerify = { viewModel.checkBatteryOptimization() },
            onContinue = {
                viewModel.skipBatteryOptimization()
                onContinue()
            },
            onSkip = {
                viewModel.skipBatteryOptimization()
                onContinue()
            },
            onBack = onBack,
            showSkip = showSkip,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceOwnerStep(
    uiState: DeviceOwnerSetupUiState,
    adbCommand: String,
    supportsWirelessAdb: Boolean,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onCheckStatus: () -> Unit,
    onToggleGuide: () -> Unit,
    onDoComplete: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNavigateToRevokeDo: () -> Unit = {},
    showSkip: Boolean = false,
    showRevokeDo: Boolean = false,
    onStartAutoSetup: () -> Unit = {},
    onStopAutoSetup: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置 Device Owner") },
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
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isDeviceOwner) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
                Text("Device Owner 已设置", style = MaterialTheme.typography.titleMedium)
                uiState.statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDoComplete, modifier = Modifier.fillMaxWidth()) {
                    Text("继续")
                }
                if (showRevokeDo) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onNavigateToRevokeDo, modifier = Modifier.fillMaxWidth()) {
                        Text("取消 Device Owner", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Text(
                    text = "PeerLock 需要 Device Owner 权限才能限制应用使用。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 一键设置按钮（Android 11+）
                if (supportsWirelessAdb) {
                    when (uiState.autoSetupState) {
                        AdbPairingState.IDLE -> {
                            Button(
                                onClick = onStartAutoSetup,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("一键设置 Device Owner")
                            }
                            Text(
                                text = "需要先在开发者选项中开启「无线调试」\n配对码将在通知栏输入",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        AdbPairingState.DISCOVERING, AdbPairingState.FOUND,
                        AdbPairingState.PAIRING, AdbPairingState.PAIRED -> {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.autoSetupMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "请在通知栏输入配对码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = onStopAutoSetup, modifier = Modifier.fillMaxWidth()) {
                                Text("取消")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        AdbPairingState.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp),
                            )
                            Text(
                                text = uiState.autoSetupMessage ?: "设置成功",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        AdbPairingState.ERROR -> {
                            Text(
                                text = uiState.autoSetupMessage ?: "设置失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onStartAutoSetup,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("重试") }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // 手动 ADB 方式（折叠）
                if (supportsWirelessAdb && !uiState.showWirelessGuide) {
                    OutlinedButton(
                        onClick = onToggleGuide,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("手动 ADB 命令设置")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (uiState.showWirelessGuide) {
                    Text(text = "手动 ADB 步骤：", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "1. 在电脑终端执行: adb pair <IP:端口>", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "2. 输入配对码完成配对", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "3. 然后执行以下命令设置 Device Owner：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = adbCommand,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { clipboardManager.setText(AnnotatedString(adbCommand)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("复制命令") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onToggleGuide, modifier = Modifier.fillMaxWidth()) {
                        Text("收起")
                    }
                } else if (!supportsWirelessAdb) {
                    Text(
                        text = "请在电脑上执行以下命令（设备需已通过 USB 连接且未添加任何账户）：",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = adbCommand,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { clipboardManager.setText(AnnotatedString(adbCommand)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("复制命令") }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCheckStatus,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("检查设置状态")
                }

                uiState.statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isDeviceOwner) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (showSkip) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text("跳过（功能受限）")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryOptimizationStep(
    isExempt: Boolean,
    requested: Boolean,
    onRequest: () -> Unit,
    onVerify: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    showSkip: Boolean = true,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池优化白名单") },
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
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isExempt) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
                Text("已关闭电池优化", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PeerLock 后台巡检可以正常运行",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("继续")
                }
            } else {
                Text(
                    text = "PeerLock 需要关闭电池优化才能保持后台巡检正常运行。\n否则系统可能会延迟或停止巡检服务。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("关闭电池优化")
                }

                if (requested) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "请在弹出的系统对话框中点击「允许」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onVerify,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("检查是否已关闭")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (showSkip) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text("跳过（后台巡检可能不稳定）")
                    }
                }
            }
        }
    }
}
