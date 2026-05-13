package com.peerlock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPairingInfo: () -> Unit = {},
    onNavigateToRoleSelection: () -> Unit = {},
    onNavigateToDeviceOwnerSetup: () -> Unit = {},
    onNavigateToLog: () -> Unit = {},
    onNavigateToIdentityInfo: () -> Unit = {},
    onNavigateToDeviceSelection: () -> Unit = {},
    onL2UnlockedChange: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 监听 onResume，刷新电池优化状态
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.resetComplete) {
        if (uiState.resetComplete) onNavigateToRoleSelection()
    }

    LaunchedEffect(uiState.l2Unlocked) {
        onL2UnlockedChange(uiState.l2Unlocked)
    }

    // 电池优化引导对话框
    if (uiState.showBatteryGuide) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBatteryGuide() },
            title = { Text("关闭电池优化") },
            text = {
                Text(
                    "PeerLock 需要关闭电池优化才能保持后台巡检正常运行。\n\n" +
                        "操作步骤：\n" +
                        "1. 点击下方「前往设置」\n" +
                        "2. 在弹出的系统对话框中点击「允许」\n" +
                        "3. 返回此页面检查状态"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.openBatterySettings() }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBatteryGuide() }) {
                    Text("取消")
                }
            },
        )
    }

    // 身份重置确认对话框
    if (uiState.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelReset() },
            title = { Text("身份重置", color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    "身份重置将清除所有配对数据和加密材料：\n\n" +
                        "· 配对关系\n" +
                        "· TOTP 种子\n" +
                        "· 策略数据\n\n" +
                        "Device Owner 权限将保留。\n" +
                        "此操作不可逆。"
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmIdentityReset() },
                    enabled = !uiState.isResetCooldownActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        if (uiState.isResetCooldownActive) "确认重置 (${uiState.resetCooldownSeconds}s)"
                        else "确认重置"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelReset() }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            // DO 状态
            SettingItem(
                label = "Device Owner",
                value = if (uiState.isDeviceOwner) "✓ 已设置" else "✗ 未设置",
                enabled = true,
                onClick = onNavigateToDeviceOwnerSetup,
            )

            // 电池优化
            SettingItem(
                label = "电池优化",
                value = if (uiState.isBatteryExempt) "✓ 已关闭" else "✗ 未关闭",
                enabled = !uiState.isBatteryExempt,
                onClick = { viewModel.showBatteryGuideDialog() },
            )

            HorizontalDivider()

            // 本机身份
            SettingItem(
                label = "本机身份",
                value = null,
                enabled = true,
                onClick = onNavigateToIdentityInfo,
            )

            // 配对信息
            SettingItem(
                label = "配对信息",
                value = null,
                enabled = true,
                onClick = onNavigateToPairingInfo,
            )

            // 设备选择（仅管控端）
            if (uiState.role == "controller") {
                SettingItem(
                    label = "设备选择",
                    value = null,
                    enabled = true,
                    onClick = onNavigateToDeviceSelection,
                )
            }

            HorizontalDivider()

            // 主题模式
            Column {
                Text(text = "主题模式", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text("跟随系统") },
                    )
                    FilterChip(
                        selected = uiState.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text("浅色") },
                    )
                    FilterChip(
                        selected = uiState.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        label = { Text("深色") },
                    )
                }
            }

            // 语言
            Column {
                Text(text = "语言", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.language == "zh",
                        onClick = { viewModel.setLanguage("zh") },
                        label = { Text("中文") },
                    )
                    FilterChip(
                        selected = uiState.language == "en",
                        onClick = { viewModel.setLanguage("en") },
                        label = { Text("English") },
                    )
                }
            }

            HorizontalDivider()

            // 切换角色（仅未配对时显示）
            if (!uiState.isPaired) {
                TextButton(onClick = onNavigateToRoleSelection) {
                    Text("切换角色")
                }
            }

            // 身份重置（仅已配对时显示）
            if (uiState.isPaired) {
                TextButton(onClick = { viewModel.requestIdentityReset() }) {
                    Text("身份重置", color = MaterialTheme.colorScheme.error)
                }
            }

            // 调试日志
            SettingItem(
                label = "调试日志",
                value = null,
                enabled = true,
                onClick = onNavigateToLog,
            )

            // 版本号（连点7次解锁 L2 高级解除）
            Text(
                text = "版本 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.onVersionTap() },
            )
        }
    }
}

@Composable
private fun SettingItem(
    label: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
