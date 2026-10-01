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

/** Colour of a cell: [DEFAULT_COLOR], an xterm palette index 0-255, or [TRUECOLOR_FLAG] or-ed with 0xRRGGBB. */
internal const val DEFAULT_COLOR = -1
internal const val TRUECOLOR_FLAG = 0x1000000

internal data class CellStyle(
    val fg: Int = DEFAULT_COLOR,
    val bg: Int = DEFAULT_COLOR,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
) {
    companion object {
        val Plain = CellStyle()
    }
}

/** A run of [style] over `[start, end)` of a [TerminalLine]'s text. */
internal data class StyleRun(val start: Int, val end: Int, val style: CellStyle)

internal data class TerminalLine(val text: String, val runs: List<StyleRun>, val isNotice: Boolean = false) {
    companion object {
        val Empty = TerminalLine("", emptyList())
    }
}

/** What one chunk of output did, for the parts of the UI that react to it rather than render it. */
internal sealed interface EchoEvent {
    /** A printable character written at the cursor. */
    data class Printed(val char: Char) : EchoEvent

    /** Anything that moved or rewrote the line other than printing: CR, LF, BS, an erase, a cursor move. */
    data object Edited : EchoEvent
}

internal class AppendResult(val echo: List<EchoEvent>, val bell: Boolean, val enteredFullScreen: Boolean)

private const val ESC = '\u001b'
private const val BEL = '\u0007'
private const val TAB_STOP = 8

/** Longest parameter string kept for one CSI sequence; a hostile or broken stream cannot grow it past this. */
private const val MAX_CSI_LENGTH = 64
private const val SGR_TRUECOLOR = 2
private const val SGR_PALETTE = 5
private const val SGR_RGB_ARGS = 3
private const val MAX_PALETTE = 255
private const val BYTE_SHIFT = 8
private const val ALT_SCREEN_1049 = 1049
private const val ALT_SCREEN_47 = 47
private const val ALT_SCREEN_1047 = 1047
private const val DECCKM = 1
private const val ERASE_TO_END = 0
private const val ERASE_TO_START = 1
private const val ERASE_ALL = 2

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
 * PTY output as styled lines - a line-oriented subset of a VT100/xterm screen.
 *
 * It keeps what a shell over a slow link actually produces: SGR colour and attributes, `\r` overwrite, backspace, tabs,
 * erase-in-line, horizontal cursor moves, insert/delete characters and `clear`. Vertical cursor addressing and the
 * alternate screen are not modelled - a full-screen program redraws a whole screen per keystroke, which the mesh cannot
 * carry - so their sequences are consumed and [AppendResult.enteredFullScreen] lets the UI say so.
 *
 * An OUTPUT frame is whatever one PTY read returned, so frame boundaries fall mid-line, mid-sequence and mid-character:
 * parser state and a split UTF-8 tail both carry across [append] calls. The last line is always the one being written,
 * which is where the screen draws the cursor.
 */
@Suppress("TooManyFunctions")
internal class TerminalOutput(private val maxLines: Int) {

    private class Row(var isNotice: Boolean = false) {
        val chars = StringBuilder()
        val styles = ArrayList<CellStyle>()
        var snapshot: TerminalLine? = null
    }

    private enum class State {
        GROUND,
        ESCAPE,
        ESCAPE_INTERMEDIATE,
        CSI,
        OSC,
        OSC_ESCAPE,
        STRING,
        STRING_ESCAPE,
    }

    private val rows = ArrayDeque<Row>().apply { addLast(Row()) }
    private var carry = ByteArray(0)
    private var state = State.GROUND
    private val csi = StringBuilder()
    private var style = CellStyle.Plain

    /** Column of the cursor on the last line. */
    var cursorColumn = 0
        private set

    /** DECCKM: arrow keys must be sent as `ESC O x` rather than `ESC [ x` while a program asks for it. */
    var applicationCursorKeys = false
        private set

    private val echo = ArrayList<EchoEvent>()
    private var bell = false
    private var enteredFullScreen = false

    fun lines(): List<TerminalLine> = rows.map { row ->
        row.snapshot ?: snapshot(row).also { row.snapshot = it }
    }

    fun append(bytes: ByteString): AppendResult {
        echo.clear()
        bell = false
        enteredFullScreen = false
        val all = carry + bytes.toByteArray()
        val complete = completeUtf8Length(all)
        carry = all.copyOfRange(complete, all.size)
        all.decodeToString(0, complete).forEach(::feed)
        return AppendResult(echo.toList(), bell, enteredFullScreen)
    }

    /** A line of our own, never merged into what the PTY is writing. */
    fun notice(text: String) {
        val current = rows.last()
        val target = if (current.chars.isEmpty()) current else Row().also { rows.addLast(it) }
        target.isNotice = true
        target.chars.append(text)
        repeat(text.length) { target.styles.add(CellStyle.Plain) }
        target.snapshot = null
        newLine()
    }

