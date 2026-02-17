package it.edgvoip.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import it.edgvoip.jarvis.ui.theme.CallRed
import it.edgvoip.jarvis.ui.theme.DarkBackground
import it.edgvoip.jarvis.ui.theme.DarkOutline
import it.edgvoip.jarvis.ui.theme.DarkSurface
import it.edgvoip.jarvis.ui.theme.DarkSurfaceVariant
import it.edgvoip.jarvis.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val preferredAgent by viewModel.preferredAgent.collectAsState()
    val sipCodec by viewModel.sipCodec.collectAsState()
    val echoCancellation by viewModel.echoCancellation.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val availableAgents by viewModel.availableAgents.collectAsState()
    val agentsLoading by viewModel.agentsLoading.collectAsState()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val isLoggingOut by viewModel.isLoggingOut.collectAsState()

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutDialog() },
            title = {
                Text(
                    text = "Esci dall'account",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Sei sicuro di voler uscire? Dovrai effettuare nuovamente il login.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.logout(onLoggedOut) },
                    enabled = !isLoggingOut
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Esci", color = CallRed)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLogoutDialog() }) {
                    Text("Annulla")
                }
            },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Impostazioni",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SettingsSection(title = "Sicurezza", icon = Icons.Default.Security)
            }
            item {
                SettingSwitchItem(
                    title = "Autenticazione biometrica",
                    subtitle = "Usa impronta digitale o riconoscimento facciale",
                    icon = Icons.Default.Fingerprint,
                    checked = biometricEnabled,
                    onCheckedChange = { viewModel.setBiometricEnabled(it) }
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSection(title = "AI Agent", icon = Icons.Default.SmartToy)
            }
            item {
                val agentOptions = buildList {
                    add("" to "Nessuno")
                    availableAgents.forEach { agent ->
                        val typeLabel = when {
                            agent.isElevenLabs -> " (ElevenLabs)"
                            else -> ""
                        }
                        add(agent.id to "${agent.name}$typeLabel")
                    }
                }
                val selectedLabel = availableAgents.find { it.id == preferredAgent }?.name
                    ?: if (preferredAgent.isEmpty()) "Nessuno selezionato" else preferredAgent

                SettingDropdownItem(
                    title = "Agente preferito",
                    subtitle = if (agentsLoading) "Caricamento..." else selectedLabel,
                    icon = Icons.Default.SmartToy,
                    options = agentOptions,
                    selectedValue = preferredAgent,
                    onValueChange = { viewModel.setPreferredAgent(it) }
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSection(title = "Audio SIP", icon = Icons.Default.GraphicEq)
            }
            item {
                SettingDropdownItem(
                    title = "Codec preferito",
                    subtitle = sipCodec,
                    icon = Icons.Default.SurroundSound,
                    options = listOf(
                        "PCMU" to "PCMU (G.711 µ-law)",
                        "PCMA" to "PCMA (G.711 A-law)",
                        "OPUS" to "Opus",
                        "G729" to "G.729"
                    ),
                    selectedValue = sipCodec,
                    onValueChange = { viewModel.setSipCodec(it) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Echo cancellation",
                    subtitle = "Riduce l'eco durante le chiamate",
                    icon = Icons.Default.GraphicEq,
                    checked = echoCancellation,
                    onCheckedChange = { viewModel.setEchoCancellation(it) }
                )
            }
            item {
                SettingSwitchItem(
                    title = "Volume boost",
                    subtitle = "Aumenta il volume dell'audio in chiamata",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    checked = volumeBoost,
                    onCheckedChange = { viewModel.setVolumeBoost(it) }
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSection(title = "Aspetto", icon = Icons.Default.Palette)
            }
            item {
                SettingThemeSelector(
                    selectedTheme = appTheme,
                    onThemeChange = { viewModel.setAppTheme(it) }
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSection(title = "Informazioni", icon = Icons.Default.Info)
            }
            item {
                SettingInfoItem(
                    title = "Versione app",
                    value = "1.0.0"
                )
            }
            item {
                SettingInfoItem(
                    title = "Tenant",
                    value = currentUser?.tenantSlug ?: "-"
                )
            }
            item {
                SettingInfoItem(
                    title = "Utente",
                    value = if (currentUser != null)
                        "${currentUser!!.firstName} ${currentUser!!.lastName}"
                    else "-"
                )
            }
            item {
                SettingInfoItem(
                    title = "Email",
                    value = currentUser?.email ?: "-"
                )
            }
            item {
                SettingInfoItem(
                    title = "Ruolo",
                    value = currentUser?.role ?: "-"
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSection(title = "Account", icon = Icons.Default.Person)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.showLogoutConfirmation() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CallRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = CallRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Esci",
                        color = CallRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = PrimaryBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

@Composable
private fun SettingDropdownItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = options.find { it.first == selectedValue }?.second ?: subtitle,
                    color = PrimaryBlue,
                    fontSize = 13.sp
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurface)
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (value == selectedValue) PrimaryBlue else Color.White,
                            fontWeight = if (value == selectedValue) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onValueChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingThemeSelector(
    selectedTheme: String,
    onThemeChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThemeOption(
            icon = Icons.Default.DarkMode,
            label = "Scuro",
            isSelected = selectedTheme == "dark",
            onClick = { onThemeChange("dark") },
            modifier = Modifier.weight(1f)
        )
        ThemeOption(
            icon = Icons.Default.LightMode,
            label = "Chiaro",
            isSelected = selectedTheme == "light",
            onClick = { onThemeChange("light") },
            modifier = Modifier.weight(1f)
        )
        ThemeOption(
            icon = Icons.Default.BrightnessAuto,
            label = "Sistema",
            isSelected = selectedTheme == "system",
            onClick = { onThemeChange("system") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeOption(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) PrimaryBlue.copy(alpha = 0.2f) else DarkSurfaceVariant
    val contentColor = if (isSelected) PrimaryBlue else Color.White.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingInfoItem(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 1.dp,
        color = DarkOutline.copy(alpha = 0.5f)
    )
}
