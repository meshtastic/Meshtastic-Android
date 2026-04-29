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

import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshPacket

/**
 * Interface for collecting packets during an active discovery scan. The scan engine implements this interface and
 * registers/unregisters with the packet handler to receive packets during dwell windows.
 */
interface DiscoveryPacketCollector {
    /** Whether this collector is currently active (scan in progress). */
    val isActive: Boolean

    /**
     * Called when a mesh packet is received during an active scan. Implementations should classify and aggregate the
     * packet data.
     *
     * @param meshPacket The raw mesh packet from the radio
     * @param dataPacket The decoded data packet with routing info
     */
    suspend fun onPacketReceived(meshPacket: MeshPacket, dataPacket: DataPacket)
}
