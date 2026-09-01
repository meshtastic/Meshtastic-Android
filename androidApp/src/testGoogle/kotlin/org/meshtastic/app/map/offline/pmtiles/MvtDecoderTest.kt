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
package org.meshtastic.app.map.offline.pmtiles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [MVT_TILE_BYTES] is a hand-encoded protobuf message, not a sample pulled from a real tile or built via
 * [ProtoBuf.encodeToByteArray] — encoding it with the very serializer under test would only prove the encoder and
 * decoder agree with each other, not that either agrees with the wire format real PMTiles data actually uses (in
 * particular, whether `kotlinx-serialization-protobuf` decodes MVT's *packed* repeated `uint32` fields correctly, which
 * is the one place this module leans on library behaviour it doesn't otherwise exercise). Encodes: `Tile{ layers:
 * [ Layer{ name="l", version=2, extent=4096, features: [ Feature{ id=1, type=POLYGON, geometry=[MoveTo(0,0), LineTo(10,0), LineTo(10,10), LineTo(0,10), ClosePath]
 * } ] } ] }`. See the field-by-field byte breakdown in a comment on each line below.
 */
@OptIn(ExperimentalSerializationApi::class)
private val MVT_TILE_BYTES =
    byteArrayOf(
        0x1A,
        0x1B, // Tile.layers[0]: tag=3<<3|2, len=27
        0x0A,
        0x01,
        0x6C, // Layer.name = "l"
        0x12,
        0x11, // Layer.features[0]: tag=2<<3|2, len=17
        0x08,
        0x01, // Feature.id = 1
        0x18,
        0x03, // Feature.type = POLYGON (3)
        0x22,
        0x0B, // Feature.geometry: tag=4<<3|2 (packed), len=11
        0x09,
        0x00,
        0x00, // MoveTo(count=1) -> zigzag(0), zigzag(0): (0,0)
        0x1A,
        0x14,
        0x00, // LineTo(count=3), first pair -> zigzag(10)=20, zigzag(0)=0: +(10,0) = (10,0)
        0x00,
        0x14, // second pair -> zigzag(0)=0, zigzag(10)=20: +(0,10) = (10,10)
        0x13,
        0x00, // third pair -> zigzag(-10)=19, zigzag(0)=0: +(-10,0) = (0,10)
        0x0F, // ClosePath(count=1)
        0x28,
        0x80.toByte(),
        0x20, // Layer.extent = 4096 (varint: 0x80 0x20)
        0x78,
        0x02, // Layer.version = 2
    )

class MvtDecoderTest {

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `decodes a hand-built tile's layer, feature and geometry fields`() {
        val tile = ProtoBuf.decodeFromByteArray(VectorTile.serializer(), MVT_TILE_BYTES)

        val layer = tile.layers.single()
        assertEquals("l", layer.name)
        assertEquals(2, layer.version)
        assertEquals(4096, layer.extent)

        val feature = layer.features.single()
        assertEquals(1L, feature.id)
        assertEquals(VectorTile.GEOM_POLYGON, feature.type)
    }

    @Test
    fun `decodes a closed polygon ring from its MoveTo-LineTo-ClosePath command stream`() {
        // The raw geometry ints from MVT_TILE_BYTES's geometry field, spelled out rather than re-parsed, so this
        // test still pins the exact command stream the byte comment above claims to encode.
        val commands = listOf(9, 0, 0, 26, 20, 0, 0, 20, 19, 0, 15)

        val rings = MvtDecoder.decodeGeometry(VectorTile.GEOM_POLYGON, commands)

        assertEquals(
            listOf(TileCoord(0, 0), TileCoord(10, 0), TileCoord(10, 10), TileCoord(0, 10), TileCoord(0, 0)),
            rings.single(),
        )
    }

    @Test
    fun `a MultiPoint feature's single MoveTo command yields one point per delta`() {
        // MoveTo(count=3): (5,5), then +(-2,0), then +(0,3) — three independent points, not a connected line.
        val commands = listOf((1) or (3 shl 3), 10, 10, 3, 0, 0, 6)

        val points = MvtDecoder.decodeGeometry(VectorTile.GEOM_POINT, commands)

        assertEquals(listOf(TileCoord(5, 5), TileCoord(3, 5), TileCoord(3, 8)), points.single())
    }

    @Test
    fun `an open LineString is returned without an implicit close`() {
        // MoveTo(1) to (0,0), LineTo(2) to (4,0) then (4,4) — no ClosePath, so no return-to-start point.
        val commands = listOf(9, 0, 0, (2) or (2 shl 3), 8, 0, 0, 8)

        val lines = MvtDecoder.decodeGeometry(VectorTile.GEOM_LINESTRING, commands)

        assertEquals(listOf(TileCoord(0, 0), TileCoord(4, 0), TileCoord(4, 4)), lines.single())
    }
}
