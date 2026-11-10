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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.StateFlow

/** What the user-started Live Update shows. One mode at a time; [Off] leaves the service notification as it is. */
sealed interface LiveUpdateMode {
    data object Off : LiveUpdateMode

    /** Connection, nodes online, unread messages and the connected radio's battery. */
    data object MeshStatus : LiveUpdateMode

    /** One node: its battery, signal, when it was last heard, hops and distance. */
    data class TrackNode(val nodeNum: Int) : LiveUpdateMode

    companion object {
        private const val OFF = "off"
        private const val MESH_STATUS = "mesh"
        private const val TRACK_NODE_PREFIX = "node:"

        fun encode(mode: LiveUpdateMode): String = when (mode) {
            Off -> OFF
            MeshStatus -> MESH_STATUS
            is TrackNode -> TRACK_NODE_PREFIX + mode.nodeNum
        }

        /** Anything unreadable decodes to [Off], so a corrupt value can never leave a promoted notification up. */
        fun decode(value: String?): LiveUpdateMode = when {
            value == MESH_STATUS -> MeshStatus

            value?.startsWith(TRACK_NODE_PREFIX) == true ->
                value.removePrefix(TRACK_NODE_PREFIX).toIntOrNull()?.let(::TrackNode) ?: Off

            else -> Off
        }
    }
}

/** The persisted [LiveUpdateMode]. Off by default. */
interface LiveUpdatePrefs {
    val mode: StateFlow<LiveUpdateMode>

    fun setMode(mode: LiveUpdateMode)
}
