package com.peerlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.ui.navigation.PeerLockNavHost
import com.peerlock.ui.theme.PeerLockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var securePrefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PeerLockTheme {
                val navController = rememberNavController()
                PeerLockNavHost(
                    navController = navController,
                    securePrefs = securePrefs,
                )
            }
        }
    }
}
