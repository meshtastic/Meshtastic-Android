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
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.FromRadio
import com.geeksville.mesh.Portnums
import com.google.protobuf.TextFormat
import java.io.IOException

@Entity(
    tableName = "log",
    indices = [
        Index(value = ["from_num"]),
        Index(value = ["port_num"]),
    ],
)
data class MeshLog(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "type") val message_type: String,
    @ColumnInfo(name = "received_date") val received_date: Long,
    @ColumnInfo(name = "message") val raw_message: String,
    @ColumnInfo(name = "from_num", defaultValue = "0") val fromNum: Int = 0,
    @ColumnInfo(name = "port_num", defaultValue = "0") val portNum: Int = 0,
    @ColumnInfo(name = "from_radio", typeAffinity = ColumnInfo.BLOB, defaultValue = "x''")
    val fromRadio: FromRadio = FromRadio.getDefaultInstance(),
) {

    val meshPacket: MeshProtos.MeshPacket?
        get() {
            if (message_type == "Packet") {
                val builder = MeshProtos.MeshPacket.newBuilder()
                try {
                    TextFormat.getParser().merge(raw_message, builder)
                    return builder.build()
                } catch (e: IOException) {
                }
            }
            return null
        }

    val nodeInfo: MeshProtos.NodeInfo?
        get() {
            if (message_type == "NodeInfo") {
                val builder = MeshProtos.NodeInfo.newBuilder()
                try {
                    TextFormat.getParser().merge(raw_message, builder)
                    return builder.build()
                } catch (e: IOException) {
                }
            }
            return null
        }

    val myNodeInfo: MeshProtos.MyNodeInfo?
        get() {
            if (message_type == "MyNodeInfo") {
                val builder = MeshProtos.MyNodeInfo.newBuilder()
                try {
                    TextFormat.getParser().merge(raw_message, builder)
                    return builder.build()
                } catch (e: IOException) {
                }
            }
            return null
        }

    val position: MeshProtos.Position?
        get() {
            return meshPacket?.run {
                if (hasDecoded() && decoded.portnumValue == Portnums.PortNum.POSITION_APP_VALUE) {
                    return MeshProtos.Position.parseFrom(decoded.payload)
                }
                return null
            } ?: nodeInfo?.position
        }
}
