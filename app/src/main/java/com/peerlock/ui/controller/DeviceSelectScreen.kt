package com.peerlock.ui.controller

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.data.db.entity.PairingSessionEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceSelectScreen(
    onNavigateToDevice: (String) -> Unit,
    onNavigateToAddDevice: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DeviceSelectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PeerLock 管控端") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
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
                Text(
                    text = "已配对设备",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (uiState.devices.isEmpty()) {
                item {
                    Text(
                        text = "暂无配对设备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.devices) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onNavigateToDevice(device.sessionId) },
                        onLongClick = { viewModel.showReceipt(device) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToAddDevice,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加新设备")
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // 回执 QR 对话框
    if (uiState.showReceiptQr && uiState.receiptQrData != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissReceipt() },
            title = { Text("回执信息") },
            text = {
                Column {
                    Text("请将以下数据发送给被控端：")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.receiptQrData!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(uiState.receiptQrData!!))
                    viewModel.dismissReceipt()
                }) {
                    Text("复制数据")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissReceipt() }) {
                    Text("关闭")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(
    device: PairingSessionEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val statusColor = when (device.status) {
        "ACTIVE" -> MaterialTheme.colorScheme.primary
        "WAITING" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = statusColor,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.peerDeviceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = device.identityFingerprint,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = device.status,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
        }
    }
}
