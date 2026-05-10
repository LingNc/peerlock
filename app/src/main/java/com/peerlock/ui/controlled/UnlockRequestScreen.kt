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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockRequestScreen(
    onBack: () -> Unit,
    viewModel: ControlledViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPackage by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableStateOf("30") }

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
            Text("选择应用", style = MaterialTheme.typography.titleMedium)
            uiState.policies.forEach { policy ->
                FilterChip(
                    selected = selectedPackage == policy.targetPackage,
                    onClick = { selectedPackage = policy.targetPackage },
                    label = { Text(policy.targetPackage) },
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
                    viewModel.generateUnlockRequest(selectedPackage, durationMinutes.toIntOrNull() ?: 30)
                },
                enabled = selectedPackage.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("生成申请码")
            }

            uiState.requestQrCode?.let { qr ->
                Text("请让控制方扫描此二维码", style = MaterialTheme.typography.titleMedium)
                QrCodeDisplay(content = qr, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            uiState.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
