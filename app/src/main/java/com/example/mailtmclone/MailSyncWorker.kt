package com.example.mailtmclone

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import retrofit2.HttpException

class MailSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val session = sessionManager.getSession() ?: return Result.success()

        val api = MailTmApi.create()
        val notificationHelper = NotificationHelper(applicationContext)

        return try {
            var currentToken = session.token

            val response = try {
                api.getMessages(currentToken)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    val newTokenRes = api.getToken(AccountRequest(session.email, session.password))
                    currentToken = "Bearer ${newTokenRes.token}"
                    sessionManager.saveSession(
                        email = session.email,
                        password = session.password,
                        token = currentToken,
                        accountId = session.accountId
                    )
                    api.getMessages(currentToken)
                } else {
                    throw e
                }
            }

            val notifiedIds = sessionManager.getNotifiedMessageIds()

            response.messages.forEach { msg ->
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

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
