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
package org.meshtastic.feature.auto

import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User

/**
 * Pure-function helpers that convert domain models into [CarContact] and display strings for
 * [MeshtasticCarScreen].
 *
 * All methods are free of Car App Library dependencies, making them straightforwardly testable as
 * plain JVM unit tests without Robolectric.
 */
internal object CarScreenDataBuilder {

    /**
     * Returns a map of `"<ch>^all" → placeholder DataPacket` for every configured channel.
     *
     * Channel placeholders ensure every configured channel is always visible in the Messages
     * tab — even before any messages have been sent or received — mirroring the behaviour of
     * `ContactsViewModel.contactList`.
     */
    fun buildChannelPlaceholders(channelSet: ChannelSet): Map<String, DataPacket> =
        (0 until channelSet.settings.size).associate { ch ->
            "${ch}${DataPacket.ID_BROADCAST}" to
                DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value, time = 0L, channel = ch)
        }

    /**
     * Converts the merged DB + placeholder map into an ordered [CarContact] list.
     *
     * Channels (keys ending with [DataPacket.ID_BROADCAST]) appear first sorted by channel index.
     * DM conversations follow sorted by [CarContact.lastMessageTime] descending — matching the
     * ordering used by the phone's Contacts screen.
     *
     * @param resolveUser Returns the [User] for a given node-ID string. The caller is responsible
     *   for providing a null-safe fallback (typically [NodeRepository.getUser]).
     * @param channelLabel Produces the display name for a channel given its index.
     *   Defaults to `"Channel N"`; callers can supply a localised string.
     * @param unknownLabel Fallback display name when neither long name nor short name is available.
     *   Defaults to `"Unknown"`; callers can supply a localised string.
     */
    fun buildCarContacts(
        merged: Map<String, DataPacket>,
        myId: String?,
        channelSet: ChannelSet,
        resolveUser: (String) -> User,
        channelLabel: (Int) -> String = { "Channel $it" },
        unknownLabel: String = "Unknown",
    ): List<CarContact> {
        val all = merged.map { (contactKey, packet) ->
            val fromLocal = packet.from == DataPacket.ID_LOCAL || packet.from == myId
            val toBroadcast = packet.to == DataPacket.ID_BROADCAST
            val userId = if (fromLocal) packet.to else packet.from

            // Resolve the user once; used for both displayName and message prefix.
            val user = resolveUser(userId ?: DataPacket.ID_BROADCAST)

            val displayName = if (toBroadcast) {
                channelSet.getChannel(packet.channel)?.name?.takeIf { it.isNotEmpty() }
                    ?: channelLabel(packet.channel)
            } else {
                // userId can be null for malformed packets (e.g. both `from` and `to` are null).
                // Fall back to a broadcast lookup which returns an "Unknown" user rather than crashing.
                user.long_name.ifEmpty { user.short_name }.ifEmpty { unknownLabel }
            }

            // Mirror ContactsViewModel: prefix received DM text with the sender's short name,
            // matching how ContactItem's ChatMetadata renders lastMessageText.
            val shortName = if (!toBroadcast) user.short_name else ""
            val lastMessageText = packet.text?.let { text ->
                if (fromLocal || shortName.isEmpty()) text else "$shortName: $text"
            }

            CarContact(
                contactKey = contactKey,
                displayName = displayName,
                unreadCount = 0, // filled in reactively by the screen's flatMapLatest
                isBroadcast = toBroadcast,
                channelIndex = packet.channel,
                lastMessageTime = if (packet.time != 0L) packet.time else null,
                lastMessageText = lastMessageText,
            )
        }

        // partition avoids iterating the list twice.
        val (channels, dms) = all.partition { it.isBroadcast }
        return channels.sortedBy { it.channelIndex } +
            dms.sortedByDescending { it.lastMessageTime ?: 0L }
    }

    /**
     * Filters and sorts [nodes] to produce the Favorites tab list.
     *
     * Only nodes with [Node.isFavorite] are included. They are sorted alphabetically by
     * [User.long_name], falling back to [User.short_name] when the long name is empty —
     * matching the alphabetical sort used by the phone's node list when filtered to favorites.
     */
    fun sortFavorites(nodes: Collection<Node>): List<Node> =
        nodes
            .filter { it.isFavorite }
            .sortedWith(compareBy { it.user.long_name.ifEmpty { it.user.short_name } })

    /**
     * Returns the primary status line for a favorite-node row (Text 1 in the Car UI row).
     *
     * Mirrors NodeItem's signal row:
     * - `"Online · Direct"` when [Node.hopsAway] == 0
     * - `"Online · N hops"` when [Node.hopsAway] > 0
     * - `"Online"` when hop distance is unknown
     * - `"Offline · <time ago>"` when [Node.lastHeard] is set
     * - `"Offline"` otherwise
     *
     * @param labelOnline  Localised "Online" label; defaults to English.
     * @param labelOffline Localised "Offline" label; defaults to English.
     * @param labelDirect  Suffix appended when [Node.hopsAway] == 0 (include leading " · ");
     *   defaults to `" · Direct"`.
     * @param labelHops    Produces the hop-count suffix given the count (include leading " · ");
     *   defaults to `" · N hops"`.
     * @param formatRelativeTime Converts a millis timestamp to a human-readable "X ago" string.
     *   Defaults to [DateFormatter.formatRelativeTime]; injectable for testing.
     */
    fun nodeStatusText(
        node: Node,
        labelOnline: String = "Online",
        labelOffline: String = "Offline",
        labelDirect: String = " · Direct",
        labelHops: (Int) -> String = { " · $it hops" },
        formatRelativeTime: (Long) -> String = DateFormatter::formatRelativeTime,
    ): String = buildString {
        if (node.isOnline) {
            append(labelOnline)
            when {
                node.hopsAway == 0 -> append(labelDirect)
                node.hopsAway > 0 -> append(labelHops(node.hopsAway))
            }
        } else {
            append(labelOffline)
            if (node.lastHeard > 0) {
                append(" · ${formatRelativeTime(node.lastHeard * 1000L)}")
            }
        }
    }

    /**
     * Returns the secondary detail line for a favorite-node row (Text 2 in the Car UI row).
     *
     * Mirrors NodeItem's battery row + node chip: `"NODE · 85%"`.
     * Returns an empty string when neither short name nor battery level is available.
     */
    fun nodeDetailText(node: Node): String = buildString {
        val shortName = node.user.short_name
        if (shortName.isNotEmpty()) append(shortName)
        val battery = node.batteryStr
        if (battery.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(battery)
        }
    }

    /**
     * Returns the message preview line for a contact row (Text 1 in the Car UI row).
     *
     * Mirrors `ChatMetadata`'s `lastMessageText` display: shows the last message text
     * (with sender prefix for received DMs), or [noMessagesLabel] for empty channels.
     *
     * @param noMessagesLabel Localised empty-state label; defaults to `"No messages yet"`.
     */
    fun contactPreviewText(
        contact: CarContact,
        noMessagesLabel: String = "No messages yet",
    ): String = contact.lastMessageText?.takeIf { it.isNotEmpty() } ?: noMessagesLabel

    /**
     * Returns the secondary metadata line for a contact row (Text 2 in the Car UI row).
     *
     * Mirrors ContactItem's unread badge + date header:
     * - [unreadLabel] result when there are unread messages
     * - Formatted short date of the last message otherwise
     * - Empty string when there are no messages at all
     *
     * @param unreadLabel  Produces the unread-count label given the count.
     *   Defaults to `"N unread"`; callers can supply a localised format string.
     * @param formatShortDate Converts a millis timestamp to a short date string.
     *   Defaults to [DateFormatter.formatShortDate]; injectable for testing.
     */
    fun contactSecondaryText(
        contact: CarContact,
        unreadLabel: (Int) -> String = { "$it unread" },
        formatShortDate: (Long) -> String = DateFormatter::formatShortDate,
    ): String = when {
        contact.unreadCount > 0 -> unreadLabel(contact.unreadCount)
        contact.lastMessageTime != null -> formatShortDate(contact.lastMessageTime)
        else -> ""
    }
}
