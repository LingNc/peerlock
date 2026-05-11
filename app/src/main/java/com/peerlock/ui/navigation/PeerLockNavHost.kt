package com.peerlock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.ui.controlled.ControlledHomeScreen
import com.peerlock.ui.controlled.UnlockRequestScreen
import com.peerlock.ui.controller.ControllerHomeScreen
import com.peerlock.ui.controller.RequestApprovalScreen
import com.peerlock.ui.emergency.EmergencyScreen
import com.peerlock.ui.onboarding.DeviceOwnerSetupScreen
import com.peerlock.ui.onboarding.PairingScreen
import com.peerlock.ui.onboarding.RoleSelectionScreen
import com.peerlock.ui.stats.StatsScreen
import com.peerlock.ui.settings.SettingsScreen

object Routes {
    const val ROLE_SELECTION = "role_selection"
    const val PAIRING = "pairing/{role}"
    const val CONTROLLER_HOME = "controller_home"
    const val CONTROLLED_HOME = "controlled_home"
    const val UNLOCK_REQUEST = "unlock_request"
    const val REQUEST_APPROVAL = "request_approval"
    const val EMERGENCY = "emergency"
    const val DEVICE_OWNER_SETUP = "device_owner_setup"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    fun pairing(role: String) = "pairing/$role"
}

@Composable
fun PeerLockNavHost(
    navController: NavHostController,
    securePrefs: SecurePrefs,
) {
    val startDestination = remember {
        when {
            !securePrefs.isPaired -> Routes.ROLE_SELECTION
            securePrefs.role == "controller" -> Routes.CONTROLLER_HOME
            else -> Routes.CONTROLLED_HOME
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    if (role == "controller") {
                        navController.navigate(Routes.pairing("controller"))
                    } else {
                        // 被控端先走 DO + 电池优化检查
                        navController.navigate(Routes.DEVICE_OWNER_SETUP)
                    }
                }
            )
        }

        composable(
            Routes.PAIRING,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "controlled"
            PairingScreen(
                role = role,
                onPairingComplete = {
                    val destination = if (role == "controller") Routes.CONTROLLER_HOME
                    else Routes.CONTROLLED_HOME
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CONTROLLER_HOME) {
            ControllerHomeScreen(
                onNavigateToUnlockRequest = { navController.navigate(Routes.UNLOCK_REQUEST) },
                onNavigateToApproval = { navController.navigate(Routes.REQUEST_APPROVAL) },
                onNavigateToEmergency = { navController.navigate(Routes.EMERGENCY) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CONTROLLED_HOME) {
            ControlledHomeScreen(
                onRequestUnlock = { navController.navigate(Routes.UNLOCK_REQUEST) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.UNLOCK_REQUEST) {
            UnlockRequestScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REQUEST_APPROVAL) {
            RequestApprovalScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.EMERGENCY) {
            EmergencyScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DEVICE_OWNER_SETUP) {
            DeviceOwnerSetupScreen(
                onContinue = {
                    if (securePrefs.isPaired) {
                        // 已配对（从设置进入），回主页
                        val destination = if (securePrefs.role == "controller") Routes.CONTROLLER_HOME
                        else Routes.CONTROLLED_HOME
                        navController.navigate(destination) { popUpTo(0) { inclusive = true } }
                    } else {
                        // 未配对（角色选择后），进配对
                        navController.navigate(Routes.PAIRING.replace("{role}", "controlled")) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onSkip = {
                    if (securePrefs.isPaired) {
                        val destination = if (securePrefs.role == "controller") Routes.CONTROLLER_HOME
                        else Routes.CONTROLLED_HOME
                        navController.navigate(destination) { popUpTo(0) { inclusive = true } }
                    } else {
                        navController.navigate(Routes.PAIRING.replace("{role}", "controlled")) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
