package it.edgvoip.jarvis

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.edgvoip.jarvis.data.api.TokenManager
import it.edgvoip.jarvis.data.repository.AuthRepository
import it.edgvoip.jarvis.data.repository.NotificationRepository
import it.edgvoip.jarvis.data.repository.PhoneRepository
import it.edgvoip.jarvis.sip.WebRtcPhoneManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val phoneRepository: PhoneRepository,
    private val notificationRepository: NotificationRepository,
    private val sipManager: WebRtcPhoneManager,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _isAuthenticated = MutableStateFlow(authRepository.isLoggedIn())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _sessionExpiredMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sessionExpiredMessage: SharedFlow<String> = _sessionExpiredMessage.asSharedFlow()

    val unreadNotificationCount: StateFlow<Int> = notificationRepository
        .getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        if (authRepository.isLoggedIn()) {
            viewModelScope.launch { phoneRepository.initializeSip() }
            viewModelScope.launch {
                try { notificationRepository.syncNotifications() } catch (_: Exception) { }
            }
        }

        viewModelScope.launch {
            tokenManager.sessionExpired.collect { reason ->
                Log.w(TAG, "Session expired event received: $reason")
                performAutoLogout(reason)
            }
        }
    }

    private fun performAutoLogout(reason: String) {
        if (!_isAuthenticated.value) return
        viewModelScope.launch {
            Log.i(TAG, "Auto-logout: shutting down SIP and clearing session")
            try { sipManager.shutdown() } catch (_: Exception) { }
            _isAuthenticated.value = false
            _sessionExpiredMessage.tryEmit(reason)
        }
    }

    fun setLoggedIn() {
        _isAuthenticated.value = true
        viewModelScope.launch { phoneRepository.initializeSip() }
        viewModelScope.launch {
            try { notificationRepository.syncNotifications() } catch (_: Exception) { }
        }
    }

    fun setLoggedOut() {
        _isAuthenticated.value = false
    }

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
