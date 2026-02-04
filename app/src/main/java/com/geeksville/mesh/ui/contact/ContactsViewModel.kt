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
package com.geeksville.mesh.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.geeksville.mesh.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.model.util.getShortDate
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.channel_name
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import javax.inject.Inject
import kotlin.collections.map as collectionsMap

@HiltViewModel
class ContactsViewModel
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    serviceRepository: ServiceRepository,
) : ViewModel() {
    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    // Combine node info and myId to reduce argument count in subsequent combines
    private val identityFlow: Flow<Pair<MyNodeEntity?, String?>> =
        combine(nodeRepository.myNodeInfo, nodeRepository.myId) { info, id -> Pair(info, id) }

    /**
     * Non-paginated contact list.
     *
     * NOTE: This is kept for ShareScreen which needs a simple, non-paginated list of contacts. The main ContactsScreen
     * uses [contactListPaged] instead for better performance with large contact lists.
     *
     * @see contactListPaged for the paginated version used in ContactsScreen
     */
    val contactList =
        combine(identityFlow, packetRepository.getContacts(), channels, packetRepository.getContactSettings()) {
                identity,
                contacts,
                channelSet,
                settings,
            ->
            val (myNodeInfo, myId) = identity
            val myNodeNum = myNodeInfo?.myNodeNum ?: return@combine emptyList()
            // Add empty channel placeholders (always show Broadcast contacts, even when empty)
            val placeholder =
                (0 until channelSet.settings.size).associate { ch ->
                    val contactKey = "$ch${DataPacket.ID_BROADCAST}"
                    val data = DataPacket(bytes = null, dataType = 1, time = 0L, channel = ch)
                    contactKey to Packet(0L, myNodeNum, 1, contactKey, 0L, true, data)
                }

            (contacts + (placeholder - contacts.keys)).values.collectionsMap { packet ->
                val data = packet.data
                val contactKey = packet.contact_key

                // Determine if this is my message (originated on this device)
                val fromLocal = data.from == DataPacket.ID_LOCAL || (myId != null && data.from == myId)
                val toBroadcast = data.to == DataPacket.ID_BROADCAST

                // grab usernames from NodeInfo
                val user = getUser(if (fromLocal) data.to else data.from)
                val node = getNode(if (fromLocal) data.to else data.from)

                val shortName = user.short_name ?: ""
                val longName =
                    if (toBroadcast) {
                        channelSet.getChannel(data.channel)?.name ?: getString(Res.string.channel_name)
                    } else {
                        user.long_name ?: ""
                    }

                Contact(
                    contactKey = contactKey,
                    shortName = if (toBroadcast) "${data.channel}" else shortName,
                    longName = longName,
                    lastMessageTime = getShortDate(data.time),
                    lastMessageText = if (fromLocal) data.text else "$shortName: ${data.text}",
                    unreadCount = packetRepository.getUnreadCount(contactKey),
                    messageCount = packetRepository.getMessageCount(contactKey),
                    isMuted = settings[contactKey]?.isMuted == true,
                    isUnmessageable = user.is_unmessagable ?: false,
                    nodeColors =
                    if (!toBroadcast) {
                        node.colors
                    } else {
                        null
                    },
                )
            }
        }
            .stateInWhileSubscribed(initialValue = emptyList())

    val contactListPaged: Flow<PagingData<Contact>> =
        combine(identityFlow, channels, packetRepository.getContactSettings()) { identity, channelSet, settings ->
            val (myNodeInfo, myId) = identity
            ContactsPagedParams(myNodeInfo?.myNodeNum, channelSet, settings, myId)
        }
            .flatMapLatest { params ->
                val myNodeNum = params.myNodeNum
                val channelSet = params.channelSet
                val settings = params.settings
                val myId = params.myId

                packetRepository.getContactsPaged().map { pagingData ->
                    pagingData.map { packet ->
                        val data = packet.data
                        val contactKey = packet.contact_key

                        // Determine if this is my message (originated on this device)
                        val fromLocal = data.from == DataPacket.ID_LOCAL || (myId != null && data.from == myId)
                        val toBroadcast = data.to == DataPacket.ID_BROADCAST

                        // grab usernames from NodeInfo
                        val user = getUser(if (fromLocal) data.to else data.from)
                        val node = getNode(if (fromLocal) data.to else data.from)

                        val shortName = user.short_name ?: ""
                        val longName =
                            if (toBroadcast) {
                                channelSet.getChannel(data.channel)?.name ?: getString(Res.string.channel_name)
                            } else {
                                user.long_name ?: ""
                            }

                        Contact(
                            contactKey = contactKey,
                            shortName = if (toBroadcast) "${data.channel}" else shortName,
                            longName = longName,
                            lastMessageTime = getShortDate(data.time),
                            lastMessageText = if (fromLocal) data.text else "$shortName: ${data.text}",
                            unreadCount = packetRepository.getUnreadCount(contactKey),
                            messageCount = packetRepository.getMessageCount(contactKey),
                            isMuted = settings[contactKey]?.isMuted == true,
                            isUnmessageable = user.is_unmessagable ?: false,
                            nodeColors =
                            if (!toBroadcast) {
                                node.colors
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            .cachedIn(viewModelScope)

    fun getNode(userId: String?) = nodeRepository.getNode(userId ?: DataPacket.ID_BROADCAST)

    fun deleteContacts(contacts: List<String>) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteContacts(contacts) }

    fun setMuteUntil(contacts: List<String>, until: Long) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.setMuteUntil(contacts, until) }

    fun getContactSettings() = packetRepository.getContactSettings()

    fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { packetRepository.setContactFilteringDisabled(contactKey, disabled) }
    }

    /**
     * Get the total message count for a list of contact keys. This queries the repository directly, so it works even if
     * contacts aren't loaded in the paged list.
     */
    suspend fun getTotalMessageCount(contactKeys: List<String>): Int = if (contactKeys.isEmpty()) {
        0
    } else {
        contactKeys.sumOf { contactKey -> packetRepository.getMessageCount(contactKey) }
    }

    private fun getUser(userId: String?) = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)

    private data class ContactsPagedParams(
        val myNodeNum: Int?,
        val channelSet: ChannelSet,
        val settings: Map<String, ContactSettings>,
        val myId: String?,
    )
}
