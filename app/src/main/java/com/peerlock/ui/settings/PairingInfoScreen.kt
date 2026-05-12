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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 当前配对信息
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
                        onClick = { viewModel.deleteSeedsForSession(uiState.sessionIdFull) },
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

            // 历史配对记录
            if (uiState.historySessions.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("历史配对记录", style = MaterialTheme.typography.titleMedium)
                }

                items(uiState.historySessions) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(session.peerDeviceName, style = MaterialTheme.typography.bodyMedium)
                                Text(session.status, style = MaterialTheme.typography.bodySmall)
                            }
                            if (session.status == "REVOKED") {
                                TextButton(onClick = { viewModel.deleteSeedsForSession(session.sessionId) }) {
                                    Text("删除种子", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
