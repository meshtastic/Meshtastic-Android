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

class TerminalOutputTest {

    @Test
    fun framesSplitMidLineJoinIntoOneLine() {
        val out = TerminalOutput(maxLines = 10)
        out.append("$ wh".encodeUtf8())
        out.append("oami\r\njames\r\n$ ".encodeUtf8())
        assertEquals(listOf("$ whoami", "james", "$ "), out.lines())
    }

    @Test
    fun aCharacterSplitAcrossFramesDecodesWhole() {
        val out = TerminalOutput(maxLines = 10)
        val bytes = "°C".encodeUtf8().toByteArray()
        out.append(bytes.copyOfRange(0, 1).toByteString())
        assertEquals(listOf(""), out.lines())
        out.append(bytes.copyOfRange(1, bytes.size).toByteString())
        assertEquals(listOf("°C"), out.lines())
    }

    @Test
    fun backspaceEchoErasesTheLastCharacter() {
        val out = TerminalOutput(maxLines = 10)
        out.append("lss\b \b".encodeUtf8())
        assertEquals(listOf("ls"), out.lines())
    }

    @Test
    fun aNoticeTakesItsOwnLineAndLeavesAFreshOne() {
        val out = TerminalOutput(maxLines = 10)
        out.append("$ ".encodeUtf8())
        out.notice("[session closed]")
        assertEquals(listOf("$ ", "[session closed]", ""), out.lines())
    }

    @Test
    fun oldestLinesAreDroppedPastTheLimit() {
        val out = TerminalOutput(maxLines = 3)
        out.append("1\n2\n3\n4".encodeUtf8())
        assertEquals(listOf("2", "3", "4"), out.lines())
    }
}
