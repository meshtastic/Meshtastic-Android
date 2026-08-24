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
package org.meshtastic.core.data.manager

import org.meshtastic.proto.Data
import org.meshtastic.proto.PortNum

/** Port numbers whose ordinary data packets are persisted by [MeshDataHandlerImpl.rememberDataPacket]. */
internal val PERSISTED_DATA_PORT_NUMBERS =
    setOf(
        PortNum.TEXT_MESSAGE_APP.value,
        PortNum.ALERT_APP.value,
        PortNum.WAYPOINT_APP.value,
        PortNum.NODE_STATUS_APP.value,
    )

/** A text-app payload that acknowledges another packet with an emoji reaction. */
internal fun Data.isReaction(): Boolean = portnum == PortNum.TEXT_MESSAGE_APP && reply_id != 0 && emoji != 0
