package it.edgvoip.jarvis.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import it.edgvoip.jarvis.data.db.CallLogEntity
import it.edgvoip.jarvis.data.db.ContactEntity
import it.edgvoip.jarvis.ui.theme.CallGreen
import it.edgvoip.jarvis.ui.theme.CallRed
import it.edgvoip.jarvis.ui.theme.DarkBackground
import it.edgvoip.jarvis.ui.theme.DarkSurface
import it.edgvoip.jarvis.ui.theme.DarkSurfaceVariant
import it.edgvoip.jarvis.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneScreen(
    onNavigateToCall: () -> Unit = {},
    viewModel: PhoneViewModel = hiltViewModel()
) {
    val dialNumber by viewModel.dialNumber.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val callHistory by viewModel.callHistory.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val matchedContactName by viewModel.matchedContactName.collectAsState()
    val isDialing by viewModel.isDialing.collectAsState()
    val callError by viewModel.callError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.navigateToCall.collect {
            onNavigateToCall()
        }
    }
    LaunchedEffect(callError) {
        callError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCallError()
        }
    }

    val pagerState = rememberPagerState(initialPage = activeTab) { 3 }
    val coroutineScope = rememberCoroutineScope()

    val tabTitles = listOf("Tastiera", "Recenti", "Contatti")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = DarkSurface,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = 3.dp,
                    color = PrimaryBlue
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                val textColor by animateColorAsState(
                    targetValue = if (selected) PrimaryBlue else Color.White.copy(alpha = 0.6f),
                    label = "tabColor"
                )
                Tab(
                    selected = selected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                        viewModel.setActiveTab(index)
                    },
                    text = {
                        Text(
                            text = title,
                            color = textColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DialerTab(
                        dialNumber = dialNumber,
                        matchedContactName = matchedContactName,
                        onDigitPress = { viewModel.appendDigit(it) },
                        onDelete = { viewModel.deleteDigit() },
                        onClear = { viewModel.clearNumber() },
                        onCall = { viewModel.dial("") }
                    )
                    1 -> RecentiTab(
                        callHistory = callHistory,
                        onCallTap = { number ->
                            viewModel.setDialNumber(number)
                            viewModel.dial(number)
                        }
                    )
                    2 -> ContattiTab(
                        contacts = contacts,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.searchContacts(it) },
                        onContactTap = { contact ->
                            contact.phone?.let { phone ->
                                viewModel.setDialNumber(phone)
                                viewModel.dial(phone)
                            }
                        }
                    )
                }
            }
            if (isDialing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun DialerTab(
    dialNumber: String,
    matchedContactName: String?,
    onDigitPress: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        if (matchedContactName != null) {
            Text(
                text = matchedContactName,
                color = PrimaryBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = dialNumber.ifEmpty { " " },
                color = Color.White,
                fontSize = if (dialNumber.length > 12) 28.sp else 36.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )

            if (dialNumber.isNotEmpty()) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Cancella",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val dialPad = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            dialPad.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { (digit, letters) ->
                        DialButton(
                            digit = digit,
                            letters = letters,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitPress(digit)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CallGreen)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCall()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Chiama",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DialButton(
    digit: String,
    letters: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentiTab(
    callHistory: List<CallLogEntity>,
    onCallTap: (String) -> Unit
) {
    if (callHistory.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Nessuna chiamata recente",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(callHistory, key = { it.id }) { callLog ->
                CallLogItem(
                    callLog = callLog,
                    onTap = {
                        val number = if (callLog.direction == "inbound") callLog.caller else callLog.callee
                        onCallTap(number)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogItem(
    callLog: CallLogEntity,
    onTap: () -> Unit
) {
    val isInbound = callLog.direction == "inbound"
    val isMissed = callLog.disposition != "ANSWERED" && isInbound
    val number = if (isInbound) callLog.caller else callLog.callee

    val directionIcon = when {
        isMissed -> Icons.Default.CallMissed
        isInbound -> Icons.Default.ArrowDownward
        else -> Icons.Default.ArrowUpward
    }

    val directionColor = when {
        isMissed -> CallRed
        isInbound -> CallGreen
        else -> PrimaryBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(directionColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = directionIcon,
                contentDescription = null,
                tint = directionColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = number,
                color = if (isMissed) CallRed else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isInbound) "In arrivo" else "In uscita",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
                if (callLog.duration > 0) {
                    Text(
                        text = " • ${formatCallDuration(callLog.duration)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Text(
            text = formatTimeAgo(callLog.startTime),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onTap,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Chiama",
                tint = CallGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContattiTab(
    contacts: List<ContactEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onContactTap: (ContactEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = {
                Text(
                    text = "Cerca contatti...",
                    color = Color.White.copy(alpha = 0.4f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancella",
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkSurfaceVariant,
                unfocusedContainerColor = DarkSurfaceVariant,
                cursorColor = PrimaryBlue,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Nessun contatto trovato" else "Nessun contatto",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            val groupedContacts = contacts.groupBy {
                val firstChar = it.name.firstOrNull()?.uppercaseChar() ?: '#'
                if (firstChar.isLetter()) firstChar.toString() else "#"
            }.toSortedMap()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                groupedContacts.forEach { (letter, contactsInGroup) ->
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = letter,
                                color = PrimaryBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(contactsInGroup, key = { it.id }) { contact ->
                        ContactItem(
                            contact = contact,
                            onTap = { onContactTap(contact) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: ContactEntity,
    onTap: () -> Unit
) {
    val initials = contact.name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifEmpty { "?" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = PrimaryBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                if (!contact.company.isNullOrBlank()) {
                    Text(
                        text = contact.company,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!contact.phone.isNullOrBlank()) {
                Text(
                    text = contact.phone,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }

        if (!contact.phone.isNullOrBlank()) {
            IconButton(
                onClick = onTap,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Chiama",
                    tint = CallGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatCallDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

private fun formatTimeAgo(date: Date): String {
    val now = System.currentTimeMillis()
    val diff = now - date.time

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> "Ora"
        minutes < 60 -> "${minutes}m fa"
        hours < 24 -> "${hours}h fa"
        days < 7 -> "${days}g fa"
        days < 30 -> "${days / 7}sett fa"
        else -> "${days / 30}mesi fa"
    }
}
