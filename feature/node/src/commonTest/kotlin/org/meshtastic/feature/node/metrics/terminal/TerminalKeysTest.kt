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

import org.meshtastic.core.ui.input.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalKeysTest {

    @Test
    fun cursorKeysFollowTheApplicationCursorMode() {
        assertEquals("\u001b[A", RemoteKey.UP.sequence(applicationCursorKeys = false))
        assertEquals("\u001bOA", RemoteKey.UP.sequence(applicationCursorKeys = true))
        assertEquals("\u001b[5~", RemoteKey.PAGE_UP.sequence(applicationCursorKeys = true))
    }

    @Test
    fun ctrlMapsLettersAndPunctuationToControlCodes() {
        val ctrl = Modifiers(ctrl = ModifierState.ONCE)
        assertEquals("\u0003", 'c'.withModifiers(ctrl))
        assertEquals("\u0003", 'C'.withModifiers(ctrl))
        assertEquals("\u001b", '['.withModifiers(ctrl))
        assertEquals("\u007f", '?'.withModifiers(ctrl))
        assertEquals("1", '1'.withModifiers(ctrl))
    }

    @Test
    fun altPrefixesEscape() {
        assertEquals("\u001bb", 'b'.withModifiers(Modifiers(alt = ModifierState.ONCE)))
        assertEquals("\u001b\u0002", 'b'.withModifiers(Modifiers(ModifierState.ONCE, ModifierState.LOCKED)))
    }

    @Test
    fun aOneShotModifierReleasesAndALockedOneStays() {
        val mods = Modifiers(ctrl = ModifierState.ONCE, alt = ModifierState.LOCKED).consumed()
        assertEquals(Modifiers(ctrl = ModifierState.OFF, alt = ModifierState.LOCKED), mods)
    }

    @Test
    fun aTapCyclesOffOnceLocked() {
        assertEquals(ModifierState.ONCE, ModifierState.OFF.next())
        assertEquals(ModifierState.LOCKED, ModifierState.ONCE.next())
        assertEquals(ModifierState.OFF, ModifierState.LOCKED.next())
    }
}
