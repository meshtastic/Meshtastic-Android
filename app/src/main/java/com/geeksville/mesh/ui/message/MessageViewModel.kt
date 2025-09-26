/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.QuickChatActionRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.ServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.prefs.ui.UiPrefs
import javax.inject.Inject

@HiltViewModel
class MessageViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    quickChatActionRepository: QuickChatActionRepository,
    serviceRepository: ServiceRepository,
    packetRepository: PacketRepository,
    private val uiPrefs: UiPrefs,
) : ViewModel() {
    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val channels =
        radioConfigRepository.channelSetFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            channelSet {},
        )

    private val _showQuickChat = MutableStateFlow(uiPrefs.showQuickChat)
    val showQuickChat: StateFlow<Boolean> = _showQuickChat

    val quickChatActions =
        quickChatActionRepository
            .getAllActions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val contactKeyForMessages: MutableStateFlow<String?> = MutableStateFlow(null)
    private val messagesForContactKey: StateFlow<List<Message>> =
        contactKeyForMessages
            .filterNotNull()
            .flatMapLatest { contactKey -> packetRepository.getMessagesFrom(contactKey, ::getNode) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun getMessagesFrom(contactKey: String): StateFlow<List<Message>> {
        contactKeyForMessages.value = contactKey
        return messagesForContactKey
    }

    fun toggleShowQuickChat() = toggle(_showQuickChat) { uiPrefs.showQuickChat = it }

    private fun toggle(state: MutableStateFlow<Boolean>, onChanged: (newValue: Boolean) -> Unit) {
        (!state.value).let { toggled ->
            state.update { toggled }
            onChanged(toggled)
        }
    }

    fun getNode(userId: String?) = nodeRepository.getNode(userId ?: DataPacket.ID_BROADCAST)

    fun getUser(userId: String?) = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)
}
