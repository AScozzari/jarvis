package it.edgvoip.jarvis.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.edgvoip.jarvis.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(uiState.isLoggedIn, uiState.showBiometricSetup) {
        if (uiState.isLoggedIn && !uiState.showBiometricSetup) {
            onLoginSuccess()
        }
    }

    if (uiState.showForceLogin) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissForceLogin() },
            containerColor = DarkSurface,
            titleContentColor = DarkOnSurface,
            textContentColor = DarkOnSurfaceVariant,
            title = {
                Text(
                    text = "Sessione attiva",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Sessione attiva su un altro dispositivo. Vuoi forzare il login?")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.forceLogin() },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryBlue)
                ) {
                    Text("Sì, accedi")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissForceLogin() },
                    colors = ButtonDefaults.textButtonColors(contentColor = DarkOnSurfaceVariant)
                ) {
                    Text("Annulla")
                }
            }
        )
    }

    if (uiState.showBiometricSetup && activity != null && BiometricHelper.canAuthenticate(context)) {
        AlertDialog(
            onDismissRequest = { viewModel.skipBiometric() },
            containerColor = DarkSurface,
            titleContentColor = DarkOnSurface,
            textContentColor = DarkOnSurfaceVariant,
            title = {
                Text(
                    text = "Attiva accesso con impronta",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Vuoi attivare l'impronta digitale (o il riconoscimento facciale) per accedere all'app al prossimo avvio? Potrai usarla al posto di email e password."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        BiometricHelper.showBiometricPrompt(
                            activity = activity,
                            title = "Verifica identità",
                            subtitle = "Usa l'impronta per attivare l'accesso biometrico al login",
                            onSuccess = {
                                viewModel.onBiometricActivated()
                            },
                            onError = {
                                viewModel.skipBiometric()
                            }
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryBlue)
                ) {
                    Text("Attiva impronta")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.skipBiometric() },
                    colors = ButtonDefaults.textButtonColors(contentColor = DarkOnSurfaceVariant)
                ) {
                    Text("Salta")
                }
            }
        )
    } else if (uiState.showBiometricSetup) {
        LaunchedEffect(Unit) {
            viewModel.skipBiometric()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CallRed,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0E14),
                            Color(0xFF0D1117),
                            Color(0xFF101820),
                            Color(0xFF0A1628)
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(PrimaryBlue, TertiaryPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "J",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "JARVIS",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 6.sp
                )

                Text(
                    text = "EDG VoIP Platform",
                    fontSize = 14.sp,
                    color = DarkOnSurfaceVariant,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(48.dp))

                if (uiState.canLoginWithBiometric && activity != null && BiometricHelper.canAuthenticate(context)) {
                    Button(
                        onClick = {
                            BiometricHelper.showBiometricPrompt(
                                activity = activity!!,
                                title = "Accedi con impronta",
                                subtitle = "Usa l'impronta digitale per accedere",
                                onSuccess = {
                                    val creds = viewModel.getBiometricCredentialsForLogin()
                                    if (creds != null) {
                                        viewModel.loginWithBiometric(creds.first, creds.second, creds.third)
                                    }
                                },
                                onError = { msg -> if (msg.isNotBlank()) viewModel.clearError() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Accedi con impronta")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "oppure",
                        fontSize = 14.sp,
                        color = DarkOnSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text(
                    text = "Accedi al tuo account",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkOnSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = uiState.tenantSlug,
                    onValueChange = { viewModel.updateTenantSlug(it) },
                    label = { Text("Tenant") },
                    prefix = {
                        Text(
                            text = "edgvoip.it/",
                            color = DarkOnSurfaceVariant,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = PrimaryBlue
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = loginTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = { viewModel.updateEmail(it) },
                    label = { Text("Email") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = PrimaryBlue
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = loginTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.updatePassword(it) },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PrimaryBlue
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Nascondi password" else "Mostra password",
                                tint = DarkOnSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.login()
                        }
                    ),
                    colors = loginTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.login()
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        disabledContainerColor = PrimaryBlue.copy(alpha = 0.5f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    AnimatedVisibility(
                        visible = uiState.isLoading,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }

                    AnimatedVisibility(
                        visible = !uiState.isLoading,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "Accedi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "© EDG VoIP",
                    fontSize = 12.sp,
                    color = DarkOnSurfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun loginTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = DarkOnSurface,
        unfocusedTextColor = DarkOnSurface,
        cursorColor = PrimaryBlue,
        focusedBorderColor = PrimaryBlue,
        unfocusedBorderColor = DarkOutline,
        focusedLabelColor = PrimaryBlue,
        unfocusedLabelColor = DarkOnSurfaceVariant,
        focusedLeadingIconColor = PrimaryBlue,
        unfocusedLeadingIconColor = DarkOnSurfaceVariant,
        focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
        unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
        focusedPrefixColor = DarkOnSurfaceVariant,
        unfocusedPrefixColor = DarkOnSurfaceVariant
    )
}
