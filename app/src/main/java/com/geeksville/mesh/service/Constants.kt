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

package com.geeksville.mesh.service

const val prefix = "com.geeksville.mesh"


//
// standard EXTRA bundle definitions
//

// a bool true means now connected, false means not
const val EXTRA_CONNECTED = "$prefix.Connected"
const val EXTRA_PROGRESS = "$prefix.Progress"

/// a bool true means we expect this condition to continue until, false means device might come back
const val EXTRA_PERMANENT = "$prefix.Permanent"

const val EXTRA_PAYLOAD = "$prefix.Payload"
const val EXTRA_NODEINFO = "$prefix.NodeInfo"
const val EXTRA_PACKET_ID = "$prefix.PacketId"
const val EXTRA_STATUS = "$prefix.Status"
