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

/** Interface for processing incoming radio messages and mesh packets. */
interface MeshMessageProcessor {
    /**
     * Handles [frame], a received radio message together with the immutable transport session that admitted it.
     *
     * @param myNodeNum local node number, or `null` while the current handshake has not resolved it
     */
    suspend fun handleFromRadio(frame: ReceivedRadioFrame, myNodeNum: Int?)

    /** Clears buffered packets before a device boundary is published. */
    suspend fun clearEarlyPackets()
}
