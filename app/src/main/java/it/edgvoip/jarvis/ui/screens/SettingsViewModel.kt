package it.edgvoip.jarvis.ui.screens

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.model.User
import it.edgvoip.jarvis.data.repository.AuthRepository
import it.edgvoip.jarvis.sip.RegistrationState
import it.edgvoip.jarvis.sip.SipManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val sipManager: SipManager
) : ViewModel() {

    companion object {
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_PREFERRED_AGENT = stringPreferencesKey("preferred_agent")
        val KEY_SIP_CODEC = stringPreferencesKey("sip_codec")
        val KEY_ECHO_CANCELLATION = booleanPreferencesKey("echo_cancellation")
        val KEY_VOLUME_BOOST = booleanPreferencesKey("volume_boost")
        val KEY_THEME = stringPreferencesKey("app_theme")
    }

    private val dataStore = context.settingsDataStore

    val biometricEnabled: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_BIOMETRIC_ENABLED] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val preferredAgent: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_PREFERRED_AGENT] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val sipCodec: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_SIP_CODEC] ?: "PCMU" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "PCMU")

    val echoCancellation: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_ECHO_CANCELLATION] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val volumeBoost: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_VOLUME_BOOST] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appTheme: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_THEME] ?: "system" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val sipRegistrationState: StateFlow<RegistrationState> = sipManager.registrationState

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    init {
        _currentUser.value = tokenManager.getUserData()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_BIOMETRIC_ENABLED] = enabled
            }
            tokenManager.setBiometricEnabled(enabled)
            if (!enabled) tokenManager.clearBiometricCredentials()
        }
    }

    fun setPreferredAgent(agentId: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_PREFERRED_AGENT] = agentId
            }
        }
    }

    fun setSipCodec(codec: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_SIP_CODEC] = codec
            }
        }
    }

    fun setEchoCancellation(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_ECHO_CANCELLATION] = enabled
            }
        }
    }

    fun setVolumeBoost(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_VOLUME_BOOST] = enabled
            }
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_THEME] = theme
            }
        }
    }

    fun showLogoutConfirmation() {
        _showLogoutDialog.value = true
    }

    fun dismissLogoutDialog() {
        _showLogoutDialog.value = false
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            _isLoggingOut.value = true
            try {
                sipManager.shutdown()
                authRepository.logout()
                onLoggedOut()
            } catch (_: Exception) {
                authRepository.logout()
                onLoggedOut()
            } finally {
                _isLoggingOut.value = false
                _showLogoutDialog.value = false
            }
        }
    }
}
