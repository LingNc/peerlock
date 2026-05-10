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

@Composable
fun DeviceOwnerSetupScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    viewModel: DeviceOwnerSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

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
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
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
            if (viewModel.supportsWirelessAdb && !uiState.showWirelessGuide) {
                OutlinedButton(
                    onClick = { viewModel.toggleWirelessGuide() },
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
                Text(
                    text = "1. 进入「设置 → 开发者选项 → 无线调试」",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "2. 点击「使用配对码配对设备」",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "3. 记下显示的配对码和 IP:端口",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "4. 在电脑终端执行：adb pair <IP:端口>",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "5. 输入配对码完成配对",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "6. 然后执行以下命令设置 Device Owner：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = viewModel.adbCommand,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { clipboardManager.setText(AnnotatedString(viewModel.adbCommand)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("复制命令")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.toggleWirelessGuide() },
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
                    text = viewModel.adbCommand,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { clipboardManager.setText(AnnotatedString(viewModel.adbCommand)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("复制命令")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.checkDeviceOwnerStatus() },
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
