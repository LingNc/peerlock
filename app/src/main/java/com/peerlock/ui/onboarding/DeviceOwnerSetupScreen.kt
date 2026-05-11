package com.peerlock.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun DeviceOwnerSetupScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    viewModel: DeviceOwnerSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // 监听 onResume，检测电池优化状态变化
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
            onDoComplete = { viewModel.advanceToDoComplete() },
            onSkip = onSkip,
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
        )
    }
}

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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("设置 Device Owner", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isDeviceOwner) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            Text("Device Owner 已设置", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDoComplete, modifier = Modifier.fillMaxWidth()) {
                Text("继续")
            }
        } else {
            Text(
                text = "PeerLock 需要 Device Owner 权限才能限制应用使用。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 无线 ADB 配对引导（Android 11+）
            if (supportsWirelessAdb && !uiState.showWirelessGuide) {
                OutlinedButton(
                    onClick = onToggleGuide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("使用无线 ADB 配对（无需电脑）")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.showWirelessGuide) {
                Text(
                    text = "无线 ADB 配对步骤：",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "1. 进入「设置 → 开发者选项 → 无线调试」", style = MaterialTheme.typography.bodyMedium)
                Text(text = "2. 点击「使用配对码配对设备」", style = MaterialTheme.typography.bodyMedium)
                Text(text = "3. 记下显示的配对码和 IP:端口", style = MaterialTheme.typography.bodyMedium)
                Text(text = "4. 在电脑终端执行：adb pair <IP:端口>", style = MaterialTheme.typography.bodyMedium)
                Text(text = "5. 输入配对码完成配对", style = MaterialTheme.typography.bodyMedium)
                Text(text = "6. 然后执行以下命令设置 Device Owner：", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = adbCommand,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { clipboardManager.setText(AnnotatedString(adbCommand)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("复制命令")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onToggleGuide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("收起")
                }
            } else {
                // 传统 USB ADB 方式
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
                ) {
                    Text("复制命令")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCheckStatus,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("检查设置状态")
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("跳过（功能受限）")
            }
        }
    }
}

@Composable
private fun BatteryOptimizationStep(
    isExempt: Boolean,
    requested: Boolean,
    onRequest: () -> Unit,
    onVerify: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("电池优化白名单", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

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
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("跳过（后台巡检可能不稳定）")
            }
        }
    }
}
