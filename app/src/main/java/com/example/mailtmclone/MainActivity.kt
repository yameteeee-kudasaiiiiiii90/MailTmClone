package com.example.mailtmclone

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

// Official Mail.tm Color Palette (Tailwind Slate & Indigo)
val BgSlate900 = Color(0xFF0F172A)
val SurfaceSlate800 = Color(0xFF1E293B)
val BorderSlate700 = Color(0xFF334155)
val Indigo500 = Color(0xFF6366F1)
val Emerald500 = Color(0xFF10B981)
val TextSlate50 = Color(0xFFF8FAFC)
val TextSlate400 = Color(0xFF94A3B8)
val DangerRed = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColorScheme = darkColorScheme(
                primary = Indigo500,
                background = BgSlate900,
                surface = SurfaceSlate800,
                onBackground = TextSlate50,
                onSurface = TextSlate50
            )
            MaterialTheme(colorScheme = darkColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgSlate900) {
                    MailTmCloneScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailTmCloneScreen(vm: MailViewModel = viewModel()) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showAccountSheet by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showCustomEmailDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(vm.statusMessage) {
        vm.statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.statusMessage = null
        }
    }

    val unreadCount = vm.messages.count { !it.seen }

    Scaffold(
        containerColor = BgSlate900,
        topBar = {
            MailTmHeader(
                email = vm.currentEmail,
                unreadCount = unreadCount,
                isRefreshing = vm.isRefreshing,
                onCopy = {
                    clipboard.setText(AnnotatedString(vm.currentEmail))
                    Toast.makeText(context, "Email address copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, vm.currentEmail)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Temporary Email"))
                },
                onRefresh = { vm.fetchMessages() },
                onOpenMenu = { showAccountSheet = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (vm.selectedMessage != null) {
                BackHandler {
                    vm.selectedMessage = null
                    vm.selectedMessageSource = null
                }
                MessageDetailScreen(
                    message = vm.selectedMessage!!,
                    rawSource = vm.selectedMessageSource,
                    authToken = vm.getAuthToken(),
                    onLoadSource = { vm.fetchMessageSource(vm.selectedMessage!!.id) },
                    onBack = {
                        vm.selectedMessage = null
                        vm.selectedMessageSource = null
                    },
                    onDelete = { vm.deleteMessage(vm.selectedMessage!!.id) }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Inbox Search & Filter Toolbar
                    InboxFilterBar(
                        searchQuery = vm.searchQuery,
                        onSearchChange = { vm.searchQuery = it },
                        filterUnreadOnly = vm.filterUnreadOnly,
                        onToggleUnreadFilter = { vm.filterUnreadOnly = !vm.filterUnreadOnly },
                        unreadCount = unreadCount,
                        totalCount = vm.messages.size,
                        onMarkAllRead = { vm.markAllAsRead() }
                    )

                    val displayedMessages = vm.filteredMessages
                    if (displayedMessages.isEmpty()) {
                        EmptyInboxView(
                            isRefreshing = vm.isRefreshing,
                            hasActiveFilter = vm.searchQuery.isNotBlank() || vm.filterUnreadOnly
                        )
                    } else {
                        InboxListView(
                            messages = displayedMessages,
                            onOpenMessage = { vm.openMessage(it) },
                            onDeleteMessage = { vm.deleteMessage(it) }
                        )
                    }
                }
            }
        }
    }

    // Account Management Bottom Sheet
    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = SurfaceSlate800,
            contentColor = TextSlate50
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Account Manager", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Current Account Credentials & Storage Quota Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSlate900)
                        .border(1.dp, BorderSlate700, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ACTIVE ADDRESS", fontSize = 11.sp, color = Indigo500, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(vm.currentEmail, fontSize = 14.sp, color = TextSlate50, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Password: ${vm.currentPassword}",
                                    fontSize = 12.sp,
                                    color = TextSlate400,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(
                                        AnnotatedString("Email: ${vm.currentEmail}\nPassword: ${vm.currentPassword}")
                                    )
                                    Toast.makeText(context, "Credentials copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Credentials", tint = Indigo500)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        val usedMb = String.format(Locale.US, "%.2f", vm.quotaUsedBytes / (1024f * 1024f))
                        val limitMb = String.format(Locale.US, "%.0f", vm.quotaLimitBytes / (1024f * 1024f))
                        val progress = (vm.quotaUsedBytes.toFloat() / vm.quotaLimitBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Storage Used", fontSize = 11.sp, color = TextSlate400)
                            Text("$usedMb MB / $limitMb MB", fontSize = 11.sp, color = TextSlate400)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Indigo500,
                            trackColor = BorderSlate700
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = {
                        vm.generateNewEmail()
                        showAccountSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Random Address")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        vm.loadDomains()
                        showAccountSheet = false
                        showCustomEmailDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Custom Address")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        showAccountSheet = false
                        showLoginDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign In to Existing Address")
                }

                // Multi-Account History Switcher
                if (vm.savedAccounts.size > 1) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text("Saved Accounts (Quick Switch)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextSlate400)
                    Spacer(modifier = Modifier.height(8.dp))

                    vm.savedAccounts.forEach { acc ->
                        val isCurrent = acc.email.equals(vm.currentEmail, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgSlate900)
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) Indigo500 else BorderSlate700,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (!isCurrent) {
                                        vm.switchToSavedAccount(acc)
                                        showAccountSheet = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.email,
                                    fontSize = 13.sp,
                                    color = TextSlate50,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isCurrent) {
                                    Text("Active now", fontSize = 11.sp, color = Emerald500)
                                }
                            }
                            if (!isCurrent) {
                                IconButton(
                                    onClick = { vm.removeSavedAccountFromHistory(acc.email) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove from history", tint = TextSlate400, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        vm.deleteCurrentAccount()
                        showAccountSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Current Account Permanently")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showLoginDialog) {
        LoginAccountDialog(
            onDismiss = { showLoginDialog = false },
            onLogin = { email, pass ->
                vm.loginWithExisting(email, pass)
                showLoginDialog = false
            }
        )
    }

    if (showCustomEmailDialog) {
        CreateCustomEmailDialog(
            domains = vm.availableDomains,
            onDismiss = { showCustomEmailDialog = false },
            onCreate = { user, domain, pass ->
                vm.createCustomEmail(user, domain, pass)
                showCustomEmailDialog = false
            }
        )
    }
}

@Composable
fun MailTmHeader(
    email: String,
    unreadCount: Int,
    isRefreshing: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "spin"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceSlate800)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Indigo500),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "mail.tm",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextSlate50,
                    letterSpacing = 0.5.sp
                )
                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Indigo500,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "$unreadCount new",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Email",
                        tint = TextSlate400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Inbox",
                        tint = TextSlate400,
                        modifier = if (isRefreshing) Modifier.rotate(angle) else Modifier
                    )
                }
                IconButton(onClick = onOpenMenu) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BorderSlate700),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Account Menu", tint = TextSlate50, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pill Email Bar (Click to copy)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(BgSlate900)
                .border(1.dp, BorderSlate700, RoundedCornerShape(50))
                .clickable { onCopy() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = email,
                color = TextSlate50,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = Indigo500.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Indigo500, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy",
                        color = Indigo500,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InboxFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filterUnreadOnly: Boolean,
    onToggleUnreadFilter: () -> Unit,
    unreadCount: Int,
    totalCount: Int,
    onMarkAllRead: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search sender, subject, or content...", fontSize = 13.sp, color = TextSlate400) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSlate400) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextSlate400)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !filterUnreadOnly,
                    onClick = { if (filterUnreadOnly) onToggleUnreadFilter() },
                    label = { Text("All ($totalCount)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = filterUnreadOnly,
                    onClick = { onToggleUnreadFilter() },
                    label = { Text("Unread ($unreadCount)", fontSize = 12.sp) }
                )
            }

            if (unreadCount > 0) {
                TextButton(onClick = onMarkAllRead) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp), tint = Indigo500)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark all read", fontSize = 12.sp, color = Indigo500)
                }
            }
        }
    }
}

