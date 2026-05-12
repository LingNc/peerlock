package com.peerlock.ui.controlled

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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledHomeScreen(
    onRequestUnlock: (String?) -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToReceiveCommand: () -> Unit = {},
    onNavigateToStrategyManagement: () -> Unit = {},
    viewModel: ControlledViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PeerLock 被控端") },
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
                StatusCard(
                    title = "已配对",
                    subtitle = "接受控制方管理",
                    icon = Icons.Default.Phone,
                )
            }

            item {
                Text(
                    text = "受限应用",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToStats,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("使用统计")
                }
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToReceiveCommand,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("接收指令")
                }
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToStrategyManagement,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("管理策略")
                }
            }

            if (uiState.policies.isEmpty()) {
                item {
                    Text(
                        text = "暂无限制",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.policies) { policy ->
                    Card(
                        onClick = { onRequestUnlock(policy.targetPackage) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = policy.targetPackage, style = MaterialTheme.typography.titleSmall)
                                policy.dailyLimitMinutes?.let { limit ->
                                    Text(text = "每日限额: $limit 分钟", style = MaterialTheme.typography.bodySmall)
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
