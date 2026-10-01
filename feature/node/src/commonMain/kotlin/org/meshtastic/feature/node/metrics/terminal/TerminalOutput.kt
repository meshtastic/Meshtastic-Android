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
package org.meshtastic.feature.node.metrics.terminal

import okio.ByteString

private const val UTF8_CONTINUATION_MASK = 0xC0
private const val UTF8_CONTINUATION = 0x80
private const val UTF8_LEAD_2 = 0xE0
private const val UTF8_LEAD_2_BITS = 0xC0
private const val UTF8_LEAD_3 = 0xF0
private const val UTF8_LEAD_3_BITS = 0xE0
private const val UTF8_LEAD_4 = 0xF8
private const val UTF8_LEAD_4_BITS = 0xF0
private const val BYTE_MASK = 0xFF
private const val UTF8_LEN_2 = 2
private const val UTF8_LEN_3 = 3
private const val UTF8_LEN_4 = 4

/**
 * PTY output as lines. An OUTPUT frame is whatever one PTY read returned, so frame boundaries fall mid-line and
 * mid-character: a split UTF-8 sequence waits for its tail. The last line is always the one being written, empty
 * straight after a newline, which is where the screen draws the caret.
 */
internal class TerminalOutput(private val maxLines: Int) {
    private val lines = ArrayDeque<StringBuilder>().apply { addLast(StringBuilder()) }
    private var carry = ByteArray(0)

    fun lines(): List<String> = lines.map { it.toString() }

    fun append(bytes: ByteString) {
        val all = carry + bytes.toByteArray()
        val complete = completeUtf8Length(all)
        carry = all.copyOfRange(complete, all.size)
        all.decodeToString(0, complete).forEach(::appendChar)
    }

    /** A line of our own, never merged into what the PTY is writing. */
    fun notice(text: String) {
        if (lines.last().isEmpty()) lines.last().append(text) else lines.addLast(StringBuilder(text))
        newLine()
    }

    private fun appendChar(c: Char) {
        when (c) {
            '\n' -> newLine()
            '\r' -> Unit
            '\b' -> lines.last().let { if (it.isNotEmpty()) it.deleteAt(it.lastIndex) }
            else -> lines.last().append(c)
        }
    }

    private fun newLine() {
        lines.addLast(StringBuilder())
        trim()
    }

    private fun trim() {
        while (lines.size > maxLines) lines.removeFirst()
    }

    /** Length of [bytes] up to, not including, a trailing UTF-8 sequence that is still missing bytes. */
    private fun completeUtf8Length(bytes: ByteArray): Int {
        var start = bytes.size - 1
        while (start >= 0 && (bytes[start].toInt() and UTF8_CONTINUATION_MASK) == UTF8_CONTINUATION) start--
        if (start < 0) return bytes.size
        val lead = bytes[start].toInt() and BYTE_MASK
        val needed =
            when {
                lead and UTF8_LEAD_2 == UTF8_LEAD_2_BITS -> UTF8_LEN_2
                lead and UTF8_LEAD_3 == UTF8_LEAD_3_BITS -> UTF8_LEN_3
                lead and UTF8_LEAD_4 == UTF8_LEAD_4_BITS -> UTF8_LEN_4
                else -> 1
            }
        return if (bytes.size - start < needed) start else bytes.size
    }
}
