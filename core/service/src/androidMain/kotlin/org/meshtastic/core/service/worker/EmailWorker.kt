/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.service.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinWorker
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.repository.EmailPrefs
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

@KoinWorker
class EmailWorker(
    context: Context,
    params: WorkerParameters,
    private val emailPrefs: EmailPrefs,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!emailPrefs.emailEnabled.value) {
            return Result.success()
        }

        val db = DatabaseProvider.db ?: return Result.retry()
        val dao = db.emailQueueDao()
        val unsentEmails = dao.getUnsentEmails().first()

        if (unsentEmails.isEmpty()) {
            return Result.success()
        }

        val host = emailPrefs.smtpHost.value
        val port = emailPrefs.smtpPort.value
        val user = emailPrefs.smtpUser.value
        val pass = emailPrefs.smtpPassword.value
        val auth = emailPrefs.smtpAuth.value
        val startTls = emailPrefs.smtpStartTls.value

        if (host.isBlank()) {
            Logger.e { "EmailWorker: SMTP Host is not configured" }
            return Result.failure()
        }

        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", auth.toString())
            put("mail.smtp.starttls.enable", startTls.toString())
        }

        val session = if (auth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(user, pass)
                }
            })
        } else {
            Session.getInstance(props)
        }

        var allSuccessful = true
        for (email in unsentEmails) {
            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(user))
                    setRecipient(Message.RecipientType.TO, InternetAddress(email.recipient))
                    setSubject(email.subject)
                    setText(email.content)
                }

                Transport.send(message)
                dao.markAsSent(email.id)
                Logger.i { "EmailWorker: Successfully sent email to ${email.recipient}" }
            } catch (e: Exception) {
                Logger.e(e) { "EmailWorker: Failed to send email to ${email.recipient}" }
                allSuccessful = false
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "email_worker"
    }
}
