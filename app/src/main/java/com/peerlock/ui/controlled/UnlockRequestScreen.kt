package com.peerlock.ui.controlled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay
import com.peerlock.ui.common.TotpInputField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockRequestScreen(
    onBack: () -> Unit,
    viewModel: ControlledViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var durationMinutes by remember { mutableStateOf("30") }
    // 默认：无预选包名时显示快速码模式，有预选包名时显示扫码模式
    var useQuickCode by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("申请解锁") },
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
            // 模式切换
            OutlinedButton(
                onClick = { useQuickCode = !useQuickCode },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (useQuickCode) "切换到申请模式（选择应用 + 扫码）" else "切换到快速码模式（直接输入 TOTP 码）")
            }

            if (useQuickCode) {
                // 快速码模式：无需选择包名，输入 6 位 TOTP 码即可解锁全部受限应用
                Text("输入控制方提供的 6 位解锁码", style = MaterialTheme.typography.titleMedium)
                Text(
                    "将解锁所有受限应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TotpInputField(
                    onCodeComplete = { code ->
                        viewModel.verifyQuickCode(
                            code,
                            null,
                            durationMinutes.toIntOrNull() ?: 30,
                        )
                    },
                )
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("解锁时长（分钟）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                uiState.quickCodeResult?.let { result ->
                    Text(text = result, color = MaterialTheme.colorScheme.primary)
                    Button(
                        onClick = {
                            viewModel.clearQuickCodeResult()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("完成")
                    }
                }
            } else {
                // 申请模式：选择应用 + 生成请求二维码
                Text("选择要解锁的应用", style = MaterialTheme.typography.titleMedium)
                uiState.policies.forEach { policy ->
                    FilterChip(
                        selected = policy.targetPackage in selectedPackages,
                        onClick = {
                            selectedPackages = if (policy.targetPackage in selectedPackages) {
                                selectedPackages - policy.targetPackage
                            } else {
                                selectedPackages + policy.targetPackage
                            }
                        },
                        label = { Text(policy.targetPackage.substringAfterLast('.')) },
                    )
                }

                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("请求时长（分钟）") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        viewModel.generateUnlockRequest(selectedPackages.first(), durationMinutes.toIntOrNull() ?: 30)
                    },
                    enabled = selectedPackages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("生成申请码")
                }

                uiState.requestQrCode?.let { qr ->
                    Text("请让控制方扫描此二维码", style = MaterialTheme.typography.titleMedium)
                    QrCodeDisplay(content = qr, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }

            uiState.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
