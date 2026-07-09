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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.Contact
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.isBroadcast
import org.meshtastic.core.model.isFromLocal
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import kotlin.collections.map as collectionsMap

@KoinViewModel
class ContactsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    connectionStateProvider: ConnectionStateProvider,
) : ViewModel() {
    val ourNodeInfo = nodeRepository.ourNodeInfo

    // ponytail: stored as a comma-joined String (not a Set) — savedstate on all KMP targets only
    // guarantees primitive types, and there are only ever two section keys.
    private val collapsedSectionsCsv = savedStateHandle.getStateFlow(KEY_COLLAPSED_SECTIONS, "")

    /** Collapsed conversation-list section keys (see [ContactSection]), persisted across process death. */
    val collapsedSections: StateFlow<Set<String>> =
        collapsedSectionsCsv.map { it.toSectionSet() }.stateInWhileSubscribed(initialValue = emptySet())

    fun toggleSectionCollapse(sectionKey: String) {
        // Read the authoritative saved value, not collapsedSections.value (which is emptySet when unsubscribed).
        val current = collapsedSectionsCsv.value.toSectionSet()
        val updated = if (sectionKey in current) current - sectionKey else current + sectionKey
        savedStateHandle[KEY_COLLAPSED_SECTIONS] = updated.joinToString(",")
    }

    val connectionState = connectionStateProvider.connectionState

    val unreadCountTotal = packetRepository.getUnreadCountTotal().stateInWhileSubscribed(0)

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    // Combine node info and myId to reduce argument count in subsequent combines
    private val identityFlow: Flow<Pair<MyNodeInfo?, String?>> =
        combine(nodeRepository.myNodeInfo, nodeRepository.myId) { info, id -> Pair(info, id) }

    /**
     * Flat contact list (channels + DMs) with empty-channel placeholders merged in, consumed by both ContactsScreen and
     * ShareScreen. ContactsScreen groups it into collapsible Channels/DirectMessages sections at render time.
     *
     * ponytail: not paged. Contact counts on a mesh are bounded (channels ≤ 8, DMs = nodes you've messaged), and a
     * LazyColumn only composes visible rows regardless. If a mesh ever grows large enough that recomputing per-row
     * unread/message counts on every emission hurts, restore paging via [PacketRepository.getContactsPaged] and inject
     * the two section headers with PagingData.insertSeparators over a query ordered channels-first.
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
                    val contactKey = ContactKey.broadcast(ch).value
                    val data = DataPacket(bytes = null, dataType = 1, time = 0L, channel = ch)
                    contactKey to data
                }

            (contacts + (placeholder - contacts.keys)).entries.collectionsMap { entry ->
                val contactKey = entry.key
                val packetData = entry.value
                // Determine if this is my message (originated on this device)
                val fromLocal = packetData.isFromLocal(myNodeNum)
                val toBroadcast = packetData.isBroadcast

                // grab usernames from NodeInfo
                val userId = if (fromLocal) packetData.to else packetData.from
                val user = nodeRepository.getUser(userId ?: NodeAddress.ID_BROADCAST)
                val node = nodeRepository.getNode(userId ?: NodeAddress.ID_BROADCAST)

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

    fun getNode(userId: String?) = nodeRepository.getNode(userId ?: NodeAddress.ID_BROADCAST)

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
     * a contact isn't currently loaded in the list.
     */
    suspend fun getTotalMessageCount(contactKeys: List<String>): Int = if (contactKeys.isEmpty()) {
        0
    } else {
        contactKeys.sumOf { contactKey -> packetRepository.getMessageCount(contactKey) }
    }

    companion object {
        private const val KEY_COLLAPSED_SECTIONS = "collapsed_contact_sections"
    }
}

private fun String.toSectionSet(): Set<String> = split(",").filter { it.isNotEmpty() }.toSet()

/** The two conversation-list sections a [Contact] is grouped into. */
enum class ContactSection(val key: String) {
    CHANNELS("channels"),
    DIRECT_MESSAGES("direct_messages"),
}

/** Channel/broadcast contact keys carry a leading channel-digit prefix (e.g. `"0^all"`); DM keys don't. */
fun Contact.section(): ContactSection =
    if (contactKey.getOrNull(1) == '^' || contactKey.endsWith("^all") || contactKey.endsWith("^broadcast")) {
        ContactSection.CHANNELS
    } else {
        ContactSection.DIRECT_MESSAGES
    }
