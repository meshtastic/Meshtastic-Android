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
 * TAKPacket V2 wire format compressor/decompressor.
 *
 * Wire format: [1 byte flags][zstd-compressed TAKPacketV2 protobuf]
 * Flags byte bits 0-5 = dictionary ID, bits 6-7 = reserved.
 * Special value 0xFF = uncompressed raw protobuf (from TAK_TRACKER firmware).
 *
 * Platform-specific implementations use zstd with pre-trained dictionaries.
 */
internal expect object TakV2Compressor {

    /** Maximum allowed decompressed payload size (bytes). */
    val MAX_DECOMPRESSED_SIZE: Int

    /** Dictionary ID for non-aircraft types. */
    val DICT_ID_NON_AIRCRAFT: Int

    /** Dictionary ID for aircraft types. */
    val DICT_ID_AIRCRAFT: Int

    /** Special flags byte value indicating uncompressed raw protobuf. */
    val DICT_ID_UNCOMPRESSED: Int

    /**
     * Compress a TAKPacketV2 into wire payload: [flags byte][zstd compressed protobuf].
     * Selects dictionary based on the CoT type classification.
     */
    fun compress(packet: TAKPacketV2): ByteArray

    /**
     * Decompress a wire payload back to TAKPacketV2.
     * Handles both compressed (dict-based) and uncompressed (0xFF) payloads.
     * @throws IllegalArgumentException if payload is malformed or exceeds size limits.
     */
    fun decompress(wirePayload: ByteArray): TAKPacketV2

    /**
     * Decompress a wire payload and reconstruct CoT XML via the SDK's CotXmlBuilder.
     * Handles ALL payload types (DrawnShape, Marker, Route, etc.) without going
     * through the Wire proto intermediate.
     */
    fun decompressToXml(wirePayload: ByteArray): String
}
