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
package org.meshtastic.core.ui.util

/** Neighbor type classification for discovery map markers. */
enum class DiscoveryNeighborType {
    DIRECT,
    MESH,
}

/**
 * Platform-neutral representation of a discovered node for map rendering. Contains only the data needed to place and
 * style a marker — no Room entities or platform types leak into the map provider API.
 */
data class DiscoveryMapNode(
    val latitude: Double,
    val longitude: Double,
    val shortName: String?,
    val longName: String?,
    val neighborType: DiscoveryNeighborType,
    val snr: Float = 0f,
    val rssi: Int = 0,
    val messageCount: Int = 0,
    val sensorPacketCount: Int = 0,
) {
    /**
     * FR-011: Map icon classification. If environment packets > text messages, return true (sensor). Otherwise return
     * false (social/chat).
     */
    val isSensorNode: Boolean
        get() = sensorPacketCount > messageCount
}
