package com.peerlock.ui.emergency

import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    viewModel: EmergencyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDestroyConfirm by remember { mutableStateOf(false) }
    var l2TapCount by remember { mutableIntStateOf(0) }

    // FLAG_SECURE 防截图
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("紧急逃生") },
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
            // L1 终止码
            Text("L1 终止码", style = MaterialTheme.typography.titleMedium)
            Text(
                "使用终止码可解除配对并清除加密材料，但保留 Device Owner 权限和用户数据。",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = uiState.destroyCode,
                onValueChange = { viewModel.updateDestroyCode(it) },
                label = { Text("6位终止码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.destroyExecuted,
            )

            if (uiState.destroyExecuted) {
                Text("L1 终止已执行", color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        viewModel.verifyDestroyCode()
                        showDestroyConfirm = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("验证终止码")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // L2 高级解除（隐藏入口）
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    l2TapCount++
                },
            )

            if (l2TapCount >= 7) {
                Text("L2 高级解除", style = MaterialTheme.typography.titleMedium)
                Text(
                    "需要通过 ADB 执行一次性命令。将移除 Device Owner 权限和所有密钥。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedButton(
                    onClick = { viewModel.generateAdbCommand() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("生成 ADB 命令")
                }

                uiState.adbCommand?.let { cmd ->
                    Text("请在电脑终端执行以下命令：", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = cmd,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (uiState.l2Executed) {
                    Text("L2 解除已执行", color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // L1 终止确认对话框
    if (showDestroyConfirm && uiState.destroyVerified && !uiState.destroyExecuted) {
        AlertDialog(
            onDismissRequest = { showDestroyConfirm = false },
            title = { Text("确认终止") },
            text = { Text("此操作将清除所有加密材料并解除配对。Device Owner 权限和用户数据将保留。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.executeDestroy()
                    showDestroyConfirm = false
                }) {
                    Text("确认终止", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestroyConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 验证失败提示
    if (showDestroyConfirm && !uiState.destroyVerified && uiState.error != null) {
        AlertDialog(
            onDismissRequest = { showDestroyConfirm = false },
            title = { Text("验证失败") },
            text = { Text(uiState.error ?: "") },
            confirmButton = {
                TextButton(onClick = { showDestroyConfirm = false }) {
                    Text("确定")
                }
            },
        )
    }
}
