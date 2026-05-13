package com.peerlock.ui.settings

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingInfoScreen(
    onBack: () -> Unit,
    onNavigateToTerminateCode: () -> Unit,
    onNavigateToApplyUnbind: () -> Unit,
    viewModel: PairingInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    // 删除种子确认对话框
    showDeleteConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除种子", color = MaterialTheme.colorScheme.error) },
            text = { Text("删除种子后将无法恢复此会话的 TOTP 验证码。此操作不可逆。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSeedsForSession(sessionId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            },
        )
    }

    // 删除历史记录确认对话框（5 秒倒计时）
    if (uiState.deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("确认删除记录", color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    if (uiState.deleteCountdown > 0)
                        "删除后此配对记录将永久消失，无法恢复。\n请等待 ${uiState.deleteCountdown} 秒..."
                    else
                        "删除后此配对记录将永久消失，无法恢复。确认删除？"
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    enabled = uiState.deleteCountdown == 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("取消") }
            },
        )
    }

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
                title = { Text("配对信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("加载中...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.isPaired) {
                // 未配对状态
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("当前未配对", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    Text(
                        "此设备尚未与任何设备配对。\n请先完成配对流程。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 已配对状态 — 当前配对信息
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("当前配对", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoRow("对方设备", uiState.peerDeviceName)
                            InfoRow("身份指纹", uiState.fingerprint)
                            InfoRow("配对时间", uiState.pairingTime)
                            InfoRow("会话 ID", uiState.sessionId)
                            InfoRow("状态", uiState.status)
                        }
                    }
                }

                if (uiState.role == "controller") {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.toggleShowCodes() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (uiState.showCodes) "隐藏验证码" else "查看验证码")
                        }
                    }

                    if (uiState.showCodes) {
                        items(uiState.totpCodes) { codeInfo ->
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
                    }

                    item {
                        TextButton(
                            onClick = { showDeleteConfirm = uiState.sessionIdFull },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("申请删除种子")
                        }
                    }
                }

                // 底部红色操作
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                if (uiState.role == "controller") {
                    item {
                        TextButton(
                            onClick = onNavigateToTerminateCode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "显示终止码",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    item {
                        TextButton(
                            onClick = onNavigateToApplyUnbind,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "申请解除",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // 历史配对记录（始终显示，无论是否已配对）
            if (uiState.historySessions.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("历史配对记录", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { viewModel.toggleMultiSelect() }) {
                            Text(if (uiState.multiSelectMode) "取消" else "多选")
                        }
                    }
                }

                items(uiState.historySessions) { session ->
                    val isSelected = session.sessionId in uiState.selectedIds
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (uiState.multiSelectMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSelect(session.sessionId) },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.peerDeviceName, style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    Text(
                                        when (session.status) {
                                            "WAITING" -> "等待配对"
                                            "ACTIVE" -> "已配对"
                                            "REVOKED" -> "已撤销"
                                            "ARCHIVED" -> "已归档"
                                            else -> session.status
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when (session.status) {
                                            "WAITING" -> MaterialTheme.colorScheme.primary
                                            "REVOKED" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Text(
                                        " · ${session.role}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!uiState.multiSelectMode) {
                                when (session.status) {
                                    "WAITING" -> {
                                        TextButton(onClick = { viewModel.archiveSession(session.sessionId) }) {
                                            Text("归档", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    "REVOKED", "ARCHIVED" -> {
                                        TextButton(onClick = { viewModel.requestDelete(session.sessionId) }) {
                                            Text("删除", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.multiSelectMode && uiState.selectedIds.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.deleteSelected() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("删除选中 (${uiState.selectedIds.size})")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        } // else (not loading)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
