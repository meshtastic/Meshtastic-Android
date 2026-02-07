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
const val ACTION_CONNECTION_CHANGED = "$PREFIX.CONNECTION_CHANGED"
const val ACTION_MESSAGE_STATUS = "$PREFIX.MESSAGE_STATUS"

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