    // region --- Parser ---

    @Suppress("CyclomaticComplexMethod")
    private fun feed(c: Char) {
        when (state) {
            State.GROUND -> ground(c)

            State.ESCAPE -> escape(c)

            State.ESCAPE_INTERMEDIATE -> state = State.GROUND

            State.CSI ->
                if (c in '@'..'~') {
                    dispatchCsi(c)
                    state = State.GROUND
                } else if (csi.length < MAX_CSI_LENGTH) {
                    csi.append(c)
                }

            State.OSC ->
                when (c) {
                    BEL -> state = State.GROUND
                    ESC -> state = State.OSC_ESCAPE
                    else -> Unit
                }

            State.OSC_ESCAPE -> state = if (c == '\\') State.GROUND else State.OSC

            State.STRING -> if (c == ESC) state = State.STRING_ESCAPE

            State.STRING_ESCAPE -> state = if (c == '\\') State.GROUND else State.STRING
        }
    }

    private fun ground(c: Char) {
        when (c) {
            ESC -> state = State.ESCAPE

            '\n' -> {
                newLine()
                echo += EchoEvent.Edited
            }

            '\r' -> {
                cursorColumn = 0
                echo += EchoEvent.Edited
            }

            '\b' -> {
                if (cursorColumn > 0) cursorColumn--
                echo += EchoEvent.Edited
            }

            '\t' -> {
                val next = (cursorColumn / TAB_STOP + 1) * TAB_STOP
                while (cursorColumn < next) put(' ')
                echo += EchoEvent.Edited
            }

            BEL -> bell = true

            else ->
                if (!c.isISOControl()) {
                    put(c)
                    echo += EchoEvent.Printed(c)
                }
        }
    }

