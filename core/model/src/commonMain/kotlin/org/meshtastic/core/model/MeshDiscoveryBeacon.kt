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
@file:Suppress("MagicNumber")

package org.meshtastic.core.model

import okio.ByteString
import org.meshtastic.proto.PortNum

/**
 * Compact app-side representation of the config discovery beacon proposed in meshtastic/firmware#7183 and
 * meshtastic/firmware#10243.
 *
 * The firmware-side protocol is still under discussion, so this parser is intentionally conservative and side-effect
 * free. It only accepts a tiny fixed-width payload and never applies radio settings automatically.
 */
data class MeshDiscoveryBeacon(
    val version: Int,
    val roleHint: RoleHint,
    val forwardingHint: ForwardingHint,
    val frequencyKHz: Int,
    val bandwidth: Bandwidth,
    val spreadingFactor: Int,
    val codingRate: Int,
    val nodeId: Int,
    val primaryChannelHash: Int,
    val primaryChannelName: String,
) {
    val nodeIdString: String
        get() = DataPacket.nodeNumToDefaultId(nodeId)

    val frequencyMHz: Float
        get() = frequencyKHz / KHZ_PER_MHZ

    fun toDebugString(): String = buildString {
        appendLine("MeshDiscoveryBeacon:")
        appendLine("  version: $version")
        appendLine("  role_hint: $roleHint")
        appendLine("  forwarding_hint: $forwardingHint")
        appendLine("  frequency_mhz: $frequencyMHz")
        appendLine("  bandwidth: ${bandwidth.label}")
        appendLine("  spreading_factor: $spreadingFactor")
        appendLine("  coding_rate: 4/$codingRate")
        appendLine("  node_id: $nodeIdString")
        appendLine("  primary_channel_hash: $primaryChannelHash")
        appendLine("  primary_channel_name: $primaryChannelName")
    }

    enum class RoleHint {
        MIGHT_FORWARD,
        WILL_FORWARD,
        WILL_NOT_FORWARD,
        UNKNOWN,
    }

    enum class ForwardingHint {
        ALL,
        CORE,
        KNOWN,
        NONE,
    }

    enum class Bandwidth(val label: String) {
        BW_31("31.25 kHz"),
        BW_62("62.5 kHz"),
        BW_125("125 kHz"),
        BW_250("250 kHz"),
        BW_500("500 kHz"),
        BW_812("812.5 kHz"),
        BW_1625("1625 kHz"),
        UNKNOWN("Unknown"),
    }

    companion object {
        const val ENCODED_SIZE = 22
        private const val KHZ_PER_MHZ = 1000f
        private const val MAX_VERSION = 0
        private const val MIN_FREQUENCY_KHZ = 400_000
        private const val MAX_FREQUENCY_KHZ = 2_500_000
        private const val CHANNEL_NAME_BYTES = 12
        private const val MIN_SPREADING_FACTOR = 5
        private const val MIN_CODING_RATE = 5

        fun decode(portnumValue: Int, payload: ByteString): MeshDiscoveryBeacon? {
            if (!isCandidatePort(portnumValue)) return null
            return decode(payload)
        }

        fun isCandidatePort(portnumValue: Int): Boolean =
            portnumValue == PortNum.PRIVATE_APP.value || portnumValue == PortNum.UNKNOWN_APP.value

        fun decode(payload: ByteString): MeshDiscoveryBeacon? {
            if (payload.size != ENCODED_SIZE) return null
            val bytes = payload.toByteArray()

            val header = bytes[0].unsigned
            val version = header shr 6
            val reserved = (header shr 4) and 0x03
            if (version > MAX_VERSION || reserved != 0) return null

            val frequencyKHz = bytes.uint24At(1)
            if (frequencyKHz !in MIN_FREQUENCY_KHZ..MAX_FREQUENCY_KHZ) return null

            val radio = bytes[4].unsigned
            val bandwidth = Bandwidth.entries.getOrNull((radio shr 5) and 0x07) ?: Bandwidth.UNKNOWN
            if (bandwidth == Bandwidth.UNKNOWN) return null
            val spreadingFactor = ((radio shr 2) and 0x07) + MIN_SPREADING_FACTOR
            val codingRate = (radio and 0x03) + MIN_CODING_RATE

            val channelName = bytes.decodeChannelName()
            if (channelName == null) return null

            return MeshDiscoveryBeacon(
                version = version,
                roleHint = RoleHint.entries[(header shr 2) and 0x03],
                forwardingHint = ForwardingHint.entries[header and 0x03],
                frequencyKHz = frequencyKHz,
                bandwidth = bandwidth,
                spreadingFactor = spreadingFactor,
                codingRate = codingRate,
                nodeId = bytes.int32At(5),
                primaryChannelHash = bytes[9].unsigned,
                primaryChannelName = channelName,
            )
        }

        private val Byte.unsigned: Int
            get() = toInt() and 0xff

        private fun ByteArray.uint24At(offset: Int): Int =
            (this[offset].unsigned shl 16) or (this[offset + 1].unsigned shl 8) or this[offset + 2].unsigned

        private fun ByteArray.int32At(offset: Int): Int = (this[offset].unsigned shl 24) or
            (this[offset + 1].unsigned shl 16) or
            (this[offset + 2].unsigned shl 8) or
            this[offset + 3].unsigned

        private fun ByteArray.decodeChannelName(): String? {
            val end = indexOfFirstZero(startIndex = 10).takeIf { it >= 0 } ?: ENCODED_SIZE
            if (end - 10 > CHANNEL_NAME_BYTES) return null
            val nameBytes = copyOfRange(10, end)
            if (nameBytes.any { it.unsigned !in 0x20..0x7e }) return null
            return nameBytes.decodeToString()
        }

        private fun ByteArray.indexOfFirstZero(startIndex: Int): Int {
            for (index in startIndex until ENCODED_SIZE) {
                if (this[index] == 0.toByte()) return index
            }
            return -1
        }
    }
}
