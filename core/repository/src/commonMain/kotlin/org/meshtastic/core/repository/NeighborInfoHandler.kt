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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import org.meshtastic.proto.MeshPacket

/** Interface for handling neighbor info responses from the mesh. */
interface NeighborInfoHandler {
    /** Starts the neighbor info handler with the given coroutine scope. */
    fun start(scope: CoroutineScope)

    /**
     * Processes a neighbor info packet.
     *
     * @param packet The received mesh packet.
     */
    fun handleNeighborInfo(packet: MeshPacket)
}
