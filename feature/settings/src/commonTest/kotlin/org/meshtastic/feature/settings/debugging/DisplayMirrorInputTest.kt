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
package org.meshtastic.feature.settings.debugging

import org.meshtastic.core.ui.input.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayMirrorInputTest {

    private fun codePoints(text: String): List<Int> = mutableListOf<Int>().also { out ->
        forEachCodePoint(text) { out += it }
    }

    @Test
    fun plainTextIsOneCodePointPerCharacter() {
        assertEquals(listOf('h'.code, 'i'.code, ' '.code), codePoints("hi "))
    }

    @Test
    fun aSurrogatePairIsOneCodePoint() {
        assertEquals(listOf(0x1F600), codePoints("😀"))
        assertEquals(listOf('a'.code, 0x1F600, 'b'.code), codePoints("a😀b"))
    }

    @Test
    fun aLoneSurrogatePassesThroughUnchanged() {
        assertEquals(listOf(0xD83D, 'x'.code), codePoints("\uD83Dx"))
    }

    @Test
    fun navigationKeysMapToDeviceEvents() {
        assertEquals(INPUT_UP, RemoteKey.UP.toInputEvent())
        assertEquals(INPUT_DOWN, RemoteKey.DOWN.toInputEvent())
        assertEquals(INPUT_LEFT, RemoteKey.LEFT.toInputEvent())
        assertEquals(INPUT_RIGHT, RemoteKey.RIGHT.toInputEvent())
        assertEquals(INPUT_BACK, RemoteKey.ESCAPE.toInputEvent())
    }

    @Test
    fun terminalOnlyKeysMapToNothing() {
        assertNull(RemoteKey.HOME.toInputEvent())
        assertNull(RemoteKey.PAGE_DOWN.toInputEvent())
        assertNull(RemoteKey.DELETE.toInputEvent())
    }
}
