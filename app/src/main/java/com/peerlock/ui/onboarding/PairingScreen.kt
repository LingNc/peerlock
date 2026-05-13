package com.peerlock.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay
import com.peerlock.ui.common.QrScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    role: String,
    onPairingComplete: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showPasteInput by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    LaunchedEffect(role) {
        viewModel.selectRole(role)
    }

    if (showScanner) {
        QrScannerScreen(
            onResult = { result ->
                showScanner = false
                viewModel.onQrScanned(result)
            },
            onClose = { showScanner = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配对") },
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
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
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
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                clipboardManager.setText(AnnotatedString(uiState.pairRequestQr))
                            }) {
                                Text("复制配对数据")
                            }
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
                        OutlinedButton(onClick = { showScanner = true }) {
                            Text("扫描响应码")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!showPasteInput) {
                            OutlinedButton(onClick = { showPasteInput = true }) {
                                Text("粘贴响应数据")
                            }
                        } else {
                            OutlinedTextField(
                                value = pasteText,
                                onValueChange = { pasteText = it },
                                label = { Text("粘贴控制方的响应数据") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (pasteText.isNotBlank()) {
                                        viewModel.onQrScanned(pasteText.trim())
                                        showPasteInput = false
                                        pasteText = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("确认")
                            }
                        }
                    } else {
                        // 管控端
                        if (uiState.pairResponseQr.isEmpty()) {
                            // 尚未扫描请求 — 显示扫描/粘贴入口
                            Text("请扫描被控端的二维码", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "扫描后将生成响应二维码供被控端扫描",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { showScanner = true }) {
                                Text("扫描二维码")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!showPasteInput) {
                                OutlinedButton(onClick = { showPasteInput = true }) {
                                    Text("粘贴配对数据")
                                }
                            } else {
                                OutlinedTextField(
                                    value = pasteText,
                                    onValueChange = { pasteText = it },
                                    label = { Text("粘贴被控端的配对数据") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (pasteText.isNotBlank()) {
                                            viewModel.onQrScanned(pasteText.trim())
                                            showPasteInput = false
                                            pasteText = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("确认")
                                }
                            }
                        } else {
                            // 已处理请求 — 显示响应QR + 完成配对
                            Text("请让被控端扫描此响应码", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(24.dp))
                            QrCodeDisplay(content = uiState.pairResponseQr)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                clipboardManager.setText(AnnotatedString(uiState.pairResponseQr))
                            }) {
                                Text("复制响应数据")
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { onPairingComplete() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("完成配对")
                            }
                        }
                    }
                }
                OnboardingStep.SCAN_PEER_QR -> {
                    if (role == "controller") {
                        Text("请让被控端扫描此响应码", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        QrCodeDisplay(content = uiState.pairResponseQr)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.pairResponseQr))
                        }) {
                            Text("复制响应数据")
                        }
                    } else {
                        Text("请扫描控制方的响应码", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { showScanner = true }) {
                            Text("扫描响应码")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!showPasteInput) {
                            OutlinedButton(onClick = { showPasteInput = true }) {
                                Text("粘贴响应数据")
                            }
                        } else {
                            OutlinedTextField(
                                value = pasteText,
                                onValueChange = { pasteText = it },
                                label = { Text("粘贴控制方的响应数据") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (pasteText.isNotBlank()) {
                                        viewModel.onQrScanned(pasteText.trim())
                                        showPasteInput = false
                                        pasteText = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("确认")
                            }
                        }
                    }
                }
                OnboardingStep.COMPLETED -> {
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
}
