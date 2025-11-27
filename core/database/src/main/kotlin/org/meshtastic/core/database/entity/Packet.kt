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

package org.meshtastic.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.getShortDateTime
import org.meshtastic.proto.MeshProtos.User

data class PacketEntity(
    @Embedded val packet: Packet,
    @Relation(entity = ReactionEntity::class, parentColumn = "packet_id", entityColumn = "reply_id")
    val reactions: List<ReactionEntity> = emptyList(),
) {
    suspend fun toMessage(getNode: suspend (userId: String?) -> Node) = with(packet) {
        val node = getNode(data.from)
        Message(
            uuid = uuid,
            receivedTime = received_time,
            node = node,
            fromLocal = node.user.id == DataPacket.ID_LOCAL,
            text = data.text.orEmpty(),
            time = getShortDateTime(data.time),
            snr = snr,
            rssi = rssi,
            hopsAway = hopsAway,
            read = read,
            status = data.status,
            routingError = routingError,
            packetId = packetId,
            emojis = reactions.toReaction(getNode),
            replyId = data.replyId,
            viaMqtt = data.viaMqtt,
            relayNode = data.relayNode,
            relays = data.relays,
        )
    }
}

@Suppress("ConstructorParameterNaming")
@Entity(
    tableName = "packet",
    indices = [Index(value = ["myNodeNum"]), Index(value = ["port_num"]), Index(value = ["contact_key"])],
)
data class Packet(
    @PrimaryKey(autoGenerate = true) val uuid: Long,
    @ColumnInfo(name = "myNodeNum", defaultValue = "0") val myNodeNum: Int,
    @ColumnInfo(name = "port_num") val port_num: Int,
    @ColumnInfo(name = "contact_key") val contact_key: String,
    @ColumnInfo(name = "received_time") val received_time: Long,
    @ColumnInfo(name = "read", defaultValue = "1") val read: Boolean,
    @ColumnInfo(name = "data") val data: DataPacket,
    @ColumnInfo(name = "packet_id", defaultValue = "0") val packetId: Int = 0,
    @ColumnInfo(name = "routing_error", defaultValue = "-1") var routingError: Int = -1,
    @ColumnInfo(name = "reply_id", defaultValue = "0") val replyId: Int = 0,
    @ColumnInfo(name = "snr", defaultValue = "0") val snr: Float = 0f,
    @ColumnInfo(name = "rssi", defaultValue = "0") val rssi: Int = 0,
    @ColumnInfo(name = "hopsAway", defaultValue = "-1") val hopsAway: Int = -1,
) {
    companion object {
        const val RELAY_NODE_SUFFIX_MASK = 0xFF

        fun getRelayNode(relayNodeId: Int, nodes: List<Node>): Node? {
            val relayNodeIdSuffix = relayNodeId and RELAY_NODE_SUFFIX_MASK
            val candidateRelayNodes = nodes.filter { (it.num and RELAY_NODE_SUFFIX_MASK) == relayNodeIdSuffix }
            val closestRelayNode =
                if (candidateRelayNodes.size == 1) {
                    candidateRelayNodes.first()
                } else {
                    candidateRelayNodes.minByOrNull { it.hopsAway }
                }
            return closestRelayNode
        }
    }
}

@Suppress("ConstructorParameterNaming")
@Entity(tableName = "contact_settings")
data class ContactSettings(
    @PrimaryKey val contact_key: String,
    val muteUntil: Long = 0L,
    @ColumnInfo(name = "last_read_message_uuid") val lastReadMessageUuid: Long? = null,
    @ColumnInfo(name = "last_read_message_timestamp") val lastReadMessageTimestamp: Long? = null,
) {
    val isMuted
        get() = System.currentTimeMillis() <= muteUntil
}

data class Reaction(val replyId: Int, val user: User, val emoji: String, val timestamp: Long)

@Entity(
    tableName = "reactions",
    primaryKeys = ["reply_id", "user_id", "emoji"],
    indices = [Index(value = ["reply_id"])],
)
data class ReactionEntity(
    @ColumnInfo(name = "reply_id") val replyId: Int,
    @ColumnInfo(name = "user_id") val userId: String,
    val emoji: String,
    val timestamp: Long,
)

private suspend fun ReactionEntity.toReaction(getNode: suspend (userId: String?) -> Node) =
    Reaction(replyId = replyId, user = getNode(userId).user, emoji = emoji, timestamp = timestamp)

private suspend fun List<ReactionEntity>.toReaction(getNode: suspend (userId: String?) -> Node) =
    this.map { it.toReaction(getNode) }
