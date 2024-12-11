/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos.User
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.util.getShortDateTime

data class PacketEntity(
    @Embedded val packet: Packet,
    @Relation(entity = ReactionEntity::class, parentColumn = "packet_id", entityColumn = "reply_id")
    val reactions: List<ReactionEntity> = emptyList(),
) {
    suspend fun toMessage(getNode: suspend (userId: String?) -> Node) = with(packet) {
        Message(
            uuid = uuid,
            receivedTime = received_time,
            node = getNode(data.from),
            text = data.text.orEmpty(),
            time = getShortDateTime(data.time),
            read = read,
            status = data.status,
            routingError = routingError,
            packetId = packetId,
            emojis = reactions.toReaction(getNode),
        )
    }
}

@Entity(
    tableName = "packet",
    indices = [
        Index(value = ["myNodeNum"]),
        Index(value = ["port_num"]),
        Index(value = ["contact_key"]),
    ]
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
)

@Entity(tableName = "contact_settings")
data class ContactSettings(
    @PrimaryKey val contact_key: String,
    val muteUntil: Long = 0L,
) {
    val isMuted get() = System.currentTimeMillis() <= muteUntil
}

data class Reaction(
    val replyId: Int,
    val user: User,
    val emoji: String,
    val timestamp: Long,
)

@Entity(
    tableName = "reactions",
    primaryKeys = ["reply_id", "user_id", "emoji"],
    indices = [
        Index(value = ["reply_id"]),
    ],
)
data class ReactionEntity(
    @ColumnInfo(name = "reply_id") val replyId: Int,
    @ColumnInfo(name = "user_id") val userId: String,
    val emoji: String,
    val timestamp: Long,
)

private suspend fun ReactionEntity.toReaction(
    getNode: suspend (userId: String?) -> Node
) = Reaction(
    replyId = replyId,
    user = getNode(userId).user,
    emoji = emoji,
    timestamp = timestamp,
)

private suspend fun List<ReactionEntity>.toReaction(
    getNode: suspend (userId: String?) -> Node
) = this.map { it.toReaction(getNode) }
