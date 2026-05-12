package com.peerlock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.TotpInputField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyUnbindScreen(
    onBack: () -> Unit,
    onUnbound: () -> Unit,
    viewModel: ApplyUnbindViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.unbindComplete) {
        if (uiState.unbindComplete) onUnbound()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("申请解除") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState.step) {
                UnbindStep.WARNING -> {
                    Text("⚠️ 警告", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "申请解除将解除与管控方的配对关系\n" +
                            "管控方的管控将被解除\n" +
                            "加密材料将被清除\n" +
                            "Device Owner 权限保留",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { viewModel.proceedToCodeInput() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isCooldownActive,
                        colors = if (uiState.isCooldownActive) {
                            ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            )
                        },
                    ) {
                        Text(
                            if (uiState.isCooldownActive) "确认 (${uiState.cooldownSeconds}s)"
                            else "确认解除"
                        )
                    }
                }

                UnbindStep.INPUT_CODE -> {
                    Text("输入终止码", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "请输入管控方提供的 6 位终止码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    TotpInputField(
                        onCodeComplete = { code ->
                            viewModel.verifyTerminateCode(code)
                        },
                    )

                    if (uiState.isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("验证中...", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                UnbindStep.UNBOUND -> {
                    // handled by onUnbound() above
                }
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
