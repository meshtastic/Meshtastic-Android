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

@Suppress("TooManyFunctions")
interface PacketRepository {
    fun getWaypoints(): Flow<List<DataPacket>>

    fun getContacts(): Flow<Map<String, DataPacket>>

    fun getContactsPaged(): Flow<PagingData<DataPacket>>

    suspend fun getMessageCount(contact: String): Int

    suspend fun getUnreadCount(contact: String): Int

    fun getFirstUnreadMessageUuid(contact: String): Flow<Long?>

    fun hasUnreadMessages(contact: String): Flow<Boolean>

    fun getUnreadCountTotal(): Flow<Int>

    suspend fun clearUnreadCount(contact: String, timestamp: Long)

    suspend fun updateLastReadMessage(contact: String, messageUuid: Long, lastReadTimestamp: Long)

    suspend fun getQueuedPackets(): List<DataPacket>?

    suspend fun savePacket(
        myNodeNum: Int,
        contactKey: String,
        packet: DataPacket,
        receivedTime: Long,
        read: Boolean = true,
        filtered: Boolean = false,
    )

    suspend fun getMessagesFrom(
        contact: String,
        limit: Int? = null,
        includeFiltered: Boolean = true,
        getNode: suspend (String?) -> Node,
    ): Flow<List<Message>>

    fun getMessagesFromPaged(contact: String, getNode: suspend (String?) -> Node): Flow<PagingData<Message>>

    fun getMessagesFromPaged(
        contactKey: String,
        includeFiltered: Boolean,
        getNode: suspend (String?) -> Node,
    ): Flow<PagingData<Message>>

    suspend fun updateMessageStatus(d: DataPacket, m: MessageStatus)

    suspend fun updateMessageId(d: DataPacket, id: Int)

    suspend fun deleteMessages(uuidList: List<Long>)

    suspend fun deleteContacts(contactList: List<String>)

    suspend fun deleteWaypoint(id: Int)

    fun getContactSettings(): Flow<Map<String, ContactSettings>>

    suspend fun getContactSettings(contact: String): ContactSettings

    suspend fun setMuteUntil(contacts: List<String>, until: Long)

    fun getFilteredCountFlow(contactKey: String): Flow<Int>

    suspend fun getFilteredCount(contactKey: String): Int

    suspend fun setContactFilteringDisabled(contactKey: String, disabled: Boolean)

    suspend fun clearPacketDB()

    suspend fun migrateChannelsByPSK(oldSettings: List<ChannelSettings>, newSettings: List<ChannelSettings>)

    suspend fun updateFilteredBySender(senderId: String, filtered: Boolean)

    suspend fun getPacketByPacketId(packetId: Int): DataPacket?

    suspend fun getPacketById(id: Int): DataPacket?

    suspend fun insert(
        packet: DataPacket,
        myNodeNum: Int,
        contactKey: String,
        receivedTime: Long,
        read: Boolean = true,
        filtered: Boolean = false,
    )

    suspend fun update(packet: DataPacket)

    suspend fun insertReaction(reaction: Reaction, myNodeNum: Int)

    suspend fun updateReaction(reaction: Reaction)

    suspend fun getReactionByPacketId(packetId: Int): Reaction?

    suspend fun findPacketsWithId(packetId: Int): List<DataPacket>

    suspend fun findReactionsWithId(packetId: Int): List<Reaction>

    suspend fun updateSFPPStatus(
        packetId: Int,
        from: Int,
        to: Int,
        hash: ByteArray,
        status: MessageStatus,
        rxTime: Long,
        myNodeNum: Int?,
    )

    suspend fun updateSFPPStatusByHash(hash: ByteArray, status: MessageStatus, rxTime: Long)
}
