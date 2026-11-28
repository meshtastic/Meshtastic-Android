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

private const val BITS_PER_BYTE = 8
private const val HIGH_BIT = 0x80

/**
 * A one-bit picture in the printer's orientation: [width] dots across the head, [height] rows along the feed. Each row
 * is packed most significant bit first, 1 for ink.
 */
class LabelBitmap(val width: Int, val height: Int) {
    val bytesPerRow: Int = (width + BITS_PER_BYTE - 1) / BITS_PER_BYTE

    private val rows = Array(height) { ByteArray(bytesPerRow) }

    fun isInk(x: Int, y: Int): Boolean = rows[y][x / BITS_PER_BYTE].toInt() and (HIGH_BIT shr (x % BITS_PER_BYTE)) != 0

    fun setInk(x: Int, y: Int) {
        val index = x / BITS_PER_BYTE
        rows[y][index] = (rows[y][index].toInt() or (HIGH_BIT shr (x % BITS_PER_BYTE))).toByte()
    }

    fun row(y: Int): ByteArray = rows[y]

    companion object {
        /**
         * Turns a picture drawn in reading orientation (along the feed) a quarter clockwise, so its height runs across
         * the head, centered when it is shorter than [headDots]. Null when it is taller than the head.
         */
        fun fromReadingOrientation(
            width: Int,
            height: Int,
            headDots: Int,
            isInk: (x: Int, y: Int) -> Boolean,
        ): LabelBitmap? {
            if (height > headDots) return null
            val bitmap = LabelBitmap(width = headDots, height = width)
            val inset = (headDots - height) / 2
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (isInk(x, y)) bitmap.setInk(x = inset + height - 1 - y, y = x)
                }
            }
            return bitmap
        }
    }
}
