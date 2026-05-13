/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet

@Suppress("LongParameterList", "TooManyFunctions")
@KoinViewModel
class MessageViewModel(
    savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    quickChatActionRepository: QuickChatActionRepository,
    private val serviceRepository: ServiceRepository,
    private val packetRepository: PacketRepository,
    private val uiPrefs: UiPrefs,
    private val customEmojiPrefs: CustomEmojiPrefs,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    private val notificationManager: NotificationManager,
    private val sendMessageUseCase: SendMessageUseCase,
) : ViewModel() {
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val nodeList: StateFlow<List<Node>> = nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(ChannelSet())

    val showQuickChat = uiPrefs.showQuickChat

    private val _showFiltered = MutableStateFlow(false)
    val showFiltered: StateFlow<Boolean> = _showFiltered.asStateFlow()

    val quickChatActions = quickChatActionRepository.getAllActions().stateInWhileSubscribed(initialValue = emptyList())

    val contactSettings: StateFlow<Map<String, ContactSettings>> =
        packetRepository.getContactSettings().stateInWhileSubscribed(initialValue = emptyMap())

    private val contactKeyForPagedMessages: MutableStateFlow<String?> = MutableStateFlow(null)
    private val pagedMessagesForContactKey: Flow<PagingData<Message>> =
        combine(contactKeyForPagedMessages.filterNotNull(), _showFiltered, contactSettings) {
                contactKey,
                showFiltered,
                settings,
            ->
            // If filtering is disabled for this contact, always include filtered messages
            val filteringDisabled = settings[contactKey]?.filteringDisabled ?: false
            val includeFiltered = showFiltered || filteringDisabled
            contactKey to includeFiltered
        }
            .flatMapLatest { (contactKey, includeFiltered) ->
                packetRepository.getMessagesFromPaged(contactKey, includeFiltered, ::getNode)
            }
            .cachedIn(viewModelScope)

    val frequentEmojis: List<String>
        get() =
            customEmojiPrefs.customEmojiFrequency.value
                ?.split(",")
                ?.associate { entry ->
                    entry.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].toInt() } ?: ("" to 0)
                }
                ?.toList()
                ?.sortedByDescending { it.second }
                ?.map { it.first }
                ?.take(6) ?: listOf("👍", "👎", "😂", "🔥", "❤️", "😮")

    val homoglyphEncodingEnabled = homoglyphEncodingPrefs.homoglyphEncodingEnabled

    val firstUnreadMessageUuid: StateFlow<Long?> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.getFirstUnreadMessageUuid(it) }
            .stateInWhileSubscribed(null)

    val hasUnreadMessages: StateFlow<Boolean?> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.hasUnreadMessages(it) }
            .stateInWhileSubscribed(null)

    val unreadCount: StateFlow<Int> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.getUnreadCountFlow(it) }
            .stateInWhileSubscribed(0)

    val filteredCount: StateFlow<Int> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.getFilteredCountFlow(it) }
            .stateInWhileSubscribed(0)

    init {
        val contactKey = savedStateHandle.get<String>("contactKey")
        if (contactKey != null) {
            contactKeyForPagedMessages.value = contactKey
        }
    }

    fun setContactKey(contactKey: String) {
        if (contactKeyForPagedMessages.value != contactKey) {
            contactKeyForPagedMessages.value = contactKey
        }
    }

    fun setTitle(title: String) {
        _title.value = title
    }

    fun getMessagesFromPaged(contactKey: String): Flow<PagingData<Message>> {
        if (contactKeyForPagedMessages.value != contactKey) {
            contactKeyForPagedMessages.value = contactKey
        }
        return pagedMessagesForContactKey
    }

    /**
     * Returns a non-paged reactive [Flow] of messages for a conversation. Used by desktop targets that don't use
     * paging-compose.
     *
     * @param contactKey The unique contact key identifying the conversation.
     * @param limit Optional maximum number of messages to return (null = all).
     */
    fun getMessagesFlow(contactKey: String, limit: Int? = null): Flow<List<Message>> {
        if (contactKeyForPagedMessages.value != contactKey) {
            contactKeyForPagedMessages.value = contactKey
        }
        return flow { emitAll(packetRepository.getMessagesFrom(contactKey, limit = limit, getNode = ::getNode)) }
    }

    fun toggleShowQuickChat() {
        uiPrefs.setShowQuickChat(!uiPrefs.showQuickChat.value)
    }

    fun toggleShowFiltered() {
        _showFiltered.update { !it }
    }

    fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) {
        safeLaunch(context = ioDispatcher, tag = "setContactFilteringDisabled") {
            packetRepository.setContactFilteringDisabled(contactKey, disabled)
        }
    }

    fun getNode(userId: String?) = nodeRepository.getNode(userId ?: DataPacket.ID_BROADCAST)

    fun getUser(userId: String?) = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)

    /**
     * Sends a message to a contact or channel.
     *
     * If the message is a direct message (no channel specified), this function will:
     * - If the device firmware version is older than 2.7.12, it will mark the destination node as a favorite to prevent
     *   it from being removed from the on-device node database.
     * - If the device firmware version is 2.7.12 or newer, it will send a shared contact to the destination node.
     *
     * @param str The message content.
     * @param contactKey The unique contact key, which is a combination of channel (optional) and node ID. Defaults to
     *   broadcasting on channel 0.
     * @param replyId The ID of the message this is a reply to, if any.
     */
    fun sendMessage(str: String, contactKey: String = "0${DataPacket.ID_BROADCAST}", replyId: Int? = null) {
        safeLaunch(tag = "sendMessage") { sendMessageUseCase.invoke(str, contactKey, replyId) }
    }

    fun sendReaction(emoji: String, replyId: Int, contactKey: String) = safeLaunch(tag = "sendReaction") {
        serviceRepository.onServiceAction(ServiceAction.Reaction(emoji, replyId, contactKey))
    }

    fun deleteMessages(uuidList: List<Long>) =
        safeLaunch(context = ioDispatcher, tag = "deleteMessages") { packetRepository.deleteMessages(uuidList) }

    fun clearUnreadCount(contact: String, messageUuid: Long, lastReadTimestamp: Long) =
        safeLaunch(context = ioDispatcher, tag = "clearUnreadCount") {
            val existingTimestamp = contactSettings.value[contact]?.lastReadMessageTimestamp ?: Long.MIN_VALUE
            if (lastReadTimestamp <= existingTimestamp) {
                return@safeLaunch
            }
            packetRepository.clearUnreadCount(contact, lastReadTimestamp)
            packetRepository.updateLastReadMessage(contact, messageUuid, lastReadTimestamp)
            val unreadCount = packetRepository.getUnreadCount(contact)
            if (unreadCount == 0) notificationManager.cancel(contact.hashCode())
        }
}
