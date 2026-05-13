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
package org.meshtastic.feature.messaging.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.Contact
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import kotlin.collections.map as collectionsMap

@KoinViewModel
class ContactsViewModel(
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    serviceRepository: ServiceRepository,
) : ViewModel() {
    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val unreadCountTotal = packetRepository.getUnreadCountTotal().stateInWhileSubscribed(0)

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    // Combine node info and myId to reduce argument count in subsequent combines
    private val identityFlow: Flow<Pair<MyNodeInfo?, String?>> =
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
            val myNodeNum = myNodeInfo?.myNodeNum ?: return@combine emptyList<Contact>()
            // Add empty channel placeholders (always show Broadcast contacts, even when empty)
            val placeholder =
                (0 until channelSet.settings.size).associate { ch ->
                    val contactKey = "$ch${DataPacket.ID_BROADCAST}"
                    val data = DataPacket(bytes = null, dataType = 1, time = 0L, channel = ch)
                    contactKey to data
                }

            (contacts + (placeholder - contacts.keys)).entries.collectionsMap { entry ->
                val contactKey = entry.key
                val packetData = entry.value
                // Determine if this is my message (originated on this device)
                val fromLocal =
                    (packetData.from == DataPacket.ID_LOCAL || (myId != null && packetData.from == myId))
                val toBroadcast = packetData.to == DataPacket.ID_BROADCAST

                // grab usernames from NodeInfo
                val userId = if (fromLocal) packetData.to else packetData.from
                val user = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)
                val node = nodeRepository.getNode(userId ?: DataPacket.ID_BROADCAST)

                val shortName = user.short_name
                val longName =
                    if (toBroadcast) {
                        channelSet.getChannel(packetData.channel)?.name ?: "Channel ${packetData.channel}"
                    } else {
                        user.long_name
                    }

                Contact(
                    contactKey = contactKey,
                    shortName = if (toBroadcast) packetData.channel.toString() else shortName,
                    longName = longName,
                    lastMessageTime = if (packetData.time != 0L) packetData.time else null,
                    lastMessageText = if (fromLocal) packetData.text else "$shortName: ${packetData.text}",
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
                val channelSet = params.channelSet
                val settings = params.settings
                val myId = params.myId

                packetRepository.getContactsPaged().map { pagingData ->
                    pagingData.map { packetData: DataPacket ->
                        // Determine if this is my message (originated on this device)
                        val fromLocal =
                            (packetData.from == DataPacket.ID_LOCAL || (myId != null && packetData.from == myId))
                        val toBroadcast = packetData.to == DataPacket.ID_BROADCAST

                        // Reconstruct contactKey exactly as rememberDataPacket() computes it:
                        // For outgoing or broadcast: use the "to" field (recipient / ^all)
                        // For incoming DMs: use the "from" field (the other party)
                        val contactId = if (fromLocal || toBroadcast) packetData.to else packetData.from
                        val contactKey = "${packetData.channel}$contactId"

                        // grab usernames from NodeInfo
                        val userId = if (fromLocal) packetData.to else packetData.from
                        val user = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)
                        val node = nodeRepository.getNode(userId ?: DataPacket.ID_BROADCAST)

                        val shortName = user.short_name
                        val longName =
                            if (toBroadcast) {
                                channelSet.getChannel(packetData.channel)?.name ?: "Channel ${packetData.channel}"
                            } else {
                                user.long_name
                            }

                        Contact(
                            contactKey = contactKey,
                            shortName = if (toBroadcast) packetData.channel.toString() else shortName,
                            longName = longName,
                            lastMessageTime = if (packetData.time != 0L) packetData.time else null,
                            lastMessageText = if (fromLocal) packetData.text else "$shortName: ${packetData.text}",
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
        safeLaunch(context = ioDispatcher, tag = "deleteContacts") { packetRepository.deleteContacts(contacts) }

    fun markAllAsRead() =
        safeLaunch(context = ioDispatcher, tag = "markAllAsRead") { packetRepository.clearAllUnreadCounts() }

    fun setMuteUntil(contacts: List<String>, until: Long) =
        safeLaunch(context = ioDispatcher, tag = "setMuteUntil") { packetRepository.setMuteUntil(contacts, until) }

    fun getContactSettings() = packetRepository.getContactSettings()

    fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) {
        safeLaunch(context = ioDispatcher, tag = "setContactFilteringDisabled") {
            packetRepository.setContactFilteringDisabled(contactKey, disabled)
        }
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

    private data class ContactsPagedParams(
        val myNodeNum: Int?,
        val channelSet: ChannelSet,
        val settings: Map<String, ContactSettings>,
        val myId: String?,
    )
}
