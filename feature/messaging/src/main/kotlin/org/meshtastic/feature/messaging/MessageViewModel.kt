/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.os.RemoteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.QuickChatActionRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.prefs.emoji.CustomEmojiPrefs
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config.DeviceConfig.Role
import org.meshtastic.proto.SharedContact
import javax.inject.Inject

@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class MessageViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    quickChatActionRepository: QuickChatActionRepository,
    private val serviceRepository: ServiceRepository,
    private val packetRepository: PacketRepository,
    private val uiPrefs: UiPrefs,
    private val customEmojiPrefs: CustomEmojiPrefs,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    private val meshServiceNotifications: MeshServiceNotifications,
) : ViewModel() {
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val nodeList: StateFlow<List<Node>> = nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(ChannelSet())

    private val _showQuickChat = MutableStateFlow(uiPrefs.showQuickChat)
    val showQuickChat: StateFlow<Boolean> = _showQuickChat

    private val _showFiltered = MutableStateFlow(false)
    val showFiltered: StateFlow<Boolean> = _showFiltered.asStateFlow()

    val quickChatActions = quickChatActionRepository.getAllActions().stateInWhileSubscribed(initialValue = emptyList())

    val contactSettings: StateFlow<Map<String, ContactSettings>> =
        packetRepository.getContactSettings().stateInWhileSubscribed(initialValue = emptyMap())

    val retryEvents = serviceRepository.retryEvents

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
            customEmojiPrefs.customEmojiFrequency
                ?.split(",")
                ?.associate { entry ->
                    entry.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].toInt() } ?: ("" to 0)
                }
                ?.toList()
                ?.sortedByDescending { it.second }
                ?.map { it.first }
                ?.take(6) ?: listOf("👍", "👎", "😂", "🔥", "❤️", "😮")

    val homoglyphEncodingEnabled = homoglyphEncodingPrefs.getHomoglyphEncodingEnabledChangesFlow()

    init {
        val contactKey = savedStateHandle.get<String>("contactKey")
        if (contactKey != null) {
            contactKeyForPagedMessages.value = contactKey
        }
    }

    fun setTitle(title: String) {
        viewModelScope.launch { _title.value = title }
    }

    fun getMessagesFromPaged(contactKey: String): Flow<PagingData<Message>> {
        if (contactKeyForPagedMessages.value != contactKey) {
            contactKeyForPagedMessages.value = contactKey
        }
        return pagedMessagesForContactKey
    }

    fun getFirstUnreadMessageUuid(contactKey: String): Flow<Long?> =
        packetRepository.getFirstUnreadMessageUuid(contactKey)

    fun hasUnreadMessages(contactKey: String): Flow<Boolean> = packetRepository.hasUnreadMessages(contactKey)

    fun toggleShowQuickChat() = toggle(_showQuickChat) { uiPrefs.showQuickChat = it }

    fun toggleShowFiltered() {
        _showFiltered.update { !it }
    }

    fun getFilteredCount(contactKey: String): Flow<Int> = packetRepository.getFilteredCountFlow(contactKey)

    fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { packetRepository.setContactFilteringDisabled(contactKey, disabled) }
    }

    private fun toggle(state: MutableStateFlow<Boolean>, onChanged: (newValue: Boolean) -> Unit) {
        (!state.value).let { toggled ->
            state.update { toggled }
            onChanged(toggled)
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
    @Suppress("NestedBlockDepth")
    fun sendMessage(str: String, contactKey: String = "0${DataPacket.ID_BROADCAST}", replyId: Int? = null) {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        // if the destination is a node, we need to ensure it's a
        // favorite so it does not get removed from the on-device node database.
        if (channel == null) { // no channel specified, so we assume it's a direct message
            val fwVersion = ourNodeInfo.value?.metadata?.firmware_version
            val destNode = nodeRepository.getNode(dest)
            val isClientBase = ourNodeInfo.value?.user?.role == Role.CLIENT_BASE

            val capabilities = Capabilities(fwVersion)

            if (capabilities.canSendVerifiedContacts) {
                sendSharedContact(destNode)
            } else {
                if (!destNode.isFavorite && !isClientBase) {
                    favoriteNode(destNode)
                }
            }
        }

        // Applying homoglyph encoding to the transmitted string if user has activated the feature
        // In most cases the value in "str" parameter will already contain the correct
        // transformed string from the text input. This call here added to make sure that
        // the feature is effective across all possible message paths (quick-chat, reply, etc.)
        val dataPacketText: String =
            if (homoglyphEncodingPrefs.homoglyphEncodingEnabled) {
                HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(str)
            } else {
                str
            }

        val p =
            DataPacket(dest, channel ?: 0, dataPacketText, replyId).apply {
                from = ourNodeInfo.value?.user?.id ?: DataPacket.ID_LOCAL
            }
        sendDataPacket(p)
    }

    fun sendReaction(emoji: String, replyId: Int, contactKey: String) =
        viewModelScope.launch { serviceRepository.onServiceAction(ServiceAction.Reaction(emoji, replyId, contactKey)) }

    fun deleteMessages(uuidList: List<Long>) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteMessages(uuidList) }

    fun clearUnreadCount(contact: String, messageUuid: Long, lastReadTimestamp: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            val existingTimestamp = contactSettings.value[contact]?.lastReadMessageTimestamp ?: Long.MIN_VALUE
            if (lastReadTimestamp <= existingTimestamp) {
                return@launch
            }
            packetRepository.clearUnreadCount(contact, lastReadTimestamp)
            packetRepository.updateLastReadMessage(contact, messageUuid, lastReadTimestamp)
            val unreadCount = packetRepository.getUnreadCount(contact)
            if (unreadCount == 0) meshServiceNotifications.cancelMessageNotification(contact)
        }

    private fun favoriteNode(node: Node) = viewModelScope.launch {
        try {
            serviceRepository.onServiceAction(ServiceAction.Favorite(node))
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Favorite node error" }
        }
    }

    private fun sendSharedContact(node: Node) = viewModelScope.launch {
        try {
            val contact =
                SharedContact(node_num = node.num, user = node.user, manually_verified = node.manuallyVerified)
            serviceRepository.onServiceAction(ServiceAction.SendContact(contact = contact))
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Send shared contact error" }
        }
    }

    private fun sendDataPacket(p: DataPacket) {
        try {
            serviceRepository.meshService?.send(p)
        } catch (ex: RemoteException) {
            Logger.e { "Send DataPacket error: ${ex.message}" }
        }
    }

    fun respondToRetry(packetId: Int, shouldRetry: Boolean) {
        serviceRepository.respondToRetry(packetId, shouldRetry)
    }
}
