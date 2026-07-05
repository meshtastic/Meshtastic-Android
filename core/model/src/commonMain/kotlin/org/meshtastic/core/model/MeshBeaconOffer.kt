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

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.MeshBeacon

/**
 * A received Mesh Beacon invitation — an advisory, zero-hop advertisement from another mesh offering a channel to join.
 * Beacons are unsigned and originate from nodes outside the local NodeDB, so this is deliberately not a
 * message/contact; it lives in the Discovery surface instead.
 *
 * @param fromNodeNum The node that broadcast the beacon (informational only — beacons are unsigned).
 * @param beacon The decoded advertisement, carrying the display [message][MeshBeacon.message] and the join offer.
 * @param snr Signal-to-noise ratio of the received beacon packet, in dB (0 when unknown).
 * @param rssi Received signal strength of the beacon packet, in dBm (0 when unknown).
 */
data class MeshBeaconOffer(val fromNodeNum: Int, val beacon: MeshBeacon, val snr: Float = 0f, val rssi: Int = 0) {
    /** Stable identity for dedup/dismiss: a given sender advertising a given channel is one standing invitation. */
    val key: String
        get() = "$fromNodeNum:${beacon.offer_channel?.name.orEmpty()}"

    val message: String
        get() = beacon.message

    val channelName: String?
        get() = beacon.offer_channel?.name?.ifBlank { null }

    /**
     * Serializes to a single-line record `fromNodeNum|snr|rssi|<base64 MeshBeacon>` for lightweight prefs persistence.
     * The base64 alphabet never contains `|`, so the delimiter is unambiguous.
     */
    fun encode(): String {
        val beaconB64 = MeshBeacon.ADAPTER.encode(beacon).toByteString().base64()
        return "$fromNodeNum|$snr|$rssi|$beaconB64"
    }

    companion object {
        private const val RECORD_FIELD_COUNT = 4

        /** Inverse of [encode]; returns null for a malformed or undecodable record. */
        @Suppress("ReturnCount")
        fun decode(record: String): MeshBeaconOffer? {
            val parts = record.split('|', limit = RECORD_FIELD_COUNT)
            if (parts.size != RECORD_FIELD_COUNT) return null
            val node = parts[0].toIntOrNull() ?: return null
            val beaconBytes = parts.last().decodeBase64()?.toByteArray() ?: return null
            val beacon = runCatching { MeshBeacon.ADAPTER.decode(beaconBytes) }.getOrNull() ?: return null
            return MeshBeaconOffer(node, beacon, parts[1].toFloatOrNull() ?: 0f, parts[2].toIntOrNull() ?: 0)
        }
    }
}
