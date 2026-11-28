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

private const val BLANK_ROWS = 0x84
private const val INKED_ROW = 0x85
private const val MAX_REPEAT = 255
private const val THIRDS = 3
private const val BYTE_SHIFT = 8
private const val BYTE_MASK = 0xFF

/**
 * Turns a bitmap into the printer's row packets: 0x85 for a row with ink, carrying its dot count in each third of the
 * head, and 0x84 for blank rows. Identical neighboring rows go as one packet with a repeat count.
 */
object NiimbotImageEncoder {
    fun packets(bitmap: LabelBitmap): List<NiimbotPacket> {
        val packets = mutableListOf<NiimbotPacket>()
        var y = 0
        while (y < bitmap.height) {
            val row = bitmap.row(y)
            var repeat = 1
            while (y + repeat < bitmap.height && repeat < MAX_REPEAT && bitmap.row(y + repeat).contentEquals(row)) {
                repeat++
            }
            val rowNumber = byteArrayOf((y shr BYTE_SHIFT).toByte(), (y and BYTE_MASK).toByte())
            packets +=
                if (row.all { it == 0.toByte() }) {
                    NiimbotPacket(BLANK_ROWS, rowNumber + repeat.toByte())
                } else {
                    NiimbotPacket(INKED_ROW, rowNumber + dotCounts(row) + repeat.toByte() + row)
                }
            y += repeat
        }
        return packets
    }

    /** Ink dots in each third of the head; zeros when the row's bytes don't split into thirds. */
    private fun dotCounts(row: ByteArray): ByteArray {
        val counts = IntArray(THIRDS)
        val third = row.size / THIRDS
        if (third > 0 && row.size <= third * THIRDS) {
            row.forEachIndexed { index, byte ->
                counts[minOf(index / third, THIRDS - 1)] += (byte.toInt() and BYTE_MASK).countOneBits()
            }
        }
        return ByteArray(THIRDS) { counts[it].toByte() }
    }
}
