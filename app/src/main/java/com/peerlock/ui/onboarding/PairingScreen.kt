package com.peerlock.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay
import com.peerlock.ui.common.QrScanLauncher

@Composable
fun PairingScreen(
    role: String,
    onPairingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanTrigger by viewModel.scanTrigger.collectAsState()

    QrScanLauncher(
        onResult = { result ->
            result?.let { viewModel.onQrScanned(it) }
        },
        trigger = scanTrigger,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState.step) {
            OnboardingStep.SHOW_MY_QR -> {
                if (role == "controlled") {
                    Text("请让控制方扫描此二维码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    if (uiState.pairRequestQr.isNotEmpty()) {
                        QrCodeDisplay(content = uiState.pairRequestQr)
                    } else {
                        CircularProgressIndicator()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "扫描完成后，请扫描控制方显示的响应码",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.requestScan() }) {
                        Text("扫描响应码")
                    }
                } else {
                    Text("请扫描被控端的二维码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "扫描后将生成响应二维码供被控端扫描",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.requestScan() }) {
                        Text("扫描二维码")
                    }
                }
            }
            OnboardingStep.SCAN_PEER_QR -> {
                if (role == "controller") {
                    Text("请让被控端扫描此响应码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    QrCodeDisplay(content = uiState.pairResponseQr)
                } else {
                    Text("请扫描控制方的响应码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.requestScan() }) {
                        Text("扫描响应码")
                    }
                }
            }
            OnboardingStep.COMPLETED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("配对完成！", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onPairingComplete) {
                    Text("进入主页")
                }
            }
            else -> {}
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
    }
}
