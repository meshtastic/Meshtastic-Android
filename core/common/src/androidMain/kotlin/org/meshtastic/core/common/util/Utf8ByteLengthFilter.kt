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
package org.meshtastic.core.common.util

import android.text.InputFilter
import android.text.Spanned

/**
 * An [InputFilter] that constrains text length based on UTF-8 byte count instead of character count. This is
 * particularly useful for fields that must be stored in byte-limited buffers, such as hardware configuration fields.
 *
 * @param maxBytes The maximum allowed length in UTF-8 bytes.
 */
class Utf8ByteLengthFilter(private val maxBytes: Int) : InputFilter {

    private companion object {
        const val ONE_BYTE_LIMIT = '\u0080'
        const val TWO_BYTE_LIMIT = '\u0800'
        const val BYTES_1 = 1
        const val BYTES_2 = 2
        const val BYTES_3 = 3
    }

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int,
    ): CharSequence? {
        val srcByteCount = countUtf8Bytes(source, start, end)

        // Calculate bytes in dest excluding the range being replaced
        val destLen = dest.length
        var destByteCount = 0
        destByteCount += countUtf8Bytes(dest, 0, dstart)
        destByteCount += countUtf8Bytes(dest, dend, destLen)

        var keepBytes = maxBytes - destByteCount
        return when {
            keepBytes <= 0 -> ""

            keepBytes >= srcByteCount -> null

            else -> {
                for (i in start until end) {
                    val c = source[i]
                    keepBytes -= getByteCount(c)
                    if (keepBytes < 0) {
                        return source.subSequence(start, i)
                    }
                }
                null
            }
        }
    }

    private fun countUtf8Bytes(seq: CharSequence, start: Int, end: Int): Int {
        var count = 0
        for (i in start until end) {
            count += getByteCount(seq[i])
        }
        return count
    }

    private fun getByteCount(c: Char): Int = when {
        c < ONE_BYTE_LIMIT -> BYTES_1
        c < TWO_BYTE_LIMIT -> BYTES_2
        else -> BYTES_3
    }
}
