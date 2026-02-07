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
package org.meshtastic.core.api

import org.meshtastic.core.api.MeshtasticIntent.EXTRA_CONNECTED
import org.meshtastic.core.api.MeshtasticIntent.EXTRA_NODEINFO
import org.meshtastic.core.api.MeshtasticIntent.EXTRA_PACKET_ID
import org.meshtastic.core.api.MeshtasticIntent.EXTRA_STATUS

/**
 * Constants for Meshtastic Android Intents. These are used by external applications to communicate with the Meshtastic
 * service.
 */
object MeshtasticIntent {
    private const val PREFIX = "com.geeksville.mesh"

    /** Broadcast when a node's information changes. Extra: [EXTRA_NODEINFO] */
    const val ACTION_NODE_CHANGE = "$PREFIX.NODE_CHANGE"

    /** Broadcast when the mesh radio connects. Extra: [EXTRA_CONNECTED] */
    const val ACTION_MESH_CONNECTED = "$PREFIX.MESH_CONNECTED"

    /** Broadcast when the mesh radio disconnects. */
    const val ACTION_MESH_DISCONNECTED = "$PREFIX.MESH_DISCONNECTED"

    /** Legacy broadcast for connection changes. Extra: [EXTRA_CONNECTED] */
    const val ACTION_CONNECTION_CHANGED = "$PREFIX.CONNECTION_CHANGED"

    /** Broadcast for message status updates. Extras: [EXTRA_PACKET_ID], [EXTRA_STATUS] */
    const val ACTION_MESSAGE_STATUS = "$PREFIX.MESSAGE_STATUS"

    /** Received a text message. */
    const val ACTION_RECEIVED_TEXT_MESSAGE_APP = "$PREFIX.RECEIVED.TEXT_MESSAGE_APP"

    /** Received a position update. */
    const val ACTION_RECEIVED_POSITION_APP = "$PREFIX.RECEIVED.POSITION_APP"

    /** Received node info. */
    const val ACTION_RECEIVED_NODEINFO_APP = "$PREFIX.RECEIVED.NODEINFO_APP"

    /** Received telemetry data. */
    const val ACTION_RECEIVED_TELEMETRY_APP = "$PREFIX.RECEIVED.TELEMETRY_APP"

    /** Received ATAK Plugin data. */
    const val ACTION_RECEIVED_ATAK_PLUGIN = "$PREFIX.RECEIVED.ATAK_PLUGIN"

    /** Received ATAK Forwarder data. */
    const val ACTION_RECEIVED_ATAK_FORWARDER = "$PREFIX.RECEIVED.ATAK_FORWARDER"

    /** Received detection sensor data. */
    const val ACTION_RECEIVED_DETECTION_SENSOR_APP = "$PREFIX.RECEIVED.DETECTION_SENSOR_APP"

    /** Received private app data. */
    const val ACTION_RECEIVED_PRIVATE_APP = "$PREFIX.RECEIVED.PRIVATE_APP"

    // standard EXTRA bundle definitions
    const val EXTRA_CONNECTED = "$PREFIX.Connected"
    const val EXTRA_PAYLOAD = "$PREFIX.Payload"
    const val EXTRA_NODEINFO = "$PREFIX.NodeInfo"
    const val EXTRA_PACKET_ID = "$PREFIX.PacketId"
    const val EXTRA_STATUS = "$PREFIX.Status"
}
