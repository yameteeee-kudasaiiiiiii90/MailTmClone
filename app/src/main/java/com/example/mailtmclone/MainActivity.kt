package com.example.mailtmclone

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

val BgSlate900 = Color(0xFF0F172A)
val SurfaceSlate800 = Color(0xFF1E293B)
val BorderSlate700 = Color(0xFF334155)
val Indigo500 = Color(0xFF6366F1)
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

    Scaffold(
        containerColor = BgSlate900,
        topBar = {
            MailTmHeader(
                email = vm.currentEmail,
                isRefreshing = vm.isRefreshing,
                onCopy = {
                    clipboard.setText(AnnotatedString(vm.currentEmail))
                    Toast.makeText(context, "تم نسخ البريد الإلكتروني!", Toast.LENGTH_SHORT).show()
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
                BackHandler { vm.selectedMessage = null }
                MessageDetailScreen(
                    message = vm.selectedMessage!!,
                    onBack = { vm.selectedMessage = null },
                    onDelete = { vm.deleteMessage(vm.selectedMessage!!.id) }
                )
            } else {
                if (vm.messages.isEmpty()) {
                    EmptyInboxView(isRefreshing = vm.isRefreshing)
                } else {
                    InboxListView(
                        messages = vm.messages,
                        onOpenMessage = { vm.openMessage(it) },
                        onDeleteMessage = { vm.deleteMessage(it) }
                    )
                }
            }
        }
    }

    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = SurfaceSlate800,
            contentColor = TextSlate50
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("إدارة الحساب المؤقت", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSlate900)
                        .border(1.dp, BorderSlate700, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("البريد: ${vm.currentEmail}", fontSize = 13.sp, color = TextSlate50)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "كلمة المرور: ${vm.currentPassword}",
                            fontSize = 13.sp,
                            color = TextSlate400,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        vm.generateNewEmail()
                        showAccountSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("توليد بريد عشوائي جديد")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        showAccountSheet = false
                        showLoginDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل الدخول لبريد سابق")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        vm.deleteCurrentAccount()
                        showAccountSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حذف هذا الحساب نهائياً")
                }

                Spacer(modifier = Modifier.height(20.dp))
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
}

@Composable
fun MailTmHeader(
    email: String,
    isRefreshing: Boolean,
    onCopy: () -> Unit,
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
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
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
                        Icon(Icons.Default.Person, contentDescription = "الحساب", tint = TextSlate50, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                Text(
                    text = "نسخ",
                    color = Indigo500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyInboxView(isRefreshing: Boolean) {
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
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = Indigo500,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "صندوق الوارد فارغ",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextSlate50
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "بانتظار وصول رسائل جديدة تلقائياً...",
            fontSize = 14.sp,
            color = TextSlate400
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isRefreshing) {
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
        contentPadding = PaddingValues(16.dp),
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
                    Text(
                        text = (msg.from.name?.firstOrNull() ?: msg.from.address.first()).uppercase(),
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = msg.subject.ifBlank { "(بدون عنوان)" },
                        fontWeight = if (!msg.seen) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        color = TextSlate50,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = msg.intro,
                        fontSize = 12.sp,
                        color = TextSlate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { onDeleteMessage(msg.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = TextSlate400, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun MessageDetailScreen(
    message: MessageDetail,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                Text("العودة لصندوق الوارد", color = TextSlate50)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف الرسالة", tint = DangerRed)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = message.subject.ifBlank { "(بدون عنوان)" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextSlate50
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "من: ${message.from.name ?: ""} <${message.from.address}>",
                fontSize = 13.sp,
                color = TextSlate400
            )
        }

        HorizontalDivider(color = BorderSlate700)

        val htmlContent = message.html?.joinToString("") ?: "<pre style='color:#F8FAFC;font-family:sans-serif;'>${message.text ?: ""}</pre>"

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
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
            }
        )
    }
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
        title = { Text("تسجيل الدخول لبريد مؤقت", color = TextSlate50) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("عنوان البريد") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(email, password) },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
            ) {
                Text("دخول")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSlate400)
            }
        }
    )
}
