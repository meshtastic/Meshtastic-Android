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
package org.meshtastic.core.model

import org.meshtastic.proto.MeshBeacon

/**
 * A received Mesh Beacon invitation — an advisory, zero-hop advertisement from another mesh offering a channel to join.
 * Beacons are unsigned and originate from nodes outside the local NodeDB, so this is deliberately not a
 * message/contact; it lives in the Discovery surface instead.
 *
 * @param fromNodeNum The node that broadcast the beacon (informational only — beacons are unsigned).
 * @param beacon The decoded advertisement, carrying the display [message][MeshBeacon.message] and the join offer.
 */
data class MeshBeaconOffer(val fromNodeNum: Int, val beacon: MeshBeacon) {
    /** Stable identity for dedup/dismiss: a given sender advertising a given channel is one standing invitation. */
    val key: String
        get() = "$fromNodeNum:${beacon.offer_channel?.name.orEmpty()}"

    val message: String
        get() = beacon.message

    val channelName: String?
        get() = beacon.offer_channel?.name?.ifBlank { null }
}
