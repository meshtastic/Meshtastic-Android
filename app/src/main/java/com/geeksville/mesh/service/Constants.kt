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
package com.geeksville.mesh.service

const val PREFIX = "com.geeksville.mesh"

const val ACTION_NODE_CHANGE = "$PREFIX.NODE_CHANGE"
const val ACTION_MESH_CONNECTED = "$PREFIX.MESH_CONNECTED"
const val ACTION_MESH_DISCONNECTED = "$PREFIX.MESH_DISCONNECTED"
const val ACTION_CONNECTION_CHANGED = "$PREFIX.CONNECTION_CHANGED"
const val ACTION_MESSAGE_STATUS = "$PREFIX.MESSAGE_STATUS"

const val ACTION_RECEIVED_TEXT_MESSAGE_APP = "$PREFIX.RECEIVED.TEXT_MESSAGE_APP"
const val ACTION_RECEIVED_POSITION_APP = "$PREFIX.RECEIVED.POSITION_APP"
const val ACTION_RECEIVED_NODEINFO_APP = "$PREFIX.RECEIVED.NODEINFO_APP"
const val ACTION_RECEIVED_TELEMETRY_APP = "$PREFIX.RECEIVED.TELEMETRY_APP"
const val ACTION_RECEIVED_ATAK_PLUGIN = "$PREFIX.RECEIVED.ATAK_PLUGIN"
const val ACTION_RECEIVED_ATAK_FORWARDER = "$PREFIX.RECEIVED.ATAK_FORWARDER"
const val ACTION_RECEIVED_DETECTION_SENSOR_APP = "$PREFIX.RECEIVED.DETECTION_SENSOR_APP"
const val ACTION_RECEIVED_PRIVATE_APP = "$PREFIX.RECEIVED.PRIVATE_APP"

fun actionReceived(portNum: String) = "$PREFIX.RECEIVED.$portNum"

//
// standard EXTRA bundle definitions
//

// a bool true means now connected, false means not
const val EXTRA_CONNECTED = "$PREFIX.Connected"
const val EXTRA_PROGRESS = "$PREFIX.Progress"

// / a bool true means we expect this condition to continue until, false means device might come back
const val EXTRA_PERMANENT = "$PREFIX.Permanent"

const val EXTRA_PAYLOAD = "$PREFIX.Payload"
const val EXTRA_NODEINFO = "$PREFIX.NodeInfo"
const val EXTRA_PACKET_ID = "$PREFIX.PacketId"
const val EXTRA_STATUS = "$PREFIX.Status"
