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
package org.meshtastic.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.meshtastic.proto.Position

@Entity(
    tableName = "traceroute_node_position",
    primaryKeys = ["log_uuid", "node_num"],
    foreignKeys =
    [
        ForeignKey(
            entity = MeshLog::class,
            parentColumns = ["uuid"],
            childColumns = ["log_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["log_uuid"]), Index(value = ["request_id"])],
)
data class TracerouteNodePositionEntity(
    @ColumnInfo(name = "log_uuid") val logUuid: String,
    @ColumnInfo(name = "request_id") val requestId: Int,
    @ColumnInfo(name = "node_num") val nodeNum: Int,
    @ColumnInfo(name = "position", typeAffinity = ColumnInfo.BLOB) val position: Position,
)
