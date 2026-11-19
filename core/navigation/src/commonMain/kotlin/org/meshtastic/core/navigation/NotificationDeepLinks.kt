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
package org.meshtastic.core.navigation

/**
 * The destination each notification opens, as a path under [DEEP_LINK_BASE_URI] that [DeepLinkRouter] routes. Every
 * platform's notifications take their click target from here, so a notification opens the same screen everywhere.
 */
object NotificationDeepLinks {
    /** Messages, reactions and alerts open their conversation. */
    fun conversation(contactKey: String): String = "messages/$contactKey"

    /** New-node and low-battery notifications open the node. */
    fun nodeDetail(nodeNum: Int): String = "nodes/$nodeNum"

    fun waypoint(waypointId: Int): String = "map?waypointId=$waypointId"

    const val MESH_BEACON: String = "discovery"

    const val FIRMWARE_UPDATE: String = "firmware/update"

    /** The connection-state and reconnect notices open the connections screen. */
    const val CONNECTIONS: String = "connections"

    /** The full URI for [path]. */
    fun uri(path: String): String = "$DEEP_LINK_BASE_URI/$path"
}
