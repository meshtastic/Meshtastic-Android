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
package org.meshtastic.core.ble.niimbot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

internal fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

/**
 * Expected bytes are the output of labeltastic's vendored niimprint (`NiimbotPacket.to_bytes`,
 * `PrinterClient._encode_image_counted`, `profiles.to_printer_orientation`) at meshtastic/labeltastic#38, run over the
 * same inputs.
 */
class NiimbotEncodingTest {

    @Test
    fun `packets frame and checksum as niimprint does`() {
        assertEquals("5555c10101c1aaaa", NiimbotPacket(0xC1, "01".unhex()).toBytes().hex())
        assertEquals(
            "5555130d00ec006000010000000000000093aaaa",
            NiimbotPacket(0x13, "00ec0060000100000000000000".unhex()).toBytes().hex(),
        )
        assertEquals("55558403012cff55aaaa", NiimbotPacket(0x84, "012cff".unhex()).toBytes().hex())
    }

    @Test
    fun `rows encode byte for byte as niimprint does`() {
        val bitmap = LabelBitmap(width = 96, height = 8)
        for (x in 0 until 96) bitmap.setInk(x, 0)
        bitmap.setInk(0, 4)
        for (y in 5..6) listOf(40, 41, 95).forEach { bitmap.setInk(it, y) }

        assertEquals(
            listOf(
                "55558512000020202001ffffffffffffffffffffffffb6aaaa",
                "5555840300010385aaaa",
                "5555851200040100000180000000000000000000000013aaaa",
                "555585120005000201020000000000c000000000000152aaaa",
                "5555840300070181aaaa",
            ),
            NiimbotImageEncoder.packets(bitmap).map { it.toBytes().hex() },
        )
    }

    @Test
    fun `a run of identical rows splits after 255`() {
        assertEquals(
            listOf("555584030000ff78aaaa", "5555840300ff2d55aaaa"),
            NiimbotImageEncoder.packets(LabelBitmap(width = 96, height = 300)).map { it.toBytes().hex() },
        )

        val inked = LabelBitmap(width = 96, height = 260)
        for (y in 0 until 260) listOf(31, 32, 63, 64).forEach { inked.setInk(it, y) }
        assertEquals(
            listOf(
                "555585120000010201ff0000000180000001800000006aaaaa",
                "5555851200ff010201050000000180000001800000006faaaa",
            ),
            NiimbotImageEncoder.packets(inked).map { it.toBytes().hex() },
        )
    }

    @Test
    fun `a reading-orientation picture turns a quarter clockwise and centers on the head`() {
        val ink = setOf(0 to 0, 1 to 0, 3 to 1)
        val bitmap =
            LabelBitmap.fromReadingOrientation(width = 4, height = 2, headDots = 8) { x, y -> (x to y) in ink }!!

        assertEquals(8 to 4, bitmap.width to bitmap.height)
        val inked = (0 until 4).flatMap { y -> (0 until 8).filter { x -> bitmap.isInk(x, y) }.map { x -> x to y } }
        assertEquals(listOf(4 to 0, 4 to 1, 3 to 3), inked)
    }

    @Test
    fun `a picture taller than the head is refused`() {
        assertNull(LabelBitmap.fromReadingOrientation(width = 4, height = 9, headDots = 8) { _, _ -> false })
    }

    @Test
    fun `the parser joins split notifications and skips noise and corrupt frames`() {
        val parser = NiimbotPacketParser()
        val first = "5555c20101c2aaaa".unhex()
        val second = NiimbotPacket(0xDD, byteArrayOf(1, 2, 3)).toBytes()

        assertEquals(emptyList(), parser.append(byteArrayOf(0x00, 0x13) + first.copyOfRange(0, 3)))
        assertEquals(
            listOf(NiimbotPacket(0xC2, byteArrayOf(1)), NiimbotPacket(0xDD, byteArrayOf(1, 2, 3))),
            parser.append(first.copyOfRange(3, first.size) + second),
        )

        val badChecksum = first.copyOf().also { it[5] = 0x3D }
        val badTail = first.copyOf().also { it[7] = 0x00 }
        assertEquals(listOf(NiimbotPacket(0xC2, byteArrayOf(1))), parser.append(badChecksum + badTail + first))
    }

    // The broken frame's length 7 reaches to byte 14, past the head of the intact reply at byte 7.
    @Test
    fun `a corrupt frame whose length runs into the next frame does not swallow it`() {
        val packets = NiimbotPacketParser().append("5555010701aaaa5555c10101c1aaaa".unhex())

        assertEquals(listOf(NiimbotPacket(0xC1, byteArrayOf(1))), packets)
    }
}
