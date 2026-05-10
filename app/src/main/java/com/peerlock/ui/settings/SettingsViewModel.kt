package com.peerlock.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "zh",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
) : ViewModel() {

    private val prefs = application.getSharedPreferences("peerlock_settings", 0)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.SYSTEM },
            language = prefs.getString("language", "zh") ?: "zh",
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _uiState.value = _uiState.value.copy(language = lang)
    }
}
