package it.edgvoip.jarvis.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val filledIcon: ImageVector? = null,
    val outlinedIcon: ImageVector? = null
) {
    data object Phone : Screen(
        route = "phone",
        label = "Telefono",
        filledIcon = Icons.Filled.Call,
        outlinedIcon = Icons.Outlined.Call
    )

    data object AiAgent : Screen(
        route = "ai_agent",
        label = "AI Agent",
        filledIcon = Icons.Filled.SmartToy,
        outlinedIcon = Icons.Outlined.SmartToy
    )

    data object Notifications : Screen(
        route = "notifications",
        label = "Notifiche",
        filledIcon = Icons.Filled.Notifications,
        outlinedIcon = Icons.Outlined.Notifications
    )

    data object Login : Screen(
        route = "login",
        label = "Accesso"
    )

    data object Settings : Screen(
        route = "settings",
        label = "Impostazioni",
        filledIcon = Icons.Filled.Settings,
        outlinedIcon = Icons.Outlined.Settings
    )

    data object Profile : Screen(
        route = "profile",
        label = "Profilo",
        filledIcon = Icons.Filled.Person,
        outlinedIcon = Icons.Outlined.Person
    )

    data object InCall : Screen(
        route = "in_call",
        label = "In Chiamata",
        filledIcon = Icons.Filled.PhoneInTalk,
        outlinedIcon = Icons.Outlined.PhoneInTalk
    )

    data object Conversation : Screen(
        route = "conversation",
        label = "Conversazione",
        filledIcon = Icons.Filled.Chat,
        outlinedIcon = Icons.Outlined.Chat
    )

    companion object {
        val bottomNavItems = listOf(Phone, AiAgent, Notifications)
    }
}
