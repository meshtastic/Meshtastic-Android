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

@KoinViewModel
class EmailQueueViewModel : ViewModel() {
    private val dao = DatabaseProvider.db?.emailQueueDao()

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
}
