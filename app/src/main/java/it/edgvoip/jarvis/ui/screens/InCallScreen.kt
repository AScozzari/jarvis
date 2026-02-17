package it.edgvoip.jarvis.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import it.edgvoip.jarvis.sip.CallDirection
import it.edgvoip.jarvis.sip.CallState
import it.edgvoip.jarvis.sip.SipManager
import it.edgvoip.jarvis.ui.theme.CallGreen
import it.edgvoip.jarvis.ui.theme.CallRed
import it.edgvoip.jarvis.ui.theme.DarkBackground
import it.edgvoip.jarvis.ui.theme.DarkSurfaceVariant
import it.edgvoip.jarvis.ui.theme.PrimaryBlue
import it.edgvoip.jarvis.ui.theme.WarningOrange

@Composable
fun InCallScreen(
    onCallEnded: () -> Unit = {},
    viewModel: InCallViewModel = hiltViewModel()
) {
    val sipManager = viewModel.sipManager
    val callState by sipManager.callState.collectAsState()
    val currentCall by sipManager.currentCall.collectAsState()
    val isMuted by sipManager.isMuted.collectAsState()
    val isSpeakerOn by sipManager.isSpeakerOn.collectAsState()
    val isOnHold by sipManager.isOnHold.collectAsState()

    var showDtmfPad by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferNumber by remember { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current

    if (callState == CallState.IDLE && currentCall == null) {
        onCallEnded()
        return
    }

    val callerNumber = currentCall?.number ?: "Sconosciuto"
    val callerName = currentCall?.name?.ifEmpty { null }
    val duration = currentCall?.duration ?: 0
    val isIncoming = currentCall?.direction == CallDirection.INCOMING

    val statusText = when (callState) {
        CallState.CALLING -> "Chiamata in corso..."
        CallState.RINGING -> "Squilla..."
        CallState.INCOMING -> "Chiamata in arrivo"
        CallState.CONNECTED -> "Connesso"
        CallState.HOLDING -> "In attesa"
        CallState.DISCONNECTED -> "Terminata"
        CallState.IDLE -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E14),
                        Color(0xFF0D1117),
                        Color(0xFF111820)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            if (callerName != null) {
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = callerNumber,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = callerNumber,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusText,
                color = when (callState) {
                    CallState.CONNECTED -> CallGreen
                    CallState.HOLDING -> WarningOrange
                    CallState.DISCONNECTED -> CallRed
                    else -> Color.White.copy(alpha = 0.7f)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            if (callState == CallState.CONNECTED || callState == CallState.HOLDING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDurationTimer(duration),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            val initials = (callerName ?: callerNumber)
                .split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("")
                .ifEmpty { "?" }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                PrimaryBlue.copy(alpha = 0.3f),
                                PrimaryBlue.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = PrimaryBlue,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(0.4f))

            if (callState == CallState.INCOMING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CallRed)
                                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            sipManager.hangupCall()
                        },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Rifiuta",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Rifiuta",
                            color = CallRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CallGreen)
                                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            sipManager.answerCall()
                        },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Rispondi",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Rispondi",
                            color = CallGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (callState != CallState.DISCONNECTED) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallActionButton(
                            icon = Icons.Default.MicOff,
                            label = "Muto",
                            isActive = isMuted,
                            activeColor = CallRed,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isMuted) sipManager.unmuteCall() else sipManager.muteCall()
                            }
                        )

                        InCallActionButton(
                            icon = Icons.Default.Dialpad,
                            label = "Tastiera",
                            isActive = showDtmfPad,
                            activeColor = PrimaryBlue,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showDtmfPad = !showDtmfPad
                            }
                        )

                        InCallActionButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "Altoparlante",
                            isActive = isSpeakerOn,
                            activeColor = PrimaryBlue,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sipManager.toggleSpeaker()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallActionButton(
                            icon = Icons.Default.Pause,
                            label = "In Attesa",
                            isActive = isOnHold,
                            activeColor = WarningOrange,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isOnHold) sipManager.unholdCall() else sipManager.holdCall()
                            }
                        )

                        InCallActionButton(
                            icon = Icons.Default.PhoneForwarded,
                            label = "Trasferisci",
                            isActive = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showTransferDialog = !showTransferDialog
                                transferNumber = ""
                            }
                        )

                        InCallActionButton(
                            icon = Icons.Default.FiberManualRecord,
                            label = "Registra",
                            isActive = false,
                            activeColor = CallRed,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(CallRed)
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sipManager.hangupCall()
                },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Chiudi chiamata",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Chiudi",
                        color = CallRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Chiamata terminata",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp
                    )
                    if (duration > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Durata: ${formatDurationTimer(duration)}",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        AnimatedVisibility(
            visible = showDtmfPad,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            DtmfOverlay(
                sipManager = sipManager,
                onClose = { showDtmfPad = false }
            )
        }

        AnimatedVisibility(
            visible = showTransferDialog,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TransferOverlay(
                transferNumber = transferNumber,
                onNumberChange = { transferNumber = it },
                onTransfer = {
                    if (transferNumber.isNotBlank()) {
                        sipManager.transferCall(transferNumber)
                        showTransferDialog = false
                    }
                },
                onClose = { showTransferDialog = false }
            )
        }
    }
}

@Composable
private fun InCallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = PrimaryBlue,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.25f) else DarkSurfaceVariant
                )
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun DtmfOverlay(
    sipManager: SipManager,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xF0111820),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tastiera DTMF",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Chiudi",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val dtmfKeys = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf('*', '0', '#')
            )

            dtmfKeys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { digit ->
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    sipManager.sendDtmf(digit)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit.toString(),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TransferOverlay(
    transferNumber: String,
    onNumberChange: (String) -> Unit,
    onTransfer: () -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xF0111820),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trasferisci a",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Chiudi",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = transferNumber.ifEmpty { "Digita il numero" },
                color = if (transferNumber.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val transferKeys = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf('*', '0', '#')
            )

            transferKeys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { digit ->
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNumberChange(transferNumber + digit)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit.toString(),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (transferNumber.isNotBlank()) CallGreen else CallGreen.copy(alpha = 0.3f))
                    .clickable(enabled = transferNumber.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTransfer()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneForwarded,
                    contentDescription = "Trasferisci",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Trasferisci",
                color = CallGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatDurationTimer(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
