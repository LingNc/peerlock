package com.peerlock.ui.controlled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrScanLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveCommandScreen(
    onBack: () -> Unit,
    viewModel: ReceiveCommandViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanTrigger by viewModel.scanTrigger.collectAsState()
    var pasteInput by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("接收指令") },
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
            when (uiState.step) {
                ReceiveStep.IDLE -> {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("接收管控方的主动指令", style = MaterialTheme.typography.titleMedium)
                    }

                    item {
                        Button(
                            onClick = {
                                showScanner = true
                                viewModel.requestScan()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("扫描指令")
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = pasteInput,
                            onValueChange = { pasteInput = it },
                            label = { Text("粘贴指令数据") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (pasteInput.isNotBlank()) {
                                    viewModel.onCommandScanned(pasteInput.trim())
                                    pasteInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pasteInput.isNotBlank(),
                        ) {
                            Text("确认")
                        }
                    }

                    if (showScanner) {
                        item {
                            QrScanLauncher(
                                onResult = { data ->
                                    if (data != null) {
                                        viewModel.onCommandScanned(data)
                                        showScanner = false
                                    }
                                },
                                trigger = scanTrigger,
                            )
                        }
                    }
                }

                ReceiveStep.REVIEWING -> {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("指令详情", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))

                                when (uiState.commandType) {
                                    CommandType.UNLOCK -> {
                                        Text("类型: 解锁指令", style = MaterialTheme.typography.bodyMedium)
                                        Text("目标应用: ${uiState.unlockTarget ?: "全部"}", style = MaterialTheme.typography.bodyMedium)
                                        Text("解锁时长: ${uiState.unlockDuration ?: 5} 分钟", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    CommandType.POLICY -> {
                                        Text("类型: 策略调整指令", style = MaterialTheme.typography.bodyMedium)
                                        uiState.policyChanges.forEach { change ->
                                            Text("· $change", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    CommandType.STATS -> {
                                        Text("类型: 统计请求", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    null -> {}
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.confirmExecute() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("确认执行")
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { viewModel.reject() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("拒绝")
                        }
                    }
                }

                ReceiveStep.EXECUTED -> {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "指令已执行",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }

                    item {
                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("返回")
                        }
                    }
                }
            }

            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
