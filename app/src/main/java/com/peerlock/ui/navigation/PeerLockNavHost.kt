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
                    navController.navigate(Routes.pairing(role)) {
                        popUpTo(Routes.ROLE_SELECTION) { inclusive = true }
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
                    if (role == "controller") {
                        // 控制端不需要 DO 设置，直接进入主页
                        navController.navigate(Routes.CONTROLLER_HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        // 被控端需要 DO 设置 + 电池优化
                        navController.navigate(Routes.DEVICE_OWNER_SETUP) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.CONTROLLER_HOME) {
            ControllerHomeScreen(
                onNavigateToUnlockRequest = { navController.navigate(Routes.UNLOCK_REQUEST) },
                onNavigateToApproval = { navController.navigate(Routes.REQUEST_APPROVAL) },
                onNavigateToEmergency = { navController.navigate(Routes.EMERGENCY) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
            )
        }

        composable(Routes.CONTROLLED_HOME) {
            ControlledHomeScreen(
                onRequestUnlock = { navController.navigate(Routes.UNLOCK_REQUEST) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
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
                    val destination = if (securePrefs.role == "controller") Routes.CONTROLLER_HOME
                    else Routes.CONTROLLED_HOME
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSkip = {
                    val destination = if (securePrefs.role == "controller") Routes.CONTROLLER_HOME
                    else Routes.CONTROLLED_HOME
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
