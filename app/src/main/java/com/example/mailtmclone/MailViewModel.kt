package com.example.mailtmclone

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MailViewModel(application: Application) : AndroidViewModel(application) {
    private val api = MailTmApi.create()
    private val sessionManager = SessionManager(application)
    private val notificationHelper = NotificationHelper(application)

    var currentEmail by mutableStateOf("جاري التحميل...")
    var currentPassword by mutableStateOf("")
    var messages by mutableStateOf<List<MessageSummary>>(emptyList())
    var selectedMessage by mutableStateOf<MessageDetail?>(null)
    var isRefreshing by mutableStateOf(false)
    var statusMessage by mutableStateOf<String?>(null)

    private var token: String? = null
    private var accountId: String? = null
    private var refreshJob: Job? = null

    init {
        restoreOrGenerateAccount()
    }

    private fun restoreOrGenerateAccount() {
        val saved = sessionManager.getSession()
        if (saved != null) {
            currentEmail = saved.email
            currentPassword = saved.password
            token = saved.token
            accountId = saved.accountId
            MailWorkScheduler.schedulePeriodicSync(getApplication())
            startAutoRefresh()
        } else {
            generateNewEmail()
        }
    }

    fun generateNewEmail() {
        viewModelScope.launch {
            isRefreshing = true
            selectedMessage = null
            try {
                val domains = api.getDomains().domains
                if (domains.isNotEmpty()) {
                    val domain = domains.first().domain
                    val randomUser = UUID.randomUUID().toString().replace("-", "").take(10)
                    val email = "$randomUser@$domain"
                    val pass = UUID.randomUUID().toString().take(12)

                    val req = AccountRequest(email, pass)
                    val accRes = api.createAccount(req)
                    val tokenRes = api.getToken(req)

                    val bearerToken = "Bearer ${tokenRes.token}"
                    accountId = accRes.id
                    token = bearerToken
                    currentEmail = email
                    currentPassword = pass
                    messages = emptyList()

                    sessionManager.clearSession()
                    sessionManager.saveSession(email, pass, bearerToken, accRes.id)
                    MailWorkScheduler.schedulePeriodicSync(getApplication())

                    startAutoRefresh()
                }
            } catch (e: Exception) {
                statusMessage = "تعذر الاتصال بخادم Mail.tm"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun loginWithExisting(email: String, pass: String) {
        viewModelScope.launch {
            isRefreshing = true
            try {
                val cleanEmail = email.trim()
                val cleanPass = pass.trim()
                val tokenRes = api.getToken(AccountRequest(cleanEmail, cleanPass))
                val bearerToken = "Bearer ${tokenRes.token}"

                accountId = tokenRes.id
                token = bearerToken
                currentEmail = cleanEmail
                currentPassword = cleanPass
                selectedMessage = null

                sessionManager.clearSession()
                sessionManager.saveSession(cleanEmail, cleanPass, bearerToken, tokenRes.id)
                MailWorkScheduler.schedulePeriodicSync(getApplication())

                fetchMessages()
                startAutoRefresh()
                statusMessage = "تم تسجيل الدخول وحفظ الحساب"
            } catch (e: Exception) {
                statusMessage = "بيانات الدخول غير صحيحة"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun deleteCurrentAccount() {
        val currentToken = token ?: return
        val id = accountId ?: return
        viewModelScope.launch {
            isRefreshing = true
            try {
                api.deleteAccount(currentToken, id)
                sessionManager.clearSession()
                MailWorkScheduler.cancelSync(getApplication())
                generateNewEmail()
            } catch (_: Exception) {
                isRefreshing = false
            }
        }
    }

    fun fetchMessages() {
        val currentToken = token ?: return
        viewModelScope.launch {
            isRefreshing = true
            try {
                val incoming = api.getMessages(currentToken).messages
                val notifiedIds = sessionManager.getNotifiedMessageIds()

                incoming.forEach { msg ->
                    if (!msg.seen && !notifiedIds.contains(msg.id)) {
                        val senderName = msg.from.name?.ifBlank { msg.from.address } ?: msg.from.address
                        notificationHelper.showNewMessageNotification(
                            messageId = msg.id,
                            sender = senderName,
                            subject = msg.subject,
                            intro = msg.intro
                        )
                        sessionManager.markMessageAsNotified(msg.id)
                    }
                }

                messages = incoming
            } catch (_: Exception) {}
            isRefreshing = false
        }
    }

    fun openMessage(id: String) {
        val currentToken = token ?: return
        viewModelScope.launch {
            isRefreshing = true
            try {
                selectedMessage = api.getMessageDetail(currentToken, id)
                api.markAsRead(currentToken, id)
                sessionManager.markMessageAsNotified(id)
                messages = messages.map { if (it.id == id) it.copy(seen = true) else it }
            } catch (_: Exception) {}
            isRefreshing = false
        }
    }

    fun deleteMessage(id: String) {
        val currentToken = token ?: return
        viewModelScope.launch {
            try {
                api.deleteMessage(currentToken, id)
                messages = messages.filterNot { it.id == id }
                selectedMessage = null
            } catch (_: Exception) {}
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (token != null) {
                fetchMessages()
                delay(8000)
            }
        }
    }
}