@Composable
fun EmptyInboxView(isRefreshing: Boolean, hasActiveFilter: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(SurfaceSlate800)
                .border(1.dp, BorderSlate700, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasActiveFilter) Icons.Default.SearchOff else Icons.Default.MarkEmailUnread,
                contentDescription = null,
                tint = Indigo500,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (hasActiveFilter) "No matching emails found" else "Your inbox is empty",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextSlate50
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasActiveFilter) "Try adjusting your search or filter settings." else "Waiting for incoming emails automatically...",
            fontSize = 14.sp,
            color = TextSlate400
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isRefreshing && !hasActiveFilter) {
            CircularProgressIndicator(color = Indigo500, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun InboxListView(
    messages: List<MessageSummary>,
    onOpenMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceSlate800)
                    .border(
                        width = 1.dp,
                        color = if (!msg.seen) Indigo500 else BorderSlate700,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onOpenMessage(msg.id) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Indigo500.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = (msg.from.name?.firstOrNull() ?: msg.from.address.firstOrNull() ?: '?').uppercase()
                    Text(
                        text = initial,
                        color = Indigo500,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!msg.seen) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Indigo500)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = msg.from.name?.ifBlank { msg.from.address } ?: msg.from.address,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextSlate50,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (msg.hasAttachments) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attachment",
                                tint = TextSlate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = msg.subject.ifBlank { "(No Subject)" },
                        fontWeight = if (!msg.seen) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        color = TextSlate50,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = msg.intro ?: "",
                        fontSize = 12.sp,
                        color = TextSlate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { onDeleteMessage(msg.id) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Email", tint = TextSlate400, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

enum class MessageViewMode { HTML, PLAIN_TEXT, RAW_SOURCE }

@Composable
fun MessageDetailScreen(
    message: MessageDetail,
    rawSource: String?,
    authToken: String?,
    onLoadSource: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(MessageViewMode.HTML) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Navigation & View Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceSlate800)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextSlate50)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back to Inbox", color = TextSlate50)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        viewMode = when (viewMode) {
                            MessageViewMode.HTML -> MessageViewMode.PLAIN_TEXT
                            MessageViewMode.PLAIN_TEXT -> {
                                onLoadSource()
                                MessageViewMode.RAW_SOURCE
                            }
                            MessageViewMode.RAW_SOURCE -> MessageViewMode.HTML
                        }
                    }
                ) {
                    Text(
                        text = when (viewMode) {
                            MessageViewMode.HTML -> "HTML"
                            MessageViewMode.PLAIN_TEXT -> "Text"
                            MessageViewMode.RAW_SOURCE -> "Source"
                        },
                        color = Indigo500,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Message", tint = DangerRed)
                }
            }
        }

        // Message Metadata Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = message.subject.ifBlank { "(No Subject)" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextSlate50
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "From: ${message.from.name ?: ""} <${message.from.address}>",
                fontSize = 13.sp,
                color = TextSlate400
            )
            Text(
                text = "Received: ${message.createdAt.replace("T", " ").take(19)}",
                fontSize = 12.sp,
                color = TextSlate400
            )

            // Attachments Section
            if (!message.attachments.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.attachments.forEach { att ->
                        AssistChip(
                            onClick = {
                                downloadAttachment(context, att, authToken)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Indigo500)
                            },
                            label = {
                                val kb = (att.size / 1024).coerceAtLeast(1)
                                Text("${att.filename} (${kb} KB)", fontSize = 12.sp, color = TextSlate50)
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = BorderSlate700)

        // Message Body Viewer
        when (viewMode) {
            MessageViewMode.HTML -> {
                val htmlContent = message.html?.joinToString("")
                    ?: "<pre style='color:#F8FAFC;font-family:sans-serif;white-space:pre-wrap;'>${message.text ?: "No message content"}</pre>"

                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.loadsImagesAutomatically = true
                            settings.defaultTextEncodingName = "utf-8"
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url ?: return false
                                    return try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, url))
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                            }
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
                    }
                )
            }

            MessageViewMode.PLAIN_TEXT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = message.text?.ifBlank { "No plain-text content available." } ?: "No plain-text content available.",
                        color = TextSlate50,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            MessageViewMode.RAW_SOURCE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = rawSource ?: "Loading raw EML message source...",
                        color = TextSlate400,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun downloadAttachment(context: Context, attachment: AttachmentItem, authToken: String?) {
    val relativeUrl = attachment.downloadUrl ?: return
    val fullUrl = if (relativeUrl.startsWith("http")) {
        relativeUrl
    } else {
        "https://api.mail.tm" + (if (relativeUrl.startsWith("/")) relativeUrl else "/$relativeUrl")
    }

    try {
        val request = DownloadManager.Request(Uri.parse(fullUrl))
            .setTitle(attachment.filename)
            .setDescription("Downloading attachment from Mail.tm")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, attachment.filename)

        if (!authToken.isNullOrBlank()) {
            request.addRequestHeader("Authorization", authToken)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Downloading ${attachment.filename}...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to start download", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCustomEmailDialog(
    domains: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedDomain by remember(domains) { mutableStateOf(domains.firstOrNull() ?: "mail.tm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceSlate800,
        title = { Text("Create Custom Address", color = TextSlate50, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (e.g. john.doe)") },
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = "@$selectedDomain",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Domain") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        domains.forEach { domain ->
                            DropdownMenuItem(
                                text = { Text("@$domain") },
                                onClick = {
                                    selectedDomain = domain
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    placeholder = { Text("Leave blank to auto-generate") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(username, selectedDomain, password) },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSlate400)
            }
        }
    )
}

@Composable
fun LoginAccountDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceSlate800,
        title = { Text("Sign In to Existing Address", color = TextSlate50, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(email, password) },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
            ) {
                Text("Sign In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSlate400)
            }
        }
    )
}
