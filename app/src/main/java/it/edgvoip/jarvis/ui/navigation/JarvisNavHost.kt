package it.edgvoip.jarvis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import it.edgvoip.jarvis.ui.screens.LoginScreen
import it.edgvoip.jarvis.ui.screens.PhoneScreen
import it.edgvoip.jarvis.ui.screens.AiAgentScreen
import it.edgvoip.jarvis.ui.screens.NotificationsScreen
import it.edgvoip.jarvis.ui.screens.SettingsScreen
import it.edgvoip.jarvis.ui.screens.ProfileScreen
import it.edgvoip.jarvis.ui.screens.InCallScreen

@Composable
fun JarvisNavHost(
    navController: NavHostController,
    isAuthenticated: Boolean,
    onLoggedIn: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val startDestination = if (isAuthenticated) Screen.Phone.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    onLoggedIn()
                    navController.navigate(Screen.Phone.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Phone.route) {
            PhoneScreen(
                onNavigateToCall = {
                    navController.navigate(Screen.InCall.route)
                }
            )
        }

        composable(Screen.AiAgent.route) {
            AiAgentScreen()
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onLoggedOut = {
                    onLoggedOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToSupport = {}
            )
        }

        composable(Screen.InCall.route) { _ ->
            InCallScreen(
                onCallEnded = { navController.popBackStack() }
            )
        }

        composable("${Screen.Conversation.route}/{conversationId}") { _ ->
            AiAgentScreen()
        }
    }
}

fun NavHostController.navigateToBottomNavRoute(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
