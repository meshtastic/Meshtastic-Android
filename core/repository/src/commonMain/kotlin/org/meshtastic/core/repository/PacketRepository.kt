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
package org.meshtastic.core.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.proto.ChannelSettings

/**
 * Repository interface for managing mesh packets and message history.
 *
 * This component provides methods for persisting received packets, querying message history, tracking unread counts,
 * and managing contact-specific settings. It supports both reactive (Flow) and one-shot (suspend) queries.
 */
@Suppress("TooManyFunctions")
interface PacketRepository {
    /** Reactive flow of all persisted waypoints (GPS locations). */
    fun getWaypoints(): Flow<List<DataPacket>>

    /** Reactive flow of all conversation contacts, keyed by their contact identifier. */
    fun getContacts(): Flow<Map<String, DataPacket>>

    /** Reactive paged flow of conversation contacts. */
    fun getContactsPaged(): Flow<PagingData<DataPacket>>

    /** Returns the total number of messages in a conversation. */
    suspend fun getMessageCount(contact: String): Int

    /** Returns the count of unread messages in a conversation. */
    suspend fun getUnreadCount(contact: String): Int

    /** Reactive flow of the unread message count in a conversation. */
    fun getUnreadCountFlow(contact: String): Flow<Int>

    /** Reactive flow of the UUID of the first unread message in a conversation. */
    fun getFirstUnreadMessageUuid(contact: String): Flow<Long?>

    /** Reactive flow indicating whether a conversation has any unread messages. */
    fun hasUnreadMessages(contact: String): Flow<Boolean>

    /** Reactive flow of the total unread message count across all conversations. */
    fun getUnreadCountTotal(): Flow<Int>

    /** Clears the unread status for messages in a conversation up to the given timestamp. */
    suspend fun clearUnreadCount(contact: String, timestamp: Long)

    /** Clears the unread status for all messages across all conversations. */
    suspend fun clearAllUnreadCounts()

    /** Updates the identifier of the last read message in a conversation. */
    suspend fun updateLastReadMessage(contact: String, messageUuid: Long, lastReadTimestamp: Long)

    /** Returns all packets currently queued for transmission. */
    suspend fun getQueuedPackets(): List<DataPacket>

    /**
     * Persists a packet in the database.
     *
     * @param myNodeNum The local node number at the time of receipt.
     * @param contactKey The identifier of the associated conversation.
     * @param packet The [DataPacket] to save.
     * @param receivedTime The timestamp (ms) the packet was received.
     * @param read Whether the packet should be marked as already read.
     * @param filtered Whether the packet was filtered by message rules.
     */
    suspend fun savePacket(
        myNodeNum: Int,
        contactKey: String,
        packet: DataPacket,
        receivedTime: Long,
        read: Boolean = true,
        filtered: Boolean = false,
    )

    /**
     * Returns a reactive flow of messages for a conversation.
     *
     * @param contact The conversation identifier.
     * @param limit Optional maximum number of messages to return.
     * @param includeFiltered Whether to include messages that were marked as filtered.
     * @param getNode Callback to fetch node info for message sender attribution.
     */
    suspend fun getMessagesFrom(
        contact: String,
        limit: Int? = null,
        includeFiltered: Boolean = true,
        getNode: suspend (String?) -> Node,
    ): Flow<List<Message>>

    /** Returns a paged flow of messages for a conversation. */
    fun getMessagesFromPaged(contact: String, getNode: suspend (String?) -> Node): Flow<PagingData<Message>>

    /** Returns a paged flow of messages for a conversation, with filtering options. */
    fun getMessagesFromPaged(
        contactKey: String,
        includeFiltered: Boolean,
        getNode: suspend (String?) -> Node,
    ): Flow<PagingData<Message>>

    /** Updates the transmission status of a packet. */
    suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus)

    /** Updates the identifier of a persisted packet. */
    suspend fun updateMessageId(d: DataPacket, id: Int)

    /** Deletes messages by their database UUIDs. */
    suspend fun deleteMessages(uuidList: List<Long>)

    /** Deletes all messages and settings for the given contacts. */
    suspend fun deleteContacts(contactList: List<String>)

    /** Deletes a waypoint by its ID. */
    suspend fun deleteWaypoint(id: Int)

    /** Reactive flow of all contact settings (e.g., mute status). */
    fun getContactSettings(): Flow<Map<String, ContactSettings>>

    /** Returns the settings for a specific contact. */
    suspend fun getContactSettings(contact: String): ContactSettings

    /** Mutes the given contacts until the specified timestamp. */
    suspend fun setMuteUntil(contacts: List<String>, until: Long)

    /** Reactive flow of the number of filtered messages for a contact. */
    fun getFilteredCountFlow(contactKey: String): Flow<Int>

    /** Returns the total count of filtered messages for a contact. */
    suspend fun getFilteredCount(contactKey: String): Int

    /** Disables or enables message filtering for a specific contact. */
    suspend fun setContactFilteringDisabled(contactKey: String, disabled: Boolean)

    /** Clears all packet and message history from the database. */
    suspend fun clearPacketDB()

    /** Migrates channel-specific message history when encryption keys change. */
    suspend fun migrateChannelsByPSK(oldSettings: List<ChannelSettings>, newSettings: List<ChannelSettings>)

    /** Marks all messages from a specific sender as filtered or unfiltered. */
    suspend fun updateFilteredBySender(senderId: String, filtered: Boolean)

    /** Returns a packet by its mesh-layer packet ID. */
    suspend fun getPacketByPacketId(packetId: Int): DataPacket?

    /** Returns a packet by its internal database ID. */
    suspend fun getPacketById(id: Int): DataPacket?

    /** Inserts a packet into the database. */
    suspend fun insert(
        packet: DataPacket,
        myNodeNum: Int,
        contactKey: String,
        receivedTime: Long,
        read: Boolean = true,
        filtered: Boolean = false,
    )

    /** Updates an existing packet in the database, optionally setting a routing error code. */
    suspend fun update(packet: DataPacket, routingError: Int = -1)

    /** Persists a message reaction (emoji). */
    suspend fun insertReaction(reaction: Reaction, myNodeNum: Int)

    /** Updates an existing reaction. */
    suspend fun updateReaction(reaction: Reaction)

    /** Returns a reaction associated with a specific packet ID. */
    suspend fun getReactionByPacketId(packetId: Int): Reaction?

    /** Finds all packets matching a specific packet ID. */
    suspend fun findPacketsWithId(packetId: Int): List<DataPacket>

    /** Finds all reactions associated with a specific packet ID. */
    suspend fun findReactionsWithId(packetId: Int): List<Reaction>

    /**
     * Updates the Store-and-Forward PlusPlus (SFPP) status for packets.
     *
     * @param packetId The packet ID.
     * @param from The sender node number.
     * @param to The recipient node number.
     * @param hash The SFPP commit hash.
     * @param status The new SFPP-specific message status.
     * @param rxTime The receipt time from the mesh.
     * @param myNodeNum The local node number.
     */
    suspend fun updateSFPPStatus(
        packetId: Int,
        from: Int,
        to: Int,
        hash: ByteArray,
        status: MessageStatus,
        rxTime: Long,
        myNodeNum: Int?,
    )

    /** Updates the SFPP status of packets matching the given commit hash. */
    suspend fun updateSFPPStatusByHash(hash: ByteArray, status: MessageStatus, rxTime: Long)
}
