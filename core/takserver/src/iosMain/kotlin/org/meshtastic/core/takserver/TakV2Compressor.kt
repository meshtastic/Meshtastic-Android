/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.meshtastic.core.takserver

import org.meshtastic.proto.TAKPacketV2

/**
 * iOS stub for TakV2Compressor.
 * TODO: Replace with Swift SDK integration via interop.
 */
internal actual object TakV2Compressor {

    actual val MAX_DECOMPRESSED_SIZE: Int = 4096
    actual val DICT_ID_NON_AIRCRAFT: Int = 0
    actual val DICT_ID_AIRCRAFT: Int = 1
    actual val DICT_ID_UNCOMPRESSED: Int = 0xFF

    actual fun compress(packet: TAKPacketV2): ByteArray {
        // iOS: Send uncompressed for now (TAK_TRACKER mode)
        val protobufBytes = TAKPacketV2.ADAPTER.encode(packet)
        val wirePayload = ByteArray(1 + protobufBytes.size)
        wirePayload[0] = DICT_ID_UNCOMPRESSED.toByte()
        protobufBytes.copyInto(wirePayload, 1)
        return wirePayload
    }

    actual fun decompressToXml(wirePayload: ByteArray): String {
        // iOS stub: decompress and convert via toCoTMessage().toXml() as fallback
        val packet = decompress(wirePayload)
        return packet.toString() // placeholder — iOS uses Swift SDK directly
    }

    actual fun decompress(wirePayload: ByteArray): TAKPacketV2 {
        require(wirePayload.size >= 2) { "Wire payload too short: ${wirePayload.size} bytes" }

        val flagsByte = wirePayload[0].toInt() and 0xFF
        val payloadBytes = wirePayload.copyOfRange(1, wirePayload.size)

        // iOS stub: only support uncompressed (0xFF) payloads
        if (flagsByte != DICT_ID_UNCOMPRESSED) {
            throw UnsupportedOperationException(
                "iOS zstd decompression not yet implemented. Received dict ID: ${flagsByte and 0x3F}"
            )
        }

        require(payloadBytes.size <= MAX_DECOMPRESSED_SIZE) {
            "Payload size ${payloadBytes.size} exceeds limit $MAX_DECOMPRESSED_SIZE"
        }

        return TAKPacketV2.ADAPTER.decode(payloadBytes)
    }
}
