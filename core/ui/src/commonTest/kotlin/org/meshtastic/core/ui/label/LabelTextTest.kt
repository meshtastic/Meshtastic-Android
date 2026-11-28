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
package org.meshtastic.core.ui.label

import kotlin.test.Test
import kotlin.test.assertEquals

/** Code points are placed by the Unicode block charts. */
class LabelTextTest {

    @Test
    fun `ideographs past the emoji blocks draw as text and emoji in every emoji block draw as emoji`() {
        val text = listOf(0x20000, 0x2A700, 0x1FB00)
        val emoji = listOf(0x1F004, 0x1F1FA, 0x1F41B, 0x1FAE0, 0x2600, 0x2B50, 0xE0067)

        assertEquals(text.map { false } + emoji.map { true }, (text + emoji).map { isLabelEmoji(it) })
    }

    @Test
    fun `line breaks and tabs in a name still part its words`() {
        assertEquals(
            listOf("Wandering Packet", "Wandering Packet Goblin"),
            listOf("Wandering\nPacket", "Wandering\r\n\tPacket\u000BGoblin").map { cleanLabelText(it) },
        )
    }
}
