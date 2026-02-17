package it.edgvoip.jarvis.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.repository.AuthRepository
import it.edgvoip.jarvis.data.repository.ForceLoginRequiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val tenantSlug: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val showForceLogin: Boolean = false,
    val showBiometricSetup: Boolean = false,
    val canLoginWithBiometric: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                isLoggedIn = authRepository.isLoggedIn(),
                canLoginWithBiometric = tokenManager.isBiometricEnabled() && tokenManager.getBiometricCredentials() != null
            )
        }
    }

    fun getBiometricCredentialsForLogin(): Triple<String, String, String>? = tokenManager.getBiometricCredentials()

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun updateTenantSlug(slug: String) {
        _uiState.update { it.copy(tenantSlug = slug, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank() || state.tenantSlug.isBlank()) {
            _uiState.update { it.copy(error = "Compila tutti i campi") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, showForceLogin = false) }

            val result = authRepository.login(
                email = state.email.trim(),
                password = state.password,
                tenantSlug = state.tenantSlug.trim(),
                forceLogin = false
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            showBiometricSetup = true
                        )
                    }
                },
                onFailure = { error ->
                    if (error is ForceLoginRequiredException) {
                        _uiState.update {
                            it.copy(isLoading = false, showForceLogin = true)
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, error = error.message)
                        }
                    }
                }
            )
        }
    }

    fun forceLogin() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, showForceLogin = false) }

            val result = authRepository.login(
                email = state.email.trim(),
                password = state.password,
                tenantSlug = state.tenantSlug.trim(),
                forceLogin = true
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            showBiometricSetup = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
            )
        }
    }

    fun dismissForceLogin() {
        _uiState.update { it.copy(showForceLogin = false) }
    }

    /** Chiamato quando l'utente attiva l'impronta dopo il login: salva credenziali per accesso futuro. */
    fun onBiometricActivated() {
        val state = _uiState.value
        tokenManager.setBiometricEnabled(true)
        tokenManager.saveBiometricCredentials(
            email = state.email.trim(),
            password = state.password,
            tenantSlug = state.tenantSlug.trim()
        )
        _uiState.update { it.copy(showBiometricSetup = false) }
    }

    fun enableBiometric() {
        _uiState.update { it.copy(showBiometricSetup = false) }
    }

    fun skipBiometric() {
        _uiState.update { it.copy(showBiometricSetup = false) }
    }

    /** Login con le credenziali salvate (dopo successo impronta). */
    fun loginWithBiometric(email: String, password: String, tenantSlug: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.login(
                email = email,
                password = password,
                tenantSlug = tenantSlug,
                forceLogin = false
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isLoading = false, isLoggedIn = true, showBiometricSetup = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Accesso fallito")
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
