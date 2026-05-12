package com.peerlock.ui.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnlockAppScreen(
    onBack: () -> Unit,
    viewModel: UnlockAppViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("解锁应用") },
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
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("选择受限应用", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.policies.forEach { policy ->
                        FilterChip(
                            selected = policy.targetPackage in uiState.selectedPackages,
                            onClick = { viewModel.togglePackage(policy.targetPackage) },
                            label = { Text(policy.targetPackage.substringAfterLast('.')) },
                        )
                    }
                }
            }

            item {
                Text("解锁时长: ${uiState.durationMinutes} 分钟", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = uiState.durationMinutes.toFloat(),
                    onValueChange = { viewModel.setDuration(it.toInt()) },
                    valueRange = 5f..180f,
                    steps = 34,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("5分", style = MaterialTheme.typography.labelSmall)
                    Text("180分", style = MaterialTheme.typography.labelSmall)
                }
            }

            item {
                Button(
                    onClick = { viewModel.generateUnlockInstruction() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.selectedPackages.isNotEmpty() && !uiState.isLoading,
                ) {
                    Text("生成解锁指令")
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

            if (uiState.generatedQr != null) {
                item {
                    Text("请让被控端扫描此指令码", style = MaterialTheme.typography.titleMedium)
                    QrCodeDisplay(
                        content = uiState.generatedQr!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.generatedQr!!))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("复制指令数据")
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重新生成")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
