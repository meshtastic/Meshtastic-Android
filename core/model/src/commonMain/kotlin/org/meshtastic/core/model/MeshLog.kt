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
package org.meshtastic.core.model

import co.touchlab.kermit.Logger
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.Position

/**
 * Represents a log entry in shared repository/domain code.
 *
 * Logs are used for auditing radio traffic, telemetry history, and debugging.
 */
@Suppress("EmptyCatchBlock", "SwallowedException", "ConstructorParameterNaming")
data class MeshLog(
    val uuid: String,
    val message_type: String,
    val received_date: Long,
    val raw_message: String,
    val fromNum: Int = 0,
    val portNum: Int = 0,
    val fromRadio: FromRadio = FromRadio(),
) {
    val meshPacket = fromRadio.packet

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
            } ?: nodeInfo?.position

    companion object {
        /**
         * The node number used to represent the local node in the logs.
         *
         * Using 0 instead of the actual node number ensures log continuity even if the radio hardware or local ID
         * changes.
         */
        const val NODE_NUM_LOCAL = 0
    }
}
