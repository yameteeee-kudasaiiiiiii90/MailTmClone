package com.example.mailtmclone

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedSession(
    val email: String,
    val password: String,
    val token: String,
    val accountId: String
)

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("mail_tm_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSession(email: String, password: String, token: String, accountId: String) {
        val session = SavedSession(email, password, token, accountId)
        prefs.edit()
            .putString("email", email)
            .putString("password", password)
            .putString("token", token)
            .putString("account_id", accountId)
            .apply()
        addAccountToHistory(session)
    }

    fun getSession(): SavedSession? {
        val email = prefs.getString("email", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        val accountId = prefs.getString("account_id", null) ?: return null
        return SavedSession(email, password, token, accountId)
    }

    fun clearActiveSession() {
        prefs.edit()
            .remove("email")
            .remove("password")
            .remove("token")
            .remove("account_id")
            .apply()
    }

    // --- Multi-Account Switcher History ---

    fun getAccountHistory(): List<SavedSession> {
        val json = prefs.getString("account_history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun addAccountToHistory(session: SavedSession) {
        val currentList = getAccountHistory().toMutableList()
        currentList.removeAll { it.email.equals(session.email, ignoreCase = true) }
        currentList.add(0, session) // Most recent first
        val trimmed = currentList.take(10) // Keep up to 10 recent accounts
        prefs.edit().putString("account_history", gson.toJson(trimmed)).apply()
    }

    fun removeAccountFromHistory(email: String) {
        val currentList = getAccountHistory().toMutableList()
        currentList.removeAll { it.email.equals(email, ignoreCase = true) }
        prefs.edit().putString("account_history", gson.toJson(currentList)).apply()
    }

    // --- Notification Tracking ---

    fun getNotifiedMessageIds(): Set<String> {
        return prefs.getStringSet("notified_ids", emptySet()) ?: emptySet()
    }

    fun markMessageAsNotified(messageId: String) {
        val updated = getNotifiedMessageIds().toMutableSet()
        updated.add(messageId)
        prefs.edit().putStringSet("notified_ids", updated).apply()
    }
}
