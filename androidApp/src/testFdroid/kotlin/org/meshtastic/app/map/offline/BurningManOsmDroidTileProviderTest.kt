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
package org.meshtastic.app.map.offline

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.osmdroid.util.MapTileIndex
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BurningManOsmDroidTileProviderTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `renders a local Protomaps pedestrian street without a network fallback`() {
        val provider = BurningManOsmDroidTileProvider(writePack(vectorTile()))

        val drawable = checkNotNull(provider.getMapTile(MapTileIndex.getTileIndex(0, 0, 0))) as BitmapDrawable
        val bitmap = checkNotNull(drawable.bitmap)

        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)
        assertEquals(255, Color.alpha(bitmap.getPixel(128, 128)))
        assertEquals(Color.rgb(254, 181, 196), bitmap.getPixel(128, 128))
        assertEquals(Color.rgb(234, 234, 234), bitmap.getPixel(32, 64))
        assertFalse(provider.useDataConnection())
    }

    @Test
    fun `returns no drawable outside the local pack`() {
        val provider = BurningManOsmDroidTileProvider(writePack(vectorTile()))

        assertNull(provider.getMapTile(MapTileIndex.getTileIndex(1, 1, 1)))
    }

    @Test
    fun `reports coverage from the local pack bounds`() {
        val provider = BurningManOsmDroidTileProvider(writePack(vectorTile()))

        assertTrue(provider.covers(0.0, 0.0))
        assertFalse(provider.covers(0.0, 1.0))
    }

    private fun writePack(tile: ByteArray): File {
        val directory = byteArrayOf(1, 0, 1) + varint(tile.size) + byteArrayOf(1)
        val metadata = "{}".encodeToByteArray()
        val header = ByteBuffer.allocate(PMTILES_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("PMTiles".encodeToByteArray())
        header.put(3)
        header.putLong(PMTILES_HEADER_SIZE.toLong())
        header.putLong(directory.size.toLong())
        header.putLong((PMTILES_HEADER_SIZE + directory.size).toLong())
        header.putLong(metadata.size.toLong())
        header.putLong((PMTILES_HEADER_SIZE + directory.size + metadata.size).toLong())
        header.putLong(0)
        header.putLong((PMTILES_HEADER_SIZE + directory.size + metadata.size).toLong())
        header.putLong(tile.size.toLong())
        header.putLong(1)
        header.putLong(1)
        header.putLong(1)
        header.put(1)
        header.put(1)
        header.put(1)
        header.put(1)
        header.put(0)
        header.put(0)

        return temporaryFolder.newFile("burning-man.pmtiles").apply {
            writeBytes(header.array() + directory + metadata + tile)
        }
    }

    private fun vectorTile(): ByteArray = field(3, roadsLayer())

    private fun roadsLayer(): ByteArray = scalar(15, 2) +
        field(1, "roads".encodeToByteArray()) +
        field(2, roadFeature(kindDetail = 1, geometry = verticalLine())) +
        field(2, roadFeature(kindDetail = 2, geometry = footwayLine())) +
        field(3, "kind".encodeToByteArray()) +
        field(3, "kind_detail".encodeToByteArray()) +
        field(4, field(1, "path".encodeToByteArray())) +
        field(4, field(1, "pedestrian".encodeToByteArray())) +
        field(4, field(1, "footway".encodeToByteArray())) +
        scalar(5, 4096)

    private fun roadFeature(kindDetail: Int, geometry: ByteArray): ByteArray =
        field(2, byteArrayOf(0, 0, 1, kindDetail.toByte())) + scalar(3, 2) + field(4, geometry)

    private fun verticalLine(): ByteArray = packed(9, 4096, 0, 10, 0, 8192)

    private fun footwayLine(): ByteArray = packed(9, 0, 2048, 10, 2048, 0)

    private fun packed(vararg values: Int): ByteArray =
        values.fold(byteArrayOf()) { bytes, value -> bytes + varint(value) }

    private fun field(number: Int, bytes: ByteArray): ByteArray =
        varint((number shl 3) or 2) + varint(bytes.size) + bytes

    private fun scalar(number: Int, value: Int): ByteArray = varint(number shl 3) + varint(value)

    private fun varint(value: Int): ByteArray {
        var remaining = value
        val bytes = mutableListOf<Byte>()
        do {
            var next = remaining and 0x7f
            remaining = remaining ushr 7
            if (remaining > 0) next = next or 0x80
            bytes += next.toByte()
        } while (remaining > 0)
        return bytes.toByteArray()
    }

    private companion object {
        const val PMTILES_HEADER_SIZE = 127
    }
}
