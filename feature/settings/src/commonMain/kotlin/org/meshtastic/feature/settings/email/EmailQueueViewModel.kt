/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.feature.settings.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.EmailQueueEntity
import org.meshtastic.core.repository.EmailPrefs
import org.meshtastic.core.repository.MeshWorkerManager

@KoinViewModel
class EmailQueueViewModel(
    private val emailPrefs: EmailPrefs,
    private val workerManager: MeshWorkerManager,
) : ViewModel() {
    private val dao = DatabaseProvider.db?.emailQueueDao()

    val emailEnabled = emailPrefs.emailEnabled
    val smtpHost = emailPrefs.smtpHost
    val smtpPort = emailPrefs.smtpPort
    val smtpUser = emailPrefs.smtpUser
    val smtpPassword = emailPrefs.smtpPassword
    val smtpAuth = emailPrefs.smtpAuth
    val smtpStartTls = emailPrefs.smtpStartTls

    fun setEmailEnabled(enabled: Boolean) {
        emailPrefs.setEmailEnabled(enabled)
        if (enabled) {
            workerManager.schedulePeriodicEmailWorker()
        } else {
            workerManager.cancelPeriodicEmailWorker()
        }
    }

    fun setSmtpHost(host: String) = emailPrefs.setSmtpHost(host)
    fun setSmtpPort(port: Int) = emailPrefs.setSmtpPort(port)
    fun setSmtpUser(user: String) = emailPrefs.setSmtpUser(user)
    fun setSmtpPassword(password: String) = emailPrefs.setSmtpPassword(password)
    fun setSmtpAuth(auth: Boolean) = emailPrefs.setSmtpAuth(auth)
    fun setSmtpStartTls(startTls: Boolean) = emailPrefs.setSmtpStartTls(startTls)

    val unsentEmails: StateFlow<List<EmailQueueEntity>> = 
        dao?.getUnsentEmails()?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) 
        ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    fun markAsSent(id: Long) {
        viewModelScope.launch {
            dao?.markAsSent(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao?.delete(id)
        }
    }

    fun testEmail() {
        workerManager.enqueueEmailWorker()
    }
}
