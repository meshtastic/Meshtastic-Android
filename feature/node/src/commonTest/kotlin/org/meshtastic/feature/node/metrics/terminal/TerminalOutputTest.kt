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

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val E = "\u001b"

private fun TerminalOutput.feed(text: String) = append(text.encodeUtf8())

private fun TerminalOutput.texts() = lines().map { it.text }

class TerminalOutputTest {

    @Test
    fun framesSplitMidLineJoinIntoOneLine() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("$ wh")
        out.feed("oami\r\njames\r\n$ ")
        assertEquals(listOf("$ whoami", "james", "$ "), out.texts())
        assertEquals(2, out.cursorColumn)
    }

    @Test
    fun aCharacterSplitAcrossFramesDecodesWhole() {
        val out = TerminalOutput(maxLines = 10)
        val bytes = "°C".encodeUtf8().toByteArray()
        out.append(bytes.copyOfRange(0, 1).toByteString())
        assertEquals(listOf(""), out.texts())
        out.append(bytes.copyOfRange(1, bytes.size).toByteString())
        assertEquals(listOf("°C"), out.texts())
    }

    @Test
    fun backspaceEchoErasesTheLastCharacter() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("lss\b \b")
        assertEquals(listOf("ls "), out.texts())
        assertEquals(2, out.cursorColumn)
    }

    @Test
    fun carriageReturnOverwritesLikeAProgressBar() {
        val out = TerminalOutput(maxLines = 10)
        out.feed(" 10%\r 55%\r100%\n")
        assertEquals(listOf("100%", ""), out.texts())
    }

    @Test
    fun eraseInLineClearsFromTheCursor() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("hello world\r$E[6C$E[K")
        assertEquals(listOf("hello "), out.texts())
    }

    @Test
    fun sgrColoursBecomeStyleRuns() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("$E[1;32mok$E[0m plain $E[38;5;208mx$E[38;2;1;2;3my$E[m")
        val line = out.lines().single()
        assertEquals("ok plain xy", line.text)
        assertEquals(
            listOf(
                StyleRun(0, 2, CellStyle(fg = 2, bold = true)),
                StyleRun(9, 10, CellStyle(fg = 208)),
                StyleRun(10, 11, CellStyle(fg = TRUECOLOR_FLAG or 0x010203)),
            ),
            line.runs,
        )
    }

    @Test
    fun aSequenceSplitAcrossFramesStillApplies() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("$E[3")
        out.feed("1mred")
        assertEquals(listOf(StyleRun(0, 3, CellStyle(fg = 1))), out.lines().single().runs)
    }

    @Test
    fun titleAndOtherStringsAreConsumed() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("$E]0;user@host: ~\u0007${E}P1\$r${E}\\$E(B$ ")
        assertEquals(listOf("$ "), out.texts())
    }

    @Test
    fun clearEmptiesTheScreen() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("one\r\ntwo\r\n$E[H$E[2J$ ")
        assertEquals(listOf("$ "), out.texts())
    }

    @Test
    fun fullScreenAndCursorKeyModesAreReported() {
        val out = TerminalOutput(maxLines = 10)
        assertTrue(out.feed("$E[?1049h$E[?1h").enteredFullScreen)
        assertTrue(out.applicationCursorKeys)
        assertFalse(out.feed("$E[?1l").enteredFullScreen)
        assertFalse(out.applicationCursorKeys)
    }

    @Test
    fun bellIsReportedNotPrinted() {
        val out = TerminalOutput(maxLines = 10)
        assertTrue(out.feed("a\u0007b").bell)
        assertEquals(listOf("ab"), out.texts())
    }

    @Test
    fun tabsAdvanceToTheNextStop() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("a\tb")
        assertEquals(listOf("a       b"), out.texts())
    }

    @Test
    fun aNoticeTakesItsOwnLineAndLeavesAFreshOne() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("$ ")
        out.notice("Session closed")
        assertEquals(listOf("$ ", "Session closed", ""), out.texts())
        assertTrue(out.lines()[1].isNotice)
    }

    @Test
    fun oldestLinesAreDroppedPastTheLimit() {
        val out = TerminalOutput(maxLines = 3)
        out.feed("1\n2\n3\n4")
        assertEquals(listOf("2", "3", "4"), out.texts())
    }

    @Test
    fun aHugeCursorMoveIsClampedToTheLineWidth() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("$E[999999999Cx")
        val line = out.lines().last()
        assertEquals(MAX_LINE_WIDTH, line.text.length)
        assertEquals('x', line.text.last())
    }

    @Test
    fun printingPastTheLineWidthWraps() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("$E[${MAX_LINE_WIDTH}Gab")
        assertEquals(listOf(MAX_LINE_WIDTH, 1), out.lines().map { it.text.length })
    }

    @Test
    fun aHugeInsertCountIsClampedToTheLineWidth() {
        val out = TerminalOutput(maxLines = 10)
        out.feed("abc\r$E[999999999@")
        assertEquals(MAX_LINE_WIDTH, out.lines().last().text.length)
    }

    @Test
    fun printedCharactersAreReportedForEchoMatching() {
        val out = TerminalOutput(maxLines = 10)
        val echo = out.feed("ab\b \b").echo
        assertEquals(
            listOf(
                EchoEvent.Printed('a'),
                EchoEvent.Printed('b'),
                EchoEvent.Edited,
                EchoEvent.Printed(' '),
                EchoEvent.Edited,
            ),
            echo,
        )
    }
}
