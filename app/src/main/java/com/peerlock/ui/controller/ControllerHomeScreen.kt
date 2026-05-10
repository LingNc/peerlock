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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.ui.common.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerHomeScreen(
    onNavigateToUnlockRequest: () -> Unit = {},
    viewModel: ControllerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PeerLock 控制端") })
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
                    subtitle = "策略巡检运行中",
                    icon = Icons.Default.Lock,
                )
            }

            item {
                Text(
                    text = "验证码",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(uiState.totpCodes) { codeInfo ->
                TotpCodeCard(codeInfo)
            }

            item {
                Text(
                    text = "策略列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (uiState.policies.isEmpty()) {
                item {
                    Text(
                        text = "暂无策略",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.policies) { policy ->
                    PolicyCard(policy)
                }
            }

            if (uiState.otpauthUris.isNotEmpty()) {
                item {
                    Text(
                        text = "导出到外部应用",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(uiState.otpauthUris) { (label, uri) ->
                    OtpauthUriCard(label = label, uri = uri)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TotpCodeCard(codeInfo: TotpCodeInfo) {
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

@Composable
private fun PolicyCard(policy: RestrictionPolicy) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = policy.targetPackage, style = MaterialTheme.typography.titleSmall)
            policy.dailyLimitMinutes?.let { limit ->
                Text(text = "每日限额: $limit 分钟", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (policy.isBlacklist) "黑名单模式" else "白名单模式",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OtpauthUriCard(label: String, uri: String) {
    val clipboardManager = LocalClipboardManager.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = uri,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = {
                clipboardManager.setText(AnnotatedString(uri))
            }) {
                Text("复制 URI")
            }
        }
    }
}
