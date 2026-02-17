package it.edgvoip.jarvis.ui.screens

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.edgvoip.jarvis.data.db.ConversationEntity
import it.edgvoip.jarvis.data.db.MessageEntity
import it.edgvoip.jarvis.data.model.ChatMessage
import it.edgvoip.jarvis.data.model.ChatbotAgent
import it.edgvoip.jarvis.data.repository.AiAgentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiAgentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AiAgentRepository
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    private val _currentConversation = MutableStateFlow<ConversationEntity?>(null)
    val currentConversation: StateFlow<ConversationEntity?> = _currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _availableAgents = MutableStateFlow<List<ChatbotAgent>>(emptyList())
    val availableAgents: StateFlow<List<ChatbotAgent>> = _availableAgents.asStateFlow()

    private val _selectedAgent = MutableStateFlow<ChatbotAgent?>(null)
    val selectedAgent: StateFlow<ChatbotAgent?> = _selectedAgent.asStateFlow()

    private val _inputMessage = MutableStateFlow("")
    val inputMessage: StateFlow<String> = _inputMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()

    private val _renameText = MutableStateFlow("")
    val renameText: StateFlow<String> = _renameText.asStateFlow()

    private val _renameConversationId = MutableStateFlow("")
    val renameConversationId: StateFlow<String> = _renameConversationId.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _deleteConversationId = MutableStateFlow("")
    val deleteConversationId: StateFlow<String> = _deleteConversationId.asStateFlow()

    private val _isVoiceMode = MutableStateFlow(false)
    val isVoiceMode: StateFlow<Boolean> = _isVoiceMode.asStateFlow()

    private val _inChatView = MutableStateFlow(false)
    val inChatView: StateFlow<Boolean> = _inChatView.asStateFlow()

    private val _showAgentPicker = MutableStateFlow(false)
    val showAgentPicker: StateFlow<Boolean> = _showAgentPicker.asStateFlow()

    private val _tenantSlug = MutableStateFlow("")
    val tenantSlug: StateFlow<String> = _tenantSlug.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var messagesJob: Job? = null
    private var conversationsJob: Job? = null

    init {
        _tenantSlug.value = repository.getTenantSlug() ?: ""
        loadConversations()
        loadAgents()
    }

    fun loadConversations() {
        conversationsJob?.cancel()
        conversationsJob = viewModelScope.launch {
            repository.getConversations().collect { list ->
                _conversations.value = list
            }
        }
    }

    private fun loadAgents() {
        viewModelScope.launch {
            val isFirstLoad = _availableAgents.value.isEmpty()
            if (isFirstLoad) _isLoading.value = true
            _errorMessage.value = null
            repository.getAvailableAgents().onSuccess { agents ->
                val filtered = agents.filter { it.isActive && it.isAssistantOrElevenLabs }
                _availableAgents.value = filtered
                val preferredId = try {
                    context.settingsDataStore.data.map { prefs ->
                        prefs[stringPreferencesKey("preferred_agent")] ?: ""
                    }.first()
                } catch (_: Exception) { "" }
                _selectedAgent.value = if (preferredId.isNotBlank()) {
                    filtered.find { it.id == preferredId } ?: filtered.firstOrNull()
                } else {
                    filtered.firstOrNull()
                }
                if (_selectedAgent.value != null) {
                    _inChatView.value = true
                }
            }.onFailure {
                _errorMessage.value = it.message ?: "Errore nel caricamento degli agenti"
            }
            _isLoading.value = false
        }
    }

    /** Ricarica gli agenti AI (es. dopo errore o lista vuota). */
    fun refreshAgents() {
        loadAgents()
    }

    fun selectConversation(id: String) {
        viewModelScope.launch {
            val conversation = repository.getConversationById(id) ?: return@launch
            _currentConversation.value = conversation
            _inChatView.value = true
            val agent = _availableAgents.value.find { it.id == conversation.agentId }
            if (agent != null) {
                _selectedAgent.value = agent
            }
            messagesJob?.cancel()
            messagesJob = viewModelScope.launch {
                repository.getMessages(id).collect { msgs ->
                    _messages.value = msgs
                }
            }
        }
    }

    fun startNewConversation(agentId: String? = null) {
        val agent = if (agentId != null) {
            _availableAgents.value.find { it.id == agentId }
        } else {
            _selectedAgent.value
        }

        if (agent == null) {
            if (_availableAgents.value.isEmpty()) {
                _errorMessage.value = "Nessun agente disponibile"
            } else {
                _showAgentPicker.value = true
            }
            return
        }

        _selectedAgent.value = agent
        _currentConversation.value = null
        _messages.value = emptyList()
        _inputMessage.value = ""
        _inChatView.value = true
        messagesJob?.cancel()
    }

    fun sendMessage() {
        val text = _inputMessage.value.trim()
        if (text.isEmpty()) return

        val agent = _selectedAgent.value ?: return

        viewModelScope.launch {
            _isSending.value = true
            _inputMessage.value = ""

            try {
                val conversation = _currentConversation.value ?: run {
                    val newConv = repository.createConversation(
                        agentId = agent.id,
                        agentName = agent.name,
                        firstMessage = text
                    )
                    _currentConversation.value = newConv
                    messagesJob?.cancel()
                    messagesJob = viewModelScope.launch {
                        repository.getMessages(newConv.id).collect { msgs ->
                            _messages.value = msgs
                        }
                    }
                    newConv
                }

                repository.addMessage(conversation.id, "user", text)

                val history = _messages.value.map { msg ->
                    ChatMessage(role = msg.role, content = msg.content)
                }

                val result = repository.sendMessage(
                    agentId = agent.id,
                    message = text,
                    sessionId = conversation.sessionId,
                    history = history
                )

                result.onSuccess { response ->
                    repository.addMessage(conversation.id, "assistant", response.message)
                    val updated = repository.getConversationById(conversation.id)
                    if (updated != null) {
                        _currentConversation.value = updated
                    }
                }.onFailure { error ->
                    repository.addMessage(
                        conversation.id,
                        "assistant",
                        "⚠️ Errore: ${error.message ?: "Errore sconosciuto"}"
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isSending.value = false
            }
        }
    }

    fun updateInput(text: String) {
        _inputMessage.value = text
    }

    fun showRenameDialog(conversationId: String, currentTitle: String) {
        _renameConversationId.value = conversationId
        _renameText.value = currentTitle
        _showRenameDialog.value = true
    }

    fun updateRenameText(text: String) {
        _renameText.value = text
    }

    fun dismissRenameDialog() {
        _showRenameDialog.value = false
        _renameText.value = ""
        _renameConversationId.value = ""
    }

    fun renameConversation() {
        val id = _renameConversationId.value
        val newTitle = _renameText.value.trim()
        if (id.isEmpty() || newTitle.isEmpty()) return

        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
            if (_currentConversation.value?.id == id) {
                val updated = repository.getConversationById(id)
                _currentConversation.value = updated
            }
            dismissRenameDialog()
        }
    }

    fun showDeleteDialog(conversationId: String) {
        _deleteConversationId.value = conversationId
        _showDeleteDialog.value = true
    }

    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
        _deleteConversationId.value = ""
    }

    fun deleteConversation() {
        val id = _deleteConversationId.value
        if (id.isEmpty()) return

        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversation.value?.id == id) {
                goBack()
            }
            dismissDeleteDialog()
        }
    }

    fun clearConversationHistory() {
        val id = _currentConversation.value?.id ?: return
        viewModelScope.launch {
            repository.clearConversationHistory(id)
            val updated = repository.getConversationById(id)
            _currentConversation.value = updated
        }
    }

    fun searchConversations(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            loadConversations()
            return
        }
        viewModelScope.launch {
            val results = repository.searchConversations(query)
            _conversations.value = results
        }
    }

    fun toggleVoiceMode() {
        _isVoiceMode.value = !_isVoiceMode.value
    }

    fun selectAgent(agent: ChatbotAgent) {
        _selectedAgent.value = agent
        _showAgentPicker.value = false
        startNewConversation(agent.id)
    }

    fun dismissAgentPicker() {
        _showAgentPicker.value = false
    }

    fun goBack() {
        _currentConversation.value = null
        _messages.value = emptyList()
        _inputMessage.value = ""
        _isVoiceMode.value = false
        _inChatView.value = false
        messagesJob?.cancel()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
