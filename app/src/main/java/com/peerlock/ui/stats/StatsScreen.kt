package com.peerlock.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peerlock.domain.repository.DailySummary
import com.peerlock.domain.repository.HourlySummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onRequestStats: () -> Unit = {},
    onSendStats: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.role == "controller") {
                        IconButton(onClick = onRequestStats) {
                            Icon(Icons.Default.Refresh, contentDescription = "请求统计")
                        }
                    } else {
                        IconButton(onClick = onSendStats) {
                            Icon(Icons.Default.Share, contentDescription = "发送统计")
                        }
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
            // 标签切换
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.tab == StatsTab.TODAY,
                        onClick = { viewModel.selectTab(StatsTab.TODAY) },
                        label = { Text("今天") },
                    )
                    FilterChip(
                        selected = uiState.tab == StatsTab.WEEK,
                        onClick = { viewModel.selectTab(StatsTab.WEEK) },
                        label = { Text("本周") },
                    )
                    FilterChip(
                        selected = uiState.tab == StatsTab.MONTH,
                        onClick = { viewModel.selectTab(StatsTab.MONTH) },
                        label = { Text("本月") },
                    )
                }
            }

            // 图表
            item {
                when (uiState.tab) {
                    StatsTab.TODAY -> {
                        HourlyBarChart(
                            hourlyData = uiState.hourlyData,
                            onHourClick = { viewModel.selectHour(it) },
                        )
                    }
                    StatsTab.WEEK,
                    StatsTab.MONTH -> {
                        DailyLineChart(
                            dailyData = uiState.dailyData,
                            onDateClick = { viewModel.selectDate(it) },
                        )
                    }
                }
            }

            // 选中日期的小时级图表（周/月模式下点击某天后显示）
            if (uiState.tab != StatsTab.TODAY && uiState.selectedDate != null) {
                item {
                    Text(
                        text = "${uiState.selectedDate} 小时分布",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    HourlyBarChart(
                        hourlyData = uiState.hourlyData,
                        onHourClick = { viewModel.selectHour(it) },
                    )
                }
            }

            // 选中小时的 30 秒级详情
            uiState.selectedHour?.let { hour ->
                item {
                    Text(
                        text = "${hour}:00 - ${hour + 1}:00 使用详情",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (uiState.detailRecords.isEmpty()) {
                    item {
                        Text(
                            text = "无详细记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(uiState.detailRecords) { record ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = record.packageName.substringAfterLast('.'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${record.durationMs / 1000}秒",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HourlyBarChart(
    hourlyData: List<HourlySummary>,
    onHourClick: (Int) -> Unit,
) {
    if (hourlyData.isEmpty()) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val barColor = MaterialTheme.colorScheme.primary
    val hourMap = hourlyData.groupBy { it.hour }.mapValues { (_, v) -> v.sumOf { it.totalMs } }
    val values = (0..23).map { hourMap[it] ?: 0L }
    val maxMinutes = values.max().coerceAtLeast(1L) / 60_000.0

    Column {
        Text(
            text = "每小时使用时长（分钟）",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(values, maxMinutes) {
                    detectTapGestures { offset ->
                        val barWidth = size.width / 28f
                        val maxHeight = size.height - 20f
                        // 判断点击落在哪个小时的柱子上
                        val hour = ((offset.x - barWidth / 2) / (size.width / 26f)).toInt().coerceIn(0, 23)
                        // 验证点击在柱子高度范围内
                        val minutes = (values[hour] ?: 0L) / 60_000.0
                        val barHeight = (minutes / maxMinutes * maxHeight).toFloat()
                        val barTop = size.height - barHeight
                        if (offset.y >= barTop && values[hour] > 0L) {
                            onHourClick(hour)
                        }
                    }
                },
        ) {
            val barWidth = size.width / 28f
            val maxHeight = size.height - 20f
            val baseY = size.height

            values.forEachIndexed { hour, ms ->
                val minutes = ms / 60_000.0
                val barHeight = (minutes / maxMinutes * maxHeight).toFloat()
                val x = (hour + 1) * (size.width / 26f)
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, baseY - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall)
            Text("6", style = MaterialTheme.typography.labelSmall)
            Text("12", style = MaterialTheme.typography.labelSmall)
            Text("18", style = MaterialTheme.typography.labelSmall)
            Text("23", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DailyLineChart(
    dailyData: List<DailySummary>,
    onDateClick: (String) -> Unit,
) {
    if (dailyData.isEmpty()) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val dateMap = dailyData.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.totalMs } }
    val sortedDates = dateMap.keys.sorted()
    val values = sortedDates.map { dateMap[it] ?: 0L }
    val maxMinutes = values.max().coerceAtLeast(1L) / 60_000.0

    Column {
        Text(
            text = "每日使用时长（分钟）",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(sortedDates, values, maxMinutes) {
                    detectTapGestures { offset ->
                        if (values.size < 2) return@detectTapGestures
                        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                        val maxHeight = size.height - 20f
                        // Find closest data point
                        val idx = (offset.x / stepX).toInt().coerceIn(0, sortedDates.size - 1)
                        val minutes = values[idx] / 60_000.0
                        val expectedY = size.height - (minutes / maxMinutes * maxHeight).toFloat()
                        if (kotlin.math.abs(offset.y - expectedY) < 40f) {
                            onDateClick(sortedDates[idx])
                        }
                    }
                },
        ) {
            if (values.size < 2) return@Canvas
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            val maxHeight = size.height - 20f
            val baseY = size.height

            val path = Path()
            values.forEachIndexed { i, ms ->
                val minutes = ms / 60_000.0
                val x = i * stepX
                val y = baseY - (minutes / maxMinutes * maxHeight).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

            // 绘制数据点
            values.forEachIndexed { i, ms ->
                val minutes = ms / 60_000.0
                val x = i * stepX
                val y = baseY - (minutes / maxMinutes * maxHeight).toFloat()
                drawCircle(lineColor, radius = 5f, center = Offset(x, y))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            sortedDates.forEach { date ->
                Text(
                    text = date.takeLast(5),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
