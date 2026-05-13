package com.peerlock.ui.controlled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.ui.common.QrCodeDisplay
import com.peerlock.ui.common.TotpInputField
import com.peerlock.ui.settings.PolicyMode
import com.peerlock.ui.settings.StrategyManagementViewModel
import com.peerlock.ui.settings.StrategyTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyManagementScreen(
    onBack: () -> Unit,
    viewModel: StrategyManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val title = if (uiState.role == "controller") "调整策略" else "策略管理"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                .padding(padding),
        ) {
            // Tab 切换
            TabRow(selectedTabIndex = uiState.currentTab.ordinal) {
                Tab(
                    selected = uiState.currentTab == StrategyTab.APP_POLICY,
                    onClick = { viewModel.switchTab(StrategyTab.APP_POLICY) },
                    text = { Text("应用策略") },
                )
                Tab(
                    selected = uiState.currentTab == StrategyTab.CONFIG_PARAMS,
                    onClick = { viewModel.switchTab(StrategyTab.CONFIG_PARAMS) },
                    text = { Text("配置参数") },
                )
            }

            when (uiState.currentTab) {
                StrategyTab.APP_POLICY -> {
                    AppPolicyTab(
                        state = uiState.appPolicy,
                        installedApps = uiState.installedApps,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onToggle = { viewModel.togglePolicy(it) },
                        onSelectAll = { viewModel.selectAllPolicies() },
                        onUpdateLimit = { id, min -> viewModel.updateDailyLimit(id, min) },
                        onSwitchMode = { viewModel.switchMode(it) },
                        onSave = { viewModel.requestSave() },
                        onGenerateRequest = { viewModel.generateChangeRequest() },
                        onVerifyCode = { viewModel.verifyManagementCode(it) },
                        onCancelCodeInput = { viewModel.cancelCodeInput() },
                        onAddPolicy = { viewModel.addPolicyForApp(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                StrategyTab.CONFIG_PARAMS -> {
                    ConfigParamsTab(
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPolicyTab(
    state: com.peerlock.ui.settings.PolicyTabState,
    installedApps: List<com.peerlock.ui.settings.AppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onUpdateLimit: (Long, Int) -> Unit,
    onSwitchMode: (PolicyMode) -> Unit,
    onSave: () -> Unit,
    onGenerateRequest: () -> Unit,
    onVerifyCode: (String) -> Unit,
    onCancelCodeInput: () -> Unit,
    onAddPolicy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("选择目标应用", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSelectAll) {
                    Text("全选")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("搜索应用") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == PolicyMode.MANAGEMENT_CODE,
                    onClick = { onSwitchMode(PolicyMode.MANAGEMENT_CODE) },
                    label = { Text("管理码模式") },
                )
                FilterChip(
                    selected = state.mode == PolicyMode.REQUEST,
                    onClick = { onSwitchMode(PolicyMode.REQUEST) },
                    label = { Text("申请模式") },
                )
            }
        }

        // 已有策略的应用
        val filteredPolicies = if (searchQuery.isBlank()) state.policies
        else state.policies.filter {
            it.targetPackage.contains(searchQuery, ignoreCase = true)
        }
        items(filteredPolicies) { policy ->
            val isSelected = policy.id in state.selectedIds
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(policy.id) },
                        label = { Text(policy.targetPackage.substringAfterLast('.')) },
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val currentLimit = state.changes[policy.id]?.dailyLimitMinutes
                            ?: policy.dailyLimitMinutes
                            ?: 60
                        Text("日限制: $currentLimit 分钟", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = currentLimit.toFloat(),
                            onValueChange = { onUpdateLimit(policy.id, it.toInt()) },
                            valueRange = 5f..480f,
                            steps = 94,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 无策略的应用（可添加）
        val policyPackages = state.policies.map { it.targetPackage }.toSet()
        val unmanagedApps = installedApps.filter { app ->
            app.packageName !in policyPackages &&
                (searchQuery.isBlank() ||
                    app.packageName.contains(searchQuery, ignoreCase = true) ||
                    app.appName.contains(searchQuery, ignoreCase = true))
        }
        if (unmanagedApps.isNotEmpty()) {
            item {
                Text(
                    "可添加限制的应用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(unmanagedApps) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onAddPolicy(app.packageName) }) {
                            Icon(Icons.Default.Add, contentDescription = "添加限制")
                        }
                    }
                }
            }
        }

        // 管理码输入区域
        if (state.showCodeInput) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("输入管理码", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "请输入 6 位管理码以确认保存",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TotpInputField(onCodeComplete = onVerifyCode)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onCancelCodeInput) {
                            Text("取消")
                        }
                    }
                }
            }
        }

        // 生成的 QR 显示
        if (state.generatedQr != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("请让被控端扫描此调整指令", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        QrCodeDisplay(
                            content = state.generatedQr,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            if (state.mode == PolicyMode.MANAGEMENT_CODE && state.showCodeInput.not()) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isDirty,
                ) {
                    Text("保存修改")
                }
            } else if (state.mode == PolicyMode.REQUEST) {
                Button(
                    onClick = onGenerateRequest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isDirty && state.generatedQr == null,
                ) {
                    Text("生成调整指令")
                }
            }
        }

        if (state.error != null) {
            item {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ConfigParamsTab(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("配置参数", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("默认解锁时长、申请模式默认时长等配置参数将在后续版本中支持。", style = MaterialTheme.typography.bodyMedium)
    }
}
