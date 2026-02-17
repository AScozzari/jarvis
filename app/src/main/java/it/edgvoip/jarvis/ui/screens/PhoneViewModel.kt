package it.edgvoip.jarvis.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.edgvoip.jarvis.data.db.CallLogEntity
import it.edgvoip.jarvis.data.db.ContactEntity
import it.edgvoip.jarvis.data.repository.PhoneRepository
import it.edgvoip.jarvis.sip.CallState
import it.edgvoip.jarvis.sip.RegistrationState
import it.edgvoip.jarvis.sip.SipManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhoneViewModel @Inject constructor(
    private val phoneRepository: PhoneRepository,
    private val sipManager: SipManager
) : ViewModel() {

    private val _dialNumber = MutableStateFlow("")
    val dialNumber: StateFlow<String> = _dialNumber.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _matchedContactName = MutableStateFlow<String?>(null)
    val matchedContactName: StateFlow<String?> = _matchedContactName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Emesso quando la chiamata è partita: la UI può navigare a InCall. */
    private val _navigateToCall = MutableSharedFlow<Unit>()
    val navigateToCall: SharedFlow<Unit> = _navigateToCall.asSharedFlow()

    private val _isDialing = MutableStateFlow(false)
    val isDialing: StateFlow<Boolean> = _isDialing.asStateFlow()

    private val _callError = MutableStateFlow<String?>(null)
    val callError: StateFlow<String?> = _callError.asStateFlow()

    val registrationState: StateFlow<RegistrationState> = sipManager.registrationState

    val callHistory: StateFlow<List<CallLogEntity>> = phoneRepository
        .getRecentCalls(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ContactEntity>> = _searchQuery
        .flatMapLatest { query ->
            phoneRepository.searchContacts(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadCallHistory()
        loadContacts()
        // All'ingresso nella parte telefono: registrazione SIP automatica con credenziali dell'utente loggato (tenant)
        viewModelScope.launch {
            if (sipManager.registrationState.first() != RegistrationState.REGISTERED) {
                phoneRepository.initializeSip()
            }
        }
    }

    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
    }

    fun appendDigit(digit: String) {
        _dialNumber.value = _dialNumber.value + digit
        lookupContact(_dialNumber.value)
    }

    fun deleteDigit() {
        val current = _dialNumber.value
        if (current.isNotEmpty()) {
            _dialNumber.value = current.dropLast(1)
            if (_dialNumber.value.isEmpty()) {
                _matchedContactName.value = null
            } else {
                lookupContact(_dialNumber.value)
            }
        }
    }

    fun clearNumber() {
        _dialNumber.value = ""
        _matchedContactName.value = null
    }

    fun setDialNumber(number: String) {
        _dialNumber.value = number
        lookupContact(number)
    }

    fun dial(number: String) {
        val target = number.ifEmpty { _dialNumber.value }.trim()
        if (target.isBlank()) return
        _callError.value = null
        viewModelScope.launch {
            _isDialing.value = true
            try {
                if (sipManager.registrationState.value != RegistrationState.REGISTERED) {
                    val initResult = phoneRepository.initializeSip()
                    initResult.onFailure { error ->
                        _callError.value = error.message ?: "Registrazione SIP non riuscita"
                        _isDialing.value = false
                        return@launch
                    }
                    var waited = 0
                    while (sipManager.registrationState.value != RegistrationState.REGISTERED && waited < 3000) {
                        delay(200)
                        waited += 200
                    }
                }
                if (sipManager.registrationState.value == RegistrationState.REGISTERED) {
                    sipManager.makeCall(target)
                    delay(200)
                    if (sipManager.callState.value != CallState.IDLE) {
                        _navigateToCall.emit(Unit)
                    } else {
                        _callError.value = "Impossibile avviare la chiamata"
                    }
                } else {
                    _callError.value = "Registrazione SIP non riuscita. Riprova."
                }
            } finally {
                _isDialing.value = false
            }
        }
    }

    fun clearCallError() {
        _callError.value = null
    }

    fun searchContacts(query: String) {
        _searchQuery.value = query
    }

    fun loadCallHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                phoneRepository.syncCallHistory()
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            try {
                phoneRepository.syncContacts()
            } catch (_: Exception) {
            }
        }
    }

    private fun lookupContact(number: String) {
        if (number.length < 3) {
            _matchedContactName.value = null
            return
        }
        viewModelScope.launch {
            val contact = phoneRepository.findContactByNumber(number)
            _matchedContactName.value = contact?.name
        }
    }
}
