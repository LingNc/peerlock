package com.peerlock.ui.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay
import com.peerlock.ui.common.QrScanLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestApprovalScreen(
    onBack: () -> Unit,
    viewModel: ControllerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanTrigger by viewModel.scanTrigger.collectAsState()

    QrScanLauncher(
        onResult = { result ->
            result?.let { viewModel.onQrScanned(it) }
        },
        trigger = scanTrigger,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("审批请求") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetApprovalFlow()
                        onBack()
                    }) {
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
            when (uiState.approvalStep) {
                ApprovalStep.IDLE -> {
                    Text("扫描被控端的请求二维码", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { viewModel.requestApprovalScan() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("开始扫描")
                    }
                }
                ApprovalStep.SCANNING -> {
                    Text("正在等待扫描...", style = MaterialTheme.typography.bodyMedium)
                }
                ApprovalStep.REVIEWING -> {
                    uiState.pendingRequest?.let { request ->
                        RequestDetailCard(request)

                        // 时长/模式调整控件（仅 unlock 类型）
                        val payload = request.payload
                        if (payload is com.peerlock.domain.request.RequestPayload.UnlockRequest) {
                            var adjustedDuration by remember { mutableIntStateOf(payload.requestedDuration) }
                            var adjustedMode by remember { mutableStateOf(payload.durationMode) }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("调整解锁参数", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("解锁时长: ${adjustedDuration} 分钟", style = MaterialTheme.typography.bodyMedium)
                                    Slider(
                                        value = adjustedDuration.toFloat(),
                                        onValueChange = { adjustedDuration = it.toInt() },
                                        valueRange = 5f..180f,
                                        steps = 34,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("时长模式", style = MaterialTheme.typography.bodySmall)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilterChip(
                                            selected = adjustedMode == "cumulative",
                                            onClick = { adjustedMode = "cumulative" },
                                            label = { Text("累计") },
                                        )
                                        FilterChip(
                                            selected = adjustedMode == "session",
                                            onClick = { adjustedMode = "session" },
                                            label = { Text("单次") },
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.rejectRequest() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("拒绝")
                                }
                                Button(
                                    onClick = { viewModel.approveRequest(adjustedDuration, adjustedMode) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("批准")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.rejectRequest() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("拒绝")
                                }
                                Button(
                                    onClick = { viewModel.approveRequest() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("批准")
                                }
                            }
                        }
                    }
                }
                ApprovalStep.SHOWING_RESPONSE -> {
                    Text("请让被控端扫描此响应码", style = MaterialTheme.typography.titleMedium)
                    uiState.responseQrCode?.let { qr ->
                        QrCodeDisplay(content = qr, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.resetApprovalFlow() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("完成")
                    }
                }
            }

            uiState.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RequestDetailCard(request: com.peerlock.domain.request.RequestEnvelope) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "请求详情", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "类型: ${request.type}", style = MaterialTheme.typography.bodyMedium)

            // 设备状态信息
            val info = request.deviceInfo
            if (info.todayScreenTimeMs > 0 || info.suspendedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "设备状态", style = MaterialTheme.typography.titleSmall)
                if (info.todayScreenTimeMs > 0) {
                    val minutes = info.todayScreenTimeMs / 60_000
                    Text(text = "今日屏幕时间: ${minutes} 分钟", style = MaterialTheme.typography.bodySmall)
                }
                if (info.suspendedApps.isNotEmpty()) {
                    Text(text = "已暂停应用: ${info.suspendedApps.size} 个", style = MaterialTheme.typography.bodySmall)
                }
                if (info.isInSafeMode) {
                    Text(text = "⚠ 安全模式已激活", style = MaterialTheme.typography.bodySmall)
                }
            }

            when (val payload = request.payload) {
                is com.peerlock.domain.request.RequestPayload.UnlockRequest -> {
                    Text(text = "应用: ${payload.targetPackage}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "请求时长: ${payload.requestedDuration} 分钟", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "时长模式: ${payload.durationMode}", style = MaterialTheme.typography.bodyMedium)
                    payload.reason?.let {
                        Text(text = "理由: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is com.peerlock.domain.request.RequestPayload.ConfigRequest -> {
                    Text(text = "策略变更: ${payload.changes.size} 项", style = MaterialTheme.typography.bodyMedium)
                    payload.changes.forEach { change ->
                        Text(
                            text = "  ${change.targetPackage}: ${change.dailyLimitMinutes ?: "无"}分钟",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
