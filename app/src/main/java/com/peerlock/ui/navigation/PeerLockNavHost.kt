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
import com.peerlock.ui.common.PlaceholderScreen
import com.peerlock.ui.controlled.ControlledHomeScreen
import com.peerlock.ui.controlled.ReceiveCommandScreen
import com.peerlock.ui.controlled.UnlockRequestScreen
import com.peerlock.ui.controller.ControllerHomeScreen
import com.peerlock.ui.controller.DeviceSelectScreen
import com.peerlock.ui.controller.RequestApprovalScreen
import com.peerlock.ui.controller.UnlockAppScreen
import com.peerlock.ui.onboarding.DeviceOwnerSetupScreen
import com.peerlock.ui.onboarding.PairingScreen
import com.peerlock.ui.onboarding.RoleSelectionScreen
import com.peerlock.ui.stats.StatsScreen
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
        // V3 启动路由：仅管控配对→设备选择页，仅被控配对→被控主页，未配对/双端→角色选择
        // TODO: 多会话成熟后改为查询 PairingSessionDao.getActiveByRole
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
                    } else {
                        navController.navigate(Routes.DEVICE_OWNER_SETUP)
                    }
                }
            )
        }

        composable(Routes.DEVICE_OWNER_SETUP) {
            DeviceOwnerSetupScreen(
                onContinue = {
                    navController.navigate(Routes.PAIRING.replace("{role}", "controlled")) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Routes.PAIRING.replace("{role}", "controlled")) {
                        popUpTo(0) { inclusive = true }
                    }
                },
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
                    val destination = if (role == "controller") Routes.CONTROLLER_HOME
                    else Routes.CONTROLLED_HOME
                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
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
            PlaceholderScreen(title = "调整策略", onBack = { navController.popBackStack() })
        }

        composable(Routes.TERMINATE_CODE) {
            PlaceholderScreen(title = "终止码", onBack = { navController.popBackStack() })
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
            PlaceholderScreen(title = "策略管理", onBack = { navController.popBackStack() })
        }

        composable(Routes.APPLY_UNBIND) {
            PlaceholderScreen(title = "申请解除", onBack = { navController.popBackStack() })
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
            )
        }

        composable(Routes.PAIRING_INFO) {
            PlaceholderScreen(title = "配对信息", onBack = { navController.popBackStack() })
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
