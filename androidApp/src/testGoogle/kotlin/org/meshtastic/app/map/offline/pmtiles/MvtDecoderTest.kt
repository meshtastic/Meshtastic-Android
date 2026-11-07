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

    @Test
    fun `a truncated parameter stream drops the rest of the tile instead of spinning through count`() {
        // LineTo(count=1) with no dx,dy pair following it — truncated immediately. A malformed/malicious tile can
        // set count up to ~536M; the fix must detect this in O(1), not by exhausting `count` iterations.
        val commands = listOf((2) or (1 shl 3))

        val lines = MvtDecoder.decodeGeometry(VectorTile.GEOM_LINESTRING, commands)

        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a truncated command near the end of the stream is not misread as a trailing command`() {
        // MoveTo(0,0), LineTo(5,5) [both valid], then a truncated LineTo(count=1) header with no dx,dy pair behind
        // it, followed by one leftover int that would decode as a valid ClosePath(count=1) header if the parser
        // kept going after the truncation. The old repeat+return@repeat implementation only skipped the failing
        // iteration, not the whole command stream, so the outer loop moved on and spuriously closed the ring — this
        // pins that the ring comes back open (2 points, not 3) once truncation correctly abandons the whole stream.
        val moveTo = listOf(9, 0, 0) // MoveTo(count=1) -> (0,0)
        val lineTo = listOf(10, 10, 10) // LineTo(count=1) -> +(5,5) = (5,5)
        val truncatedLineTo = 10 // LineTo(count=1) header with no dx,dy pair following
        val leftoverClosePathHeader = (1 shl 3) or 7 // would decode as ClosePath(count=1) if read as a new header
        val commands = moveTo + lineTo + truncatedLineTo + leftoverClosePathHeader

        val rings = MvtDecoder.decodeGeometry(VectorTile.GEOM_POLYGON, commands)

        assertEquals(listOf(TileCoord(0, 0), TileCoord(5, 5)), rings.single())
    }

    @Test
    fun `an outer ring, its hole and a second outer ring group into two polygons`() {
        // Tile coordinates are y-down, so the spec's clockwise exterior has a positive shoelace area:
        // (0,0)->(100,0)->(100,100)->(0,100) is +20000, and the counterclockwise hole is -6400.
        val outerOne = listOf(0 to 0, 100 to 0, 100 to 100, 0 to 100)
        val hole = listOf(20 to 20, 20 to 80, 80 to 80, 80 to 20)
        val outerTwo = listOf(200 to 0, 300 to 0, 300 to 100, 200 to 100)

        val rings = MvtDecoder.decodeGeometry(VectorTile.GEOM_POLYGON, polygonCommands(outerOne, hole, outerTwo))
        val polygons = MvtDecoder.groupPolygons(rings)

        assertEquals(2, polygons.size)
        assertEquals(TileCoord(100, 100), polygons[0].outer[2])
        assertEquals(1, polygons[0].holes.size)
        assertEquals(TileCoord(20, 80), polygons[0].holes.single()[1])
        assertEquals(TileCoord(200, 0), polygons[1].outer.first())
        assertEquals(emptyList(), polygons[1].holes)
    }

    @Test
    fun `a hole separates two outer rings that follow it`() {
        val outerOne = listOf(0 to 0, 100 to 0, 100 to 100, 0 to 100)
        val outerTwo = listOf(200 to 0, 300 to 0, 300 to 100, 200 to 100)
        val holeInTwo = listOf(220 to 20, 220 to 80, 280 to 80, 280 to 20)

        val polygons =
            MvtDecoder.groupPolygons(
                MvtDecoder.decodeGeometry(VectorTile.GEOM_POLYGON, polygonCommands(outerOne, outerTwo, holeInTwo)),
            )

        assertEquals(listOf(0, 1), polygons.map { it.holes.size })
    }

    @Test
    fun `tiles wound the other way group relative to the first ring`() {
        val outerOne = listOf(0 to 0, 0 to 100, 100 to 100, 100 to 0)
        val hole = listOf(20 to 20, 80 to 20, 80 to 80, 20 to 80)
        val outerTwo = listOf(200 to 0, 200 to 100, 300 to 100, 300 to 0)

        val polygons =
            MvtDecoder.groupPolygons(
                MvtDecoder.decodeGeometry(VectorTile.GEOM_POLYGON, polygonCommands(outerOne, hole, outerTwo)),
            )

        assertEquals(listOf(1, 0), polygons.map { it.holes.size })
    }

    @Test
    fun `a ring with no area is dropped`() {
        val sliver = polygonCommands(listOf(0 to 0, 10 to 0, 20 to 0))

        assertEquals(emptyList(), MvtDecoder.groupPolygons(MvtDecoder.decodeGeometry(VectorTile.GEOM_POLYGON, sliver)))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `feature id presence and attributes decode, including zigzag sint values`() {
        // Layer{ keys=["min_zoom","kind"], values=[sint 12 (zigzag 24), string "rail"] },
        // Feature{ tags=[0,0,1,1] } with no id, then Feature{ id=0 }.
        val bytes =
            byteArrayOf(
                0x1A,
                0x28, // Tile.layers[0], len=40
                0x12,
                0x06,
                0x12,
                0x04,
                0x00,
                0x00,
                0x01,
                0x01, // features[0]: tags (packed) [0,0,1,1]
                0x12,
                0x02,
                0x08,
                0x00, // features[1]: id = 0
                0x1A,
                0x08,
                0x6D,
                0x69,
                0x6E,
                0x5F,
                0x7A,
                0x6F,
                0x6F,
                0x6D, // keys[0] "min_zoom"
                0x1A,
                0x04,
                0x6B,
                0x69,
                0x6E,
                0x64, // keys[1] "kind"
                0x22,
                0x02,
                0x30,
                0x18, // values[0].sint_value = 24 (zigzag of 12)
                0x22,
                0x06,
                0x0A,
                0x04,
                0x72,
                0x61,
                0x69,
                0x6C, // values[1].string_value "rail"
            )

        val layer = ProtoBuf.decodeFromByteArray(VectorTile.serializer(), bytes).layers.single()
        val withTags = layer.features[0]

        assertEquals(null, withTags.id)
        assertEquals(0L, layer.features[1].id)
        assertEquals(mapOf<String, Any>("min_zoom" to 12L, "kind" to "rail"), withTags.properties(layer))
    }

    /** The command stream for rings given as absolute points; the cursor carries across rings as in the spec. */
    private fun polygonCommands(vararg rings: List<Pair<Int, Int>>): List<Int> {
        fun zigzag(v: Int) = (v shl 1) xor (v shr 31)
        var cursor = 0 to 0
        val out = mutableListOf<Int>()
        for (ring in rings) {
            ring.forEachIndexed { i, point ->
                if (i == 0) out += 1 or (1 shl 3)
                if (i == 1) out += 2 or ((ring.size - 1) shl 3)
                out += zigzag(point.first - cursor.first)
                out += zigzag(point.second - cursor.second)
                cursor = point
            }
            out += 7 or (1 shl 3)
        }
        return out
    }
}
