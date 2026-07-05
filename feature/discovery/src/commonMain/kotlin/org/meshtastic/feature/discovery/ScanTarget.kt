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
package org.meshtastic.feature.discovery

import okio.ByteString
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig.RegionCode

/**
 * One entry in the discovery scan queue. A [channel] of `null` is a public-preset target (the original scan behavior —
 * dwell on [preset] using the radio's existing primary channel). A non-null [channel] is a beacon-advertised custom
 * channel: during its dwell the engine tunes the radio's primary channel to that name+PSK (and [region]) so nodes on
 * that mesh are heard, then restores the original primary channel afterwards (Apple 014-mesh-beacons FR-005).
 *
 * @param label Result label persisted with the dwell (e.g. `"LONG_FAST"` or `"LONG_FAST · PartyNet"`), so a custom
 *   channel's results never collide with the same preset's public results.
 */
data class ScanTarget(
    val preset: ChannelOption,
    val label: String,
    val channel: ChannelSettings? = null,
    val region: RegionCode? = null,
)

/**
 * A distinct custom channel a beacon advertised, presented as a selectable row in scan setup (FR-007). Deduped by [id]
 * (name + preset + PSK), since the same channel on the same preset is one row regardless of how many nodes beaconed it
 * — but a different PSK is a different network, so it must not collapse into another row (that would send the user to
 * the wrong mesh).
 */
data class BeaconChannel(val name: String, val psk: ByteString, val preset: ChannelOption?, val region: RegionCode) {
    val id: String
        get() = "$name|${preset?.name.orEmpty()}|${psk.hex()}"
}
