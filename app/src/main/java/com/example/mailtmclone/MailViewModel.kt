package com.example.mailtmclone

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.UUID

class MailViewModel(application: Application) : AndroidViewModel(application) {
    private val api = MailTmApi.create()
    private val sessionManager = SessionManager(application)
    private val notificationHelper = NotificationHelper(application)

    var currentEmail by mutableStateOf("Generating address...")
    var currentPassword by mutableStateOf("")
    var availableDomains by mutableStateOf<List<String>>(emptyList())
    var messages by mutableStateOf<List<MessageSummary>>(emptyList())
    var selectedMessage by mutableStateOf<MessageDetail?>(null)
    var selectedMessageSource by mutableStateOf<String?>(null)
    var savedAccounts by mutableStateOf<List<SavedSession>>(emptyList())

    // Quota tracking
    var quotaUsedBytes by mutableStateOf(0L)
    var quotaLimitBytes by mutableStateOf(40000000L)

    // Search and Filtering
    var searchQuery by mutableStateOf("")
    var filterUnreadOnly by mutableStateOf(false)

    var isRefreshing by mutableStateOf(false)
    var statusMessage by mutableStateOf<String?>(null)

    private var token: String? = null
    private var accountId: String? = null
    private var refreshJob: Job? = null

    val filteredMessages: List<MessageSummary>
        get() = messages.filter { msg ->
            val matchesUnread = !filterUnreadOnly || !msg.seen
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                msg.subject.lowercase().contains(q) ||
                msg.from.address.lowercase().contains(q) ||
                (msg.from.name?.lowercase()?.contains(q) == true) ||
                (msg.intro?.lowercase()?.contains(q) == true)
            matchesUnread && matchesSearch
        }

    init {
        savedAccounts = sessionManager.getAccountHistory()
        loadDomains()
        restoreOrGenerateAccount()
    }