    private fun escape(c: Char) {
        state =
            when (c) {
                '[' -> {
                    csi.clear()
                    State.CSI
                }

                ']' -> State.OSC

                // DCS, SOS, PM, APC: consumed up to the string terminator.
                'P',
                'X',
                '^',
                '_',
                -> State.STRING

                // Charset designations and other two-byte sequences carry one more byte.
                '(',
                ')',
                '*',
                '+',
                '#',
                '%',
                -> State.ESCAPE_INTERMEDIATE

                'c' -> {
                    clearScreen()
                    State.GROUND
                }

                else -> State.GROUND
            }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun dispatchCsi(final: Char) {
        val raw = csi.toString()
        val private = raw.startsWith('?')
        val params = raw.trimStart('?', '>', '=', '<').split(';').map { it.toIntOrNull() }
        fun arg(i: Int, default: Int = 1): Int = params.getOrNull(i)?.takeIf { it > 0 } ?: default

        if (private) {
            if (final == 'h' || final == 'l') setPrivateModes(params, final == 'h')
            return
        }
        when (final) {
            'm' -> sgr(params)

            'K' -> eraseInLine(params.firstOrNull() ?: ERASE_TO_END)

            'J' -> if ((params.firstOrNull() ?: ERASE_TO_END) >= ERASE_ALL) clearScreen()

            'C' -> moveCursor(cursorColumn + arg(0))

            'D' -> moveCursor(cursorColumn - arg(0))

            'G',
            '`',
            -> moveCursor(arg(0) - 1)

            // Line-oriented: only the column of an absolute position is meaningful.
            'H',
            'f',
            -> moveCursor(arg(1) - 1)

            'P' -> deleteChars(arg(0))

            '@' -> insertBlanks(arg(0))

            'X' -> eraseChars(arg(0))

            else -> return
        }
        echo += EchoEvent.Edited
    }

    private fun setPrivateModes(params: List<Int?>, on: Boolean) {
        params.forEach { mode ->
            when (mode) {
                DECCKM -> applicationCursorKeys = on

                ALT_SCREEN_1049,
                ALT_SCREEN_47,
                ALT_SCREEN_1047,
                -> if (on) enteredFullScreen = true

                else -> Unit
            }
        }
    }

    // The numbers are ECMA-48's SGR parameters; naming each would only restate the table.
    @Suppress("CyclomaticComplexMethod", "MagicNumber")
    private fun sgr(params: List<Int?>) {
        var i = 0
        if (params.isEmpty()) style = CellStyle.Plain
        while (i < params.size) {
            val p = params[i] ?: 0
            style =
                when (p) {
                    0 -> CellStyle.Plain

                    1 -> style.copy(bold = true)

                    2 -> style.copy(dim = true)

                    3 -> style.copy(italic = true)

                    4 -> style.copy(underline = true)

                    7 -> style.copy(inverse = true)

                    22 -> style.copy(bold = false, dim = false)

                    23 -> style.copy(italic = false)

                    24 -> style.copy(underline = false)

                    27 -> style.copy(inverse = false)

                    in 30..37 -> style.copy(fg = p - 30)

                    39 -> style.copy(fg = DEFAULT_COLOR)

                    in 40..47 -> style.copy(bg = p - 40)

                    49 -> style.copy(bg = DEFAULT_COLOR)

                    in 90..97 -> style.copy(fg = p - 90 + 8)

                    in 100..107 -> style.copy(bg = p - 100 + 8)

                    38,
                    48,
                    -> {
                        val (color, used) = extendedColor(params, i + 1)
                        i += used
                        if (color == null) {
                            style
                        } else if (p == 38) {
                            style.copy(fg = color)
                        } else {
                            style.copy(bg = color)
                        }
                    }

                    else -> style
                }
            i++
        }
    }

    /** `5;n` or `2;r;g;b` after a 38/48. Returns the colour and how many parameters it consumed. */
    private fun extendedColor(params: List<Int?>, at: Int): Pair<Int?, Int> = when (params.getOrNull(at)) {
        SGR_PALETTE -> params.getOrNull(at + 1)?.coerceIn(0, MAX_PALETTE) to 2

        SGR_TRUECOLOR -> {
            val rgb = (1..SGR_RGB_ARGS).map { (params.getOrNull(at + it) ?: 0).coerceIn(0, BYTE_MASK) }
            (TRUECOLOR_FLAG or (rgb[0] shl (2 * BYTE_SHIFT)) or (rgb[1] shl BYTE_SHIFT) or rgb[2]) to
                (1 + SGR_RGB_ARGS)
        }

        else -> null to 0
    }

    // endregion

    // region --- Line editing ---

    private fun current(): Row = rows.last()

    private fun put(c: Char) {
        val row = current()
        padTo(row, cursorColumn)
        if (cursorColumn < row.chars.length) {
            row.chars[cursorColumn] = c
            row.styles[cursorColumn] = style
        } else {
            row.chars.append(c)
            row.styles.add(style)
        }
        row.snapshot = null
        cursorColumn++
    }

    private fun padTo(row: Row, column: Int) {
        while (row.chars.length < column) {
            row.chars.append(' ')
            row.styles.add(CellStyle.Plain)
        }
    }

    private fun moveCursor(column: Int) {
        cursorColumn = column.coerceAtLeast(0)
    }

    private fun eraseInLine(mode: Int) {
        val row = current()
        when (mode) {
            ERASE_TO_END -> truncate(row, cursorColumn)

            ERASE_TO_START -> {
                padTo(row, cursorColumn)
                for (i in 0 until minOf(cursorColumn + 1, row.chars.length)) {
                    row.chars[i] = ' '
                    row.styles[i] = CellStyle.Plain
                }
            }

            else -> truncate(row, 0)
        }
        row.snapshot = null
    }

    private fun truncate(row: Row, length: Int) {
        if (length < row.chars.length) {
            row.chars.setLength(length)
            while (row.styles.size > length) row.styles.removeAt(row.styles.lastIndex)
        }
    }

    private fun deleteChars(count: Int) {
        val row = current()
        val end = minOf(cursorColumn + count, row.chars.length)
        if (cursorColumn >= end) return
        row.chars.deleteRange(cursorColumn, end)
        repeat(end - cursorColumn) { row.styles.removeAt(cursorColumn) }
        row.snapshot = null
    }

    private fun insertBlanks(count: Int) {
        val row = current()
        if (cursorColumn >= row.chars.length) return
        row.chars.insert(cursorColumn, " ".repeat(count))
        repeat(count) { row.styles.add(cursorColumn, CellStyle.Plain) }
        row.snapshot = null
    }

    private fun eraseChars(count: Int) {
        val row = current()
        for (i in cursorColumn until minOf(cursorColumn + count, row.chars.length)) {
            row.chars[i] = ' '
            row.styles[i] = CellStyle.Plain
        }
        row.snapshot = null
    }

    private fun newLine() {
        rows.addLast(Row())
        cursorColumn = 0
        while (rows.size > maxLines) rows.removeFirst()
    }

    private fun clearScreen() {
        rows.clear()
        rows.addLast(Row())
        cursorColumn = 0
    }

    // endregion

    private fun snapshot(row: Row): TerminalLine {
        if (row.chars.isEmpty()) return if (row.isNotice) TerminalLine("", emptyList(), true) else TerminalLine.Empty
        val runs = ArrayList<StyleRun>()
        var start = 0
        for (i in 1..row.styles.size) {
            if (i == row.styles.size || row.styles[i] != row.styles[start]) {
                if (row.styles[start] != CellStyle.Plain) runs += StyleRun(start, i, row.styles[start])
                start = i
            }
        }
        return TerminalLine(row.chars.toString(), runs, row.isNotice)
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
