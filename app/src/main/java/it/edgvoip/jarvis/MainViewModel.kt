package it.edgvoip.jarvis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.edgvoip.jarvis.data.repository.AuthRepository
import it.edgvoip.jarvis.data.repository.PhoneRepository
import it.edgvoip.jarvis.sip.SipManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val phoneRepository: PhoneRepository,
    private val sipManager: SipManager
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(authRepository.isLoggedIn())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        if (authRepository.isLoggedIn()) {
            viewModelScope.launch { phoneRepository.initializeSip() }
        }
    }

    fun setLoggedIn() {
        _isAuthenticated.value = true
        viewModelScope.launch { phoneRepository.initializeSip() }
    }

    fun setLoggedOut() {
        _isAuthenticated.value = false
    }

    /** Logout reale: chiude SIP, invalida token e chiama API logout, poi esegue il callback (es. navigazione al login). */
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                sipManager.shutdown()
            } catch (_: Exception) { }
            try {
                authRepository.logout()
            } catch (_: Exception) { }
            _isAuthenticated.value = false
            onLoggedOut()
        }
    }
}
