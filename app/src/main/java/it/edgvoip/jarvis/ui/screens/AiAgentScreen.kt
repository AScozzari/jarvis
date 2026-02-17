package it.edgvoip.jarvis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import it.edgvoip.jarvis.ai.ElevenLabsWebRtcManager
import it.edgvoip.jarvis.data.db.ConversationEntity
import it.edgvoip.jarvis.data.db.MessageEntity
import it.edgvoip.jarvis.data.model.ChatbotAgent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AiAgentScreen(
    viewModel: AiAgentViewModel = hiltViewModel()
) {
    val isVoiceMode by viewModel.isVoiceMode.collectAsState()
    val showRenameDialog by viewModel.showRenameDialog.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showAgentPicker by viewModel.showAgentPicker.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkPreferredAgentChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    BackHandler(enabled = viewModel.inChatView.collectAsState().value || isVoiceMode) {
        if (isVoiceMode) {
            viewModel.toggleVoiceMode()
        } else {
            viewModel.goBack()
        }
    }

    val inChatView by viewModel.inChatView.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (inChatView) {
            ChatView(viewModel = viewModel, snackbarHostState = snackbarHostState)
        } else {
            ConversationListView(viewModel = viewModel, snackbarHostState = snackbarHostState)
        }

        AnimatedVisibility(
            visible = isVoiceMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            VoiceModeOverlay(viewModel = viewModel)
        }
    }

    if (showRenameDialog) {
        RenameDialog(viewModel = viewModel)
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(viewModel = viewModel)
    }

    if (showAgentPicker) {
        AgentPickerDialog(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListView(
    viewModel: AiAgentViewModel,
    snackbarHostState: SnackbarHostState
) {
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI Agent",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchConversations(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Cerca conversazioni...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchConversations("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val agents by viewModel.availableAgents.collectAsState()
                if (agents.isEmpty()) {
                    EmptyAgentsState(onRetry = { viewModel.refreshAgents() })
                } else if (conversations.isEmpty()) {
                    EmptyConversationsState()
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = conversations,
                        key = { it.id }
                    ) { conversation ->
                        SwipeableConversationCard(
                            conversation = conversation,
                            onClick = { viewModel.selectConversation(conversation.id) },
                            onRename = { viewModel.showRenameDialog(conversation.id, conversation.title) },
                            onDelete = { viewModel.showDeleteDialog(conversation.id) }
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun EmptyAgentsState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Nessun agente AI configurato",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Non risultano agenti AI configurati per il tuo account. Verifica con l'amministratore o riprova.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onRetry) {
            Text("Riprova")
        }
    }
}

@Composable
private fun EmptyConversationsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Nessuna conversazione",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Inizia una nuova chat!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableConversationCard(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else {
                false
            }
        }
    )
    var showContextMenu by remember { mutableStateOf(false) }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFD32F2F)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Elimina",
                    modifier = Modifier.padding(end = 24.dp),
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatRelativeTime(conversation.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = conversation.agentName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }

                    if (conversation.lastMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = conversation.lastMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rinomina") },
                        onClick = {
                            showContextMenu = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Elimina", color = Color(0xFFD32F2F)) },
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F)
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatView(
    viewModel: AiAgentViewModel,
    snackbarHostState: SnackbarHostState
) {
    val currentConversation by viewModel.currentConversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val inputMessage by viewModel.inputMessage.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val selectedAgent by viewModel.selectedAgent.collectAsState()
    val listState = rememberLazyListState()
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                title = {
                    Column(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                currentConversation?.let {
                                    viewModel.showRenameDialog(it.id, it.title)
                                }
                            }
                        )
                    ) {
                        Text(
                            text = currentConversation?.title ?: "Nuova Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        selectedAgent?.let { agent ->
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rinomina") },
                                onClick = {
                                    showOverflowMenu = false
                                    currentConversation?.let {
                                        viewModel.showRenameDialog(it.id, it.title)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Elimina", color = Color(0xFFD32F2F)) },
                                onClick = {
                                    showOverflowMenu = false
                                    currentConversation?.let {
                                        viewModel.showDeleteDialog(it.id)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cancella cronologia") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.clearConversationHistory()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (messages.isEmpty() && !isSending) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Scrivi un messaggio per iniziare",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            selectedAgent?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Stai parlando con ${it.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(message = message)
                }

                if (isSending) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            ChatInputBar(
                message = inputMessage,
                onMessageChange = { viewModel.updateInput(it) },
                onSend = { viewModel.sendMessage() },
                onMicTap = { viewModel.toggleVoiceMode() },
                isSending = isSending,
                showVoiceButton = selectedAgent?.supportsVoice == true,
                showSttButton = selectedAgent?.isChatbot == true
            )
        }
    }
}

@Composable
private fun ChatBubble(message: MessageEntity) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatMessageTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
    isSending: Boolean,
    showVoiceButton: Boolean = false,
    showSttButton: Boolean = false
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isRecording = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                val combined = if (message.isBlank()) spokenText else "$message $spokenText"
                onMessageChange(combined)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showVoiceButton) {
                IconButton(
                    onClick = onMicTap,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Modalità voce",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showSttButton) {
                IconButton(
                    onClick = {
                        isRecording = true
                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Parla ora...")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            isRecording = false
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Dettatura vocale",
                        tint = if (isRecording) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Scrivi un messaggio...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            IconButton(
                onClick = onSend,
                enabled = message.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (message.isNotBlank() && !isSending)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Invia",
                        tint = if (message.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceModeOverlay(viewModel: AiAgentViewModel) {
    val selectedAgent by viewModel.selectedAgent.collectAsState()
    val context = LocalContext.current
    val manager = remember { ElevenLabsWebRtcManager() }
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")

    val elevenLabsAgentId = selectedAgent?.elevenLabsAgentId
    val agentStatus by manager.status.collectAsState()
    val isConnected by manager.isConnected.collectAsState()
    val isSpeaking by manager.isSpeaking.collectAsState()
    val isListening by manager.isListening.collectAsState()
    val vadScore by manager.vadScore.collectAsState()
    val lastAgentMessage by manager.lastAgentMessage.collectAsState()
    val lastUserMessage by manager.lastUserMessage.collectAsState()
    val agentMuted by manager.isMuted.collectAsState()
    val errorMessage by manager.errorMessage.collectAsState()

    val accentPurple = Color(0xFF7C4DFF)
    val accentBlue = Color(0xFF448AFF)
    val bgDark = Color(0xFF0D0D1A)
    val cardBg = Color(0xFF1A1A2E)
    val errorRed = Color(0xFFEF5350)
    val endCallRed = Color(0xFFD32F2F)

    val outerPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f + (if (isSpeaking) 0.2f else vadScore * 0.15f),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 600 else 1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_pulse"
    )

    val innerPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f + (if (isSpeaking) 0.1f else vadScore * 0.1f),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 500 else 1200, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inner_pulse"
    )

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    var callStarted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            manager.disconnect()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = selectedAgent?.name ?: "AI Agent",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            val statusText = when {
                elevenLabsAgentId == null -> "Questo agente supporta solo chat testuale"
                !callStarted -> "Premi per iniziare la conversazione"
                agentStatus == it.edgvoip.jarvis.ai.AgentStatus.ERROR -> errorMessage ?: "Errore di connessione"
                agentStatus == it.edgvoip.jarvis.ai.AgentStatus.CONNECTING -> "Connessione in corso..."
                isSpeaking -> "Sta parlando..."
                isListening && !agentMuted -> "In ascolto..."
                agentMuted -> "Microfono disattivato"
                isConnected -> "Connesso"
                else -> "Disconnesso"
            }

            val statusColor = when {
                agentStatus == it.edgvoip.jarvis.ai.AgentStatus.ERROR -> errorRed
                isSpeaking -> accentPurple
                isListening && !agentMuted -> Color(0xFF4CAF50)
                agentMuted -> Color(0xFFFF9800)
                isConnected -> Color(0xFF4CAF50)
                else -> Color.White.copy(alpha = 0.5f)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isConnected && agentStatus != it.edgvoip.jarvis.ai.AgentStatus.ERROR) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            if (agentStatus == it.edgvoip.jarvis.ai.AgentStatus.ERROR) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(errorRed.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = errorRed
                        )
                    }

                    Text(
                        text = errorMessage ?: "Errore sconosciuto",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Surface(
                        onClick = {
                            callStarted = true
                            manager.retry()
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = accentPurple
                    ) {
                        Text(
                            text = "Riprova",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
                        )
                    }
                }
            } else if (elevenLabsAgentId == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MicOff,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    Text(
                        text = "Questo agente non supporta la conversazione vocale.\nPuoi usare la chat testuale.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else if (!callStarted) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(accentPurple.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(accentPurple.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            callStarted = true
                            if (elevenLabsAgentId != null) {
                                manager.startConversation(context, elevenLabsAgentId)
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        color = accentPurple,
                        modifier = Modifier.widthIn(min = 200.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Chiama agente",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Text(
                        text = "Conversazione vocale in tempo reale",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .scale(wave1)
                                .background(
                                    (if (isSpeaking) accentPurple else accentBlue)
                                        .copy(alpha = (1f - wave1) * 0.3f),
                                    CircleShape
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .scale(wave2)
                                .background(
                                    (if (isSpeaking) accentPurple else accentBlue)
                                        .copy(alpha = (1f - wave2) * 0.2f),
                                    CircleShape
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .scale(if (isConnected) outerPulse else 1f)
                            .background(
                                when {
                                    isSpeaking -> accentPurple.copy(alpha = 0.2f)
                                    isConnected -> accentBlue.copy(alpha = 0.15f)
                                    else -> Color.White.copy(alpha = 0.05f)
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(if (isConnected) innerPulse else 1f)
                                .background(
                                    when {
                                        isSpeaking -> accentPurple.copy(alpha = 0.45f)
                                        isConnected -> accentBlue.copy(alpha = 0.35f)
                                        else -> Color.White.copy(alpha = 0.1f)
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                agentStatus == it.edgvoip.jarvis.ai.AgentStatus.CONNECTING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(44.dp),
                                        strokeWidth = 3.dp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                isSpeaking -> {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = Color.White
                                    )
                                }
                                else -> {
                                    Icon(
                                        if (agentMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = if (isConnected) Color.White else Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isConnected) {
                val displayMessage = if (isSpeaking) lastAgentMessage else lastUserMessage
                if (displayMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = if (isSpeaking) "Agente" else "Tu",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSpeaking) accentPurple else accentBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            if (isConnected || agentStatus == it.edgvoip.jarvis.ai.AgentStatus.ERROR || elevenLabsAgentId == null || !callStarted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnected) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { manager.toggleMute() },
                                shape = CircleShape,
                                color = if (agentMuted) Color(0xFFFF9800) else Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        if (agentMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (agentMuted) "Attiva mic" else "Muta mic",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (agentMuted) "Muto" else "Mic",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = {
                                manager.disconnect()
                                viewModel.toggleVoiceMode()
                            },
                            shape = CircleShape,
                            color = endCallRed,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Chiudi conversazione",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "Chiudi",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    if (isConnected) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { /* placeholder for speaker toggle */ },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = "Agente",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Agente",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun RenameDialog(viewModel: AiAgentViewModel) {
    val renameText by viewModel.renameText.collectAsState()

    AlertDialog(
        onDismissRequest = { viewModel.dismissRenameDialog() },
        title = { Text("Rinomina conversazione") },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = { viewModel.updateRenameText(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Titolo") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.renameConversation() },
                enabled = renameText.isNotBlank()
            ) {
                Text("Rinomina")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissRenameDialog() }) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(viewModel: AiAgentViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDeleteDialog() },
        title = { Text("Elimina conversazione") },
        text = {
            Text("Eliminare questa conversazione? L'azione non può essere annullata.")
        },
        confirmButton = {
            TextButton(onClick = { viewModel.deleteConversation() }) {
                Text("Elimina", color = Color(0xFFD32F2F))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun AgentPickerDialog(viewModel: AiAgentViewModel) {
    val agents by viewModel.availableAgents.collectAsState()

    AlertDialog(
        onDismissRequest = { viewModel.dismissAgentPicker() },
        title = { Text("Seleziona un agente") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(agents) { agent ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.selectAgent(agent) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = agent.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (agent.agentType != null) {
                                        Text(
                                            text = if (agent.isElevenLabs) "Voce" else "Chatbot",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (agent.description.isNotEmpty()) {
                                    Text(
                                        text = agent.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.dismissAgentPicker() }) {
                Text("Annulla")
            }
        }
    )
}

private fun formatRelativeTime(date: Date): String {
    val now = System.currentTimeMillis()
    val diff = now - date.time
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    val todayCal = Calendar.getInstance()
    val dateCal = Calendar.getInstance().apply { time = date }

    return when {
        minutes < 1 -> "Adesso"
        minutes < 60 -> "${minutes} min fa"
        hours < 24 && todayCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR) &&
                todayCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.ITALIAN).format(date)
        }
        days < 2 -> "Ieri"
        days < 7 -> {
            SimpleDateFormat("EEEE", Locale.ITALIAN).format(date)
                .replaceFirstChar { it.uppercase() }
        }
        todayCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) -> {
            SimpleDateFormat("dd MMM", Locale.ITALIAN).format(date)
        }
        else -> {
            SimpleDateFormat("dd MMM yyyy", Locale.ITALIAN).format(date)
        }
    }
}

private fun formatMessageTimestamp(date: Date): String {
    val todayCal = Calendar.getInstance()
    val dateCal = Calendar.getInstance().apply { time = date }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALIAN)

    return if (todayCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR) &&
        todayCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR)
    ) {
        timeFormat.format(date)
    } else {
        val dateFormat = SimpleDateFormat("HH:mm - dd MMM", Locale.ITALIAN)
        dateFormat.format(date)
    }
}
