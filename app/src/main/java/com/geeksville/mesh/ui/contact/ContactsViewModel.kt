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

package com.geeksville.mesh.ui.contact

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.model.util.getShortDate
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.channelSet
import javax.inject.Inject
import kotlin.collections.map

@HiltViewModel
class ContactsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    serviceRepository: ServiceRepository,
) : ViewModel() {
    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = serviceRepository.connectionState

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = channelSet {})

    val contactList =
        combine(
            nodeRepository.myNodeInfo,
            packetRepository.getContacts(),
            channels,
            packetRepository.getContactSettings(),
        ) { myNodeInfo, contacts, channelSet, settings ->
            val myNodeNum = myNodeInfo?.myNodeNum ?: return@combine emptyList()
            // Add empty channel placeholders (always show Broadcast contacts, even when empty)
            val placeholder =
                (0 until channelSet.settingsCount).associate { ch ->
                    val contactKey = "$ch${DataPacket.ID_BROADCAST}"
                    val data = DataPacket(bytes = null, dataType = 1, time = 0L, channel = ch)
                    contactKey to Packet(0L, myNodeNum, 1, contactKey, 0L, true, data)
                }

            (contacts + (placeholder - contacts.keys)).values.map { packet ->
                val data = packet.data
                val contactKey = packet.contact_key

                // Determine if this is my message (originated on this device)
                val fromLocal = data.from == DataPacket.ID_LOCAL
                val toBroadcast = data.to == DataPacket.ID_BROADCAST

                // grab usernames from NodeInfo
                val user = getUser(if (fromLocal) data.to else data.from)
                val node = getNode(if (fromLocal) data.to else data.from)

                val shortName = user.shortName
                val longName =
                    if (toBroadcast) {
                        channelSet.getChannel(data.channel)?.name ?: context.getString(R.string.channel_name)
                    } else {
                        user.longName
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
                    isUnmessageable = user.isUnmessagable,
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

    fun getNode(userId: String?) = nodeRepository.getNode(userId ?: DataPacket.ID_BROADCAST)

    fun deleteContacts(contacts: List<String>) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.deleteContacts(contacts) }

    fun setMuteUntil(contacts: List<String>, until: Long) =
        viewModelScope.launch(Dispatchers.IO) { packetRepository.setMuteUntil(contacts, until) }

    private fun getUser(userId: String?) = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)
}
