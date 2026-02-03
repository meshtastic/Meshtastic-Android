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
import androidx.room.Index
import androidx.room.PrimaryKey
import co.touchlab.kermit.Logger
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.Position

@Suppress("EmptyCatchBlock", "SwallowedException", "ConstructorParameterNaming")
@Entity(tableName = "log", indices = [Index(value = ["from_num"]), Index(value = ["port_num"])])
data class MeshLog(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "type") val message_type: String,
    @ColumnInfo(name = "received_date") val received_date: Long,
    @ColumnInfo(name = "message") val raw_message: String,
    @ColumnInfo(name = "from_num", defaultValue = "0") val fromNum: Int = 0,
    @ColumnInfo(name = "port_num", defaultValue = "0") val portNum: Int = 0,
    @ColumnInfo(name = "from_radio", typeAffinity = ColumnInfo.BLOB, defaultValue = "x''")
    val fromRadio: FromRadio = FromRadio(),
) {

    val meshPacket: MeshPacket?
        get() = fromRadio.packet

    val nodeInfo: NodeInfo?
        get() = fromRadio.node_info

    val myNodeInfo: MyNodeInfo?
        get() = fromRadio.my_info

    val position: Position?
        get() =
            fromRadio.packet?.decoded?.payload?.let {
                if (fromRadio.packet?.decoded?.portnum == org.meshtastic.proto.PortNum.POSITION_APP) {
                    Position.ADAPTER.decodeOrNull(it, Logger)
                } else {
                    null
                }
            }
}
