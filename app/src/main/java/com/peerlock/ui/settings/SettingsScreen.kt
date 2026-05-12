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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPairingInfo: () -> Unit = {},
    onNavigateToRevokeDo: () -> Unit = {},
    onNavigateToRoleSelection: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                enabled = !uiState.isDeviceOwner,
                onClick = { /* TODO: 跳转 DO 设置 */ },
            )

            // 电池优化
            SettingItem(
                label = "电池优化",
                value = if (uiState.isBatteryExempt) "✓ 已关闭" else "✗ 未关闭",
                enabled = !uiState.isBatteryExempt,
                onClick = { /* TODO: 跳转电池优化 */ },
            )

            HorizontalDivider()

            // 配对信息
            SettingItem(
                label = "配对信息",
                value = null,
                enabled = true,
                onClick = onNavigateToPairingInfo,
            )

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

            // 切换角色
            TextButton(onClick = onNavigateToRoleSelection) {
                Text("切换角色")
            }

            // 取消 DO（仅当 DO 已设置且非被控端管控时可用）
            if (uiState.isDeviceOwner) {
                TextButton(onClick = onNavigateToRevokeDo) {
                    Text("取消 Device Owner", color = MaterialTheme.colorScheme.error)
                }
            }

            // 身份重置
            TextButton(onClick = { /* TODO: 身份重置 10s 确认 */ }) {
                Text("身份重置", color = MaterialTheme.colorScheme.error)
            }

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
