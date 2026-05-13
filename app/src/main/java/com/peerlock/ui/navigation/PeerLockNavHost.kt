package com.peerlock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.peerlock.data.db.dao.PairingSessionDao
import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.ui.controlled.ControlledHomeScreen
import com.peerlock.ui.controlled.ReceiveCommandScreen
import com.peerlock.ui.controlled.StrategyManagementScreen
import com.peerlock.ui.controlled.UnlockRequestScreen
import com.peerlock.ui.controller.ControllerHomeScreen
import com.peerlock.ui.controller.DeviceSelectScreen
import com.peerlock.ui.controller.RequestApprovalScreen
import com.peerlock.ui.controller.UnlockAppScreen
import com.peerlock.ui.emergency.EmergencyScreen
import com.peerlock.ui.onboarding.AlreadyBoundScreen
import com.peerlock.ui.onboarding.DeviceOwnerSetupScreen
import com.peerlock.ui.onboarding.PairingConfirmScreen
import com.peerlock.ui.onboarding.PairingScreen
import com.peerlock.ui.onboarding.RoleSelectionScreen
import com.peerlock.ui.stats.StatsScreen
import com.peerlock.ui.settings.ApplyUnbindScreen
import com.peerlock.ui.settings.PairingInfoScreen
import com.peerlock.ui.settings.RevokeDoScreen
import com.peerlock.ui.settings.SettingsScreen

object Routes {
    const val ROLE_SELECTION = "role_selection"
    const val PAIRING = "pairing/{role}"
    const val CONTROLLER_HOME = "controller_home"
    const val CONTROLLED_HOME = "controlled_home"
    const val UNLOCK_REQUEST = "unlock_request"
    const val REQUEST_APPROVAL = "request_approval"
    const val DEVICE_OWNER_SETUP = "device_owner_setup"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    // V3 新增路由
    const val DEVICE_SELECTION = "device_selection"
    const val PAIRING_CONFIRM = "pairing_confirm/{role}"
    const val RECEIVE_COMMAND = "receive_command"
    const val PAIRING_INFO = "pairing_info"
    const val REVOKE_DO = "revoke_do"
    const val UNLOCK_APP = "unlock_app"
    const val ADJUST_POLICY = "adjust_policy"
    const val STRATEGY_MANAGEMENT = "strategy_management"
    const val TERMINATE_CODE = "terminate_code"
    const val APPLY_UNBIND = "apply_unbind"
    const val ALREADY_BOUND = "already_bound"
    const val DEVICE_OWNER_SETUP_SETTINGS = "device_owner_setup_settings"

    fun pairing(role: String) = "pairing/$role"
    fun pairingConfirm(role: String) = "pairing_confirm/$role"
}

@Composable
fun PeerLockNavHost(
    navController: NavHostController,
    securePrefs: SecurePrefs,
    pairingSessionDao: PairingSessionDao,
) {
    val startDestination = remember {
        // V3 启动路由：SecurePrefs 作为同步信号（PairingProtocolImpl 同步写入 Room）
        when {
            securePrefs.isPaired && securePrefs.role == "controller" -> Routes.DEVICE_SELECTION
            securePrefs.isPaired && securePrefs.role == "controlled" -> Routes.CONTROLLED_HOME
            else -> Routes.ROLE_SELECTION
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        // === 初始化流程 ===

        composable(Routes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    if (role == "controller") {
                        navController.navigate(Routes.pairing("controller"))
                    } else if (securePrefs.isPaired && securePrefs.role == "controlled") {
                        navController.navigate(Routes.ALREADY_BOUND)
                    } else {
                        navController.navigate(Routes.DEVICE_OWNER_SETUP)
                    }
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.ALREADY_BOUND) {
            AlreadyBoundScreen(
                onNavigateToPairingInfo = { navController.navigate(Routes.PAIRING_INFO) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DEVICE_OWNER_SETUP_SETTINGS) {
            DeviceOwnerSetupScreen(
                onContinue = { navController.popBackStack() },
                onSkip = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DEVICE_OWNER_SETUP) {
            DeviceOwnerSetupScreen(
                onContinue = {
                    navController.navigate(Routes.PAIRING.replace("{role}", "controlled")) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSkip = {},
                showSkip = false,
                onBack = { navController.popBackStack() },
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
                    navController.navigate(Routes.pairingConfirm(role)) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = {
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Routes.PAIRING_CONFIRM,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "controlled"
            PairingConfirmScreen(
                onConfirmed = {
                    val destination = if (role == "controller") Routes.DEVICE_SELECTION
                    else Routes.CONTROLLED_HOME
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onCancel = {
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // === 管控端 ===

        composable(Routes.DEVICE_SELECTION) {
            DeviceSelectScreen(
                onNavigateToDevice = { navController.navigate(Routes.CONTROLLER_HOME) },
                onNavigateToAddDevice = { navController.navigate(Routes.pairing("controller")) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CONTROLLER_HOME) {
            ControllerHomeScreen(
                onNavigateToUnlockRequest = { navController.navigate(Routes.UNLOCK_APP) },
                onNavigateToApproval = { navController.navigate(Routes.REQUEST_APPROVAL) },
                onNavigateToAdjustPolicy = { navController.navigate(Routes.ADJUST_POLICY) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.REQUEST_APPROVAL) {
            RequestApprovalScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.UNLOCK_APP) {
            UnlockAppScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADJUST_POLICY) {
            StrategyManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TERMINATE_CODE) {
            EmergencyScreen(onBack = { navController.popBackStack() })
        }

        // === 被控端 ===

        composable(Routes.CONTROLLED_HOME) {
            ControlledHomeScreen(
                onRequestUnlock = { navController.navigate(Routes.UNLOCK_REQUEST) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToReceiveCommand = { navController.navigate(Routes.RECEIVE_COMMAND) },
                onNavigateToStrategyManagement = { navController.navigate(Routes.STRATEGY_MANAGEMENT) },
            )
        }

        composable(Routes.UNLOCK_REQUEST) {
            UnlockRequestScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.RECEIVE_COMMAND) {
            ReceiveCommandScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.STRATEGY_MANAGEMENT) {
            StrategyManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.APPLY_UNBIND) {
            ApplyUnbindScreen(
                onBack = { navController.popBackStack() },
                onUnbound = {
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // === 共用页面 ===

        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPairingInfo = { navController.navigate(Routes.PAIRING_INFO) },
                onNavigateToRevokeDo = { navController.navigate(Routes.REVOKE_DO) },
                onNavigateToRoleSelection = {
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToDeviceOwnerSetup = {
                    navController.navigate(Routes.DEVICE_OWNER_SETUP_SETTINGS)
                },
            )
        }

        composable(Routes.PAIRING_INFO) {
            PairingInfoScreen(
                onBack = { navController.popBackStack() },
                onNavigateToTerminateCode = { navController.navigate(Routes.TERMINATE_CODE) },
                onNavigateToApplyUnbind = { navController.navigate(Routes.APPLY_UNBIND) },
            )
        }

        composable(Routes.REVOKE_DO) {
            RevokeDoScreen(
                onBack = { navController.popBackStack() },
                onRevoked = {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                },
            )
        }
    }
}
