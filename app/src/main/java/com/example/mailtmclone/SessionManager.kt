package com.example.mailtmclone

import android.content.Context

data class SavedSession(
    val email: String,
    val password: String,
    val token: String,
    val accountId: String
)

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("mail_tm_prefs", Context.MODE_PRIVATE)

    fun saveSession(email: String, password: String, token: String, accountId: String) {
        prefs.edit()
            .putString("email", email)
            .putString("password", password)
            .putString("token", token)
            .putString("account_id", accountId)
            .apply()
    }

    fun getSession(): SavedSession? {
        val email = prefs.getString("email", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        val accountId = prefs.getString("account_id", null) ?: return null
        return SavedSession(email, password, token, accountId)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun getNotifiedMessageIds(): Set<String> {
        return prefs.getStringSet("notified_ids", emptySet()) ?: emptySet()
    }

    fun markMessageAsNotified(messageId: String) {
        val updated = getNotifiedMessageIds().toMutableSet()
        updated.add(messageId)
        prefs.edit().putStringSet("notified_ids", updated).apply()
    }
}
