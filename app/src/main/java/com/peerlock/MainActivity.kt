package com.peerlock

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.ui.navigation.PeerLockNavHost
import com.peerlock.ui.theme.PeerLockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var securePrefs: SecurePrefs
    @Inject lateinit var pairingSessionDao: PairingSessionDao

    private lateinit var settingsPrefs: SharedPreferences
    private val themeMode = mutableIntStateOf(0)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme_mode") {
            themeMode.intValue = settingsPrefs.getInt("theme_mode", 0)
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsPrefs = getSharedPreferences("peerlock_settings", MODE_PRIVATE)
        themeMode.intValue = settingsPrefs.getInt("theme_mode", 0)
        settingsPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        setContent {
            val darkTheme = when (themeMode.intValue) {
                1 -> false   // LIGHT
                2 -> true    // DARK
                else -> isSystemInDarkTheme()  // SYSTEM
            }

            PeerLockTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                PeerLockNavHost(
                    navController = navController,
                    securePrefs = securePrefs,
                    pairingSessionDao = pairingSessionDao,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