    fun loadDomains() {
        viewModelScope.launch {
            try {
                val domainList = api.getDomains().domains.filter { it.isActive }.map { it.domain }
                if (domainList.isNotEmpty()) {
                    availableDomains = domainList
                }
            } catch (_: Exception) {}
        }
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
            selectedMessageSource = null
            try {
                val domains = api.getDomains().domains.filter { it.isActive }
                if (domains.isNotEmpty()) {
                    availableDomains = domains.map { it.domain }
                    val domain = domains.first().domain
                    val randomUser = UUID.randomUUID().toString().replace("-", "").take(10)
                    val email = "$randomUser@$domain"
                    val pass = UUID.randomUUID().toString().take(12)

                    createAndActivateAccount(email, pass)
                    statusMessage = "New temporary address created"
                }
            } catch (e: Exception) {
                statusMessage = "Unable to connect to Mail.tm server"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun createCustomEmail(username: String, domain: String, passwordInput: String) {
        viewModelScope.launch {
            val cleanUser = username.trim().lowercase()
            if (cleanUser.isEmpty()) {
                statusMessage = "Please enter a valid username"
                return@launch
            }
            val finalPass = passwordInput.trim().ifEmpty { UUID.randomUUID().toString().take(12) }
            val fullEmail = "$cleanUser@$domain"

            isRefreshing = true
            selectedMessage = null
            selectedMessageSource = null
            try {
                createAndActivateAccount(fullEmail, finalPass)
                statusMessage = "Custom address $fullEmail created!"
            } catch (e: HttpException) {
                statusMessage = if (e.code() == 422) {
                    "Address already taken or invalid characters"
                } else {
                    "Failed to create custom address (HTTP ${e.code()})"
                }
            } catch (e: Exception) {
                statusMessage = "Network error while creating custom address"
            } finally {
                isRefreshing = false
            }
        }
    }

    private suspend fun createAndActivateAccount(email: String, pass: String) {
        val req = AccountRequest(email, pass)
        val accRes = api.createAccount(req)
        val tokenRes = api.getToken(req)

        val bearerToken = "Bearer ${tokenRes.token}"
        accountId = accRes.id
        token = bearerToken
        currentEmail = email
        currentPassword = pass
        quotaUsedBytes = accRes.used
        quotaLimitBytes = accRes.quota
        messages = emptyList()

        sessionManager.saveSession(email, pass, bearerToken, accRes.id)
        savedAccounts = sessionManager.getAccountHistory()
        MailWorkScheduler.schedulePeriodicSync(getApplication())

        startAutoRefresh()
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
                selectedMessageSource = null

                sessionManager.saveSession(cleanEmail, cleanPass, bearerToken, tokenRes.id)
                savedAccounts = sessionManager.getAccountHistory()
                MailWorkScheduler.schedulePeriodicSync(getApplication())

                fetchMessages()
                startAutoRefresh()
                statusMessage = "Signed in to $cleanEmail"
            } catch (e: Exception) {
                statusMessage = "Invalid email or password"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun switchToSavedAccount(saved: SavedSession) {
        token = saved.token
        accountId = saved.accountId
        currentEmail = saved.email
        currentPassword = saved.password
        selectedMessage = null
        selectedMessageSource = null
        messages = emptyList()

        sessionManager.saveSession(saved.email, saved.password, saved.token, saved.accountId)
        savedAccounts = sessionManager.getAccountHistory()
        statusMessage = "Switched to ${saved.email}"
        fetchMessages()
        startAutoRefresh()
    }

    fun removeSavedAccountFromHistory(email: String) {
        sessionManager.removeAccountFromHistory(email)
        savedAccounts = sessionManager.getAccountHistory()
    }

    fun deleteCurrentAccount() {
        val currentToken = token ?: return
        val id = accountId ?: return
        val emailToRemove = currentEmail
        viewModelScope.launch {
            isRefreshing = true
            try {
                api.deleteAccount(currentToken, id)
                sessionManager.removeAccountFromHistory(emailToRemove)
                sessionManager.clearActiveSession()
                savedAccounts = sessionManager.getAccountHistory()
                MailWorkScheduler.cancelSync(getApplication())
                statusMessage = "Account deleted permanently"
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
                            intro = msg.intro ?: ""
                        )
                        sessionManager.markMessageAsNotified(msg.id)
                    }
                }

                messages = incoming

                // Refresh storage quota info
                try {
                    val info = api.getAccountInfo(currentToken)
                    quotaUsedBytes = info.used
                    quotaLimitBytes = info.quota
                } catch (_: Exception) {}

            } catch (e: HttpException) {
                if (e.code() == 401 && currentEmail.contains("@") && currentPassword.isNotEmpty()) {
                    // Auto-renew expired token
                    try {
                        val tokenRes = api.getToken(AccountRequest(currentEmail, currentPassword))
                        val newBearer = "Bearer ${tokenRes.token}"
                        token = newBearer
                        sessionManager.saveSession(currentEmail, currentPassword, newBearer, tokenRes.id)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            isRefreshing = false
        }
    }

    fun openMessage(id: String) {
        val currentToken = token ?: return
        viewModelScope.launch {
            isRefreshing = true
            selectedMessageSource = null
            try {
                selectedMessage = api.getMessageDetail(currentToken, id)
                api.markAsRead(currentToken, id)
                sessionManager.markMessageAsNotified(id)
                messages = messages.map { if (it.id == id) it.copy(seen = true) else it }
            } catch (_: Exception) {}
            isRefreshing = false
        }
    }

    fun fetchMessageSource(id: String) {
        val currentToken = token ?: return
        viewModelScope.launch {
            try {
                val res = api.getMessageSource(currentToken, id)
                selectedMessageSource = res.data
            } catch (_: Exception) {
                statusMessage = "Could not load raw message source"
            }
        }
    }

    fun markAllAsRead() {
        val currentToken = token ?: return
        val unread = messages.filter { !it.seen }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            unread.forEach { msg ->
                try {
                    api.markAsRead(currentToken, msg.id)
                    sessionManager.markMessageAsNotified(msg.id)
                } catch (_: Exception) {}
            }
            messages = messages.map { it.copy(seen = true) }
            statusMessage = "All messages marked as read"
        }
    }

    fun deleteMessage(id: String) {
        val currentToken = token ?: return
        viewModelScope.launch {
            try {
                api.deleteMessage(currentToken, id)
                messages = messages.filterNot { it.id == id }
                selectedMessage = null
                selectedMessageSource = null
                statusMessage = "Message deleted"
            } catch (_: Exception) {}
        }
    }

    fun getAuthToken(): String? = token

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
