package com.peerlock.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.domain.repository.DailySummary
import com.peerlock.domain.repository.HourlySummary
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.repository.UsageRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class StatsTab { TODAY, WEEK, MONTH }

data class StatsUiState(
    val tab: StatsTab = StatsTab.TODAY,
    val hourlyData: List<HourlySummary> = emptyList(),
    val dailyData: List<DailySummary> = emptyList(),
    val detailRecords: List<UsageRecord> = emptyList(),
    val selectedHour: Int? = null,
    val selectedDate: String? = null,
    val isLoading: Boolean = true,
    val role: String = "",
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val today: String = LocalDate.now().toString()

    init {
        loadToday()
        _uiState.value = _uiState.value.copy(role = securePrefs.role ?: "")
    }

    fun selectTab(tab: StatsTab) {
        _uiState.value = _uiState.value.copy(tab = tab, selectedHour = null, detailRecords = emptyList())
        when (tab) {
            StatsTab.TODAY -> loadToday()
            StatsTab.WEEK -> loadWeek()
            StatsTab.MONTH -> loadMonth()
        }
    }

    fun selectHour(hour: Int) {
        _uiState.value = _uiState.value.copy(selectedHour = hour)
        viewModelScope.launch {
            val date = _uiState.value.selectedDate ?: today
            val records = storageRepository.getUsageByDate(date)
            val filtered = records.filter { record ->
                val recordHour = (record.startTime % 86_400_000L / 3_600_000L).toInt()
                recordHour == hour
            }
            _uiState.value = _uiState.value.copy(detailRecords = filtered)
        }
    }

    fun selectDate(date: String) {
        _uiState.value = _uiState.value.copy(selectedDate = date, selectedHour = null, detailRecords = emptyList())
        loadHourlyForDate(date)
    }

    private fun loadToday() {
        viewModelScope.launch {
            val hourly = storageRepository.getHourlySummary(today)
            _uiState.value = _uiState.value.copy(
                hourlyData = hourly,
                selectedDate = today,
                isLoading = false,
            )
        }
    }

    private fun loadWeek() {
        viewModelScope.launch {
            val endDate = today
            val startDate = LocalDate.now().minusDays(6).toString()
            val daily = storageRepository.getDailySummary(startDate, endDate)
            _uiState.value = _uiState.value.copy(dailyData = daily, isLoading = false)
        }
    }

    private fun loadMonth() {
        viewModelScope.launch {
            val endDate = today
            val startDate = LocalDate.now().minusDays(29).toString()
            val daily = storageRepository.getDailySummary(startDate, endDate)
            _uiState.value = _uiState.value.copy(dailyData = daily, isLoading = false)
        }
    }

    private fun loadHourlyForDate(date: String) {
        viewModelScope.launch {
            val hourly = storageRepository.getHourlySummary(date)
            _uiState.value = _uiState.value.copy(hourlyData = hourly)
        }
    }
}
