/*
 * Copyright (c) 2026 Chris7X
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
package org.meshtastic.feature.voiceburst.model

/**
 * Payload of a Voice Burst ready for transmission or just received.
 *
 * Target MVP sizes:
 *   - audioData: ~88 bytes (Codec2 700B, 1 second at 700 bps)
 *   - overhead metadata: ~12 bytes
 *   - total: < 120 bytes → fits in a single MeshPacket (max ~240 bytes)
 *
 * PortNum: PRIVATE_APP = 256 (provisional — open question in the PRD)
 * TODO: define official proto or request a registered portnum upstream.
 *
 * MVP serialization: raw bytes prefixed with a minimal fixed-length header:
 *   [1 byte version=1][1 byte codecMode][2 bytes durationMs][N bytes audioData]
 * This avoids an additional protobuf dependency in the module for MVP.
 */
data class VoiceBurstPayload(
    /**
     * Version of the payload format.
     * Increment if the format changes, to allow graceful degradation.
     */
    val version: Byte = 1,

    /**
     * Codec mode used for encoding.
     * 0 = Codec2 700B (only supported value in MVP)
     * TODO: map to Codec2Mode enum when available.
     */
    val codecMode: Byte = 0,

    /**
     * Actual duration of the recorded audio, in milliseconds.
     * MVP: always ≤ 1000ms.
     */
    val durationMs: Short,

    /**
     * Audio bytes compressed with Codec2.
     * MVP: ~88 bytes per 1 second at 700B.
     */
    val audioData: ByteArray,

    /**
     * Sender node ID (used on the receiver side for display).
     * Populated by the receiver with the from field of the DataPacket.
     */
    val senderNodeId: String = "",
) {

    /**
     * Serializes the payload into a ByteArray to insert into [DataPacket.bytes].
     * Format: [version:1][codecMode:1][durationMs:2 BE][audioData:N]
     */
    fun encode(): ByteArray {
        val buf = ByteArray(4 + audioData.size)
        buf[0] = version
        buf[1] = codecMode
        buf[2] = ((durationMs.toInt() shr 8) and 0xFF).toByte()
        buf[3] = (durationMs.toInt() and 0xFF).toByte()
        audioData.copyInto(buf, destinationOffset = 4)
        return buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceBurstPayload) return false
        return version == other.version &&
            codecMode == other.codecMode &&
            durationMs == other.durationMs &&
            audioData.contentEquals(other.audioData) &&
            senderNodeId == other.senderNodeId
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + codecMode.toInt()
        result = 31 * result + durationMs.toInt()
        result = 31 * result + audioData.contentHashCode()
        result = 31 * result + senderNodeId.hashCode()
        return result
    }

    companion object {
        /** Provisional PortNum for MVP. PRIVATE_APP = 256. */
        const val PORT_NUM = 256

        /** Maximum duration supported in MVP (1 second). */
        const val MAX_DURATION_MS = 1000

        /**
         * Deserializes a payload received from a [DataPacket].
         * Returns null if the format is unrecognizable or the version is not supported.
         */
        fun decode(bytes: ByteArray, senderNodeId: String = ""): VoiceBurstPayload? {
            if (bytes.size < 5) return null // minimum: 4-byte header + 1 byte audio
            val version = bytes[0]
            if (version != 1.toByte()) return null // unsupported version
            val codecMode = bytes[1]
            val durationMs = (((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)).toShort()
            val audioData = bytes.copyOfRange(4, bytes.size)
            return VoiceBurstPayload(
                version = version,
                codecMode = codecMode,
                durationMs = durationMs,
                audioData = audioData,
                senderNodeId = senderNodeId,
            )
        }
    }
}
