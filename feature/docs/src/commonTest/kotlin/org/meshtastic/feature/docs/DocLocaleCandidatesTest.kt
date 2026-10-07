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
package org.meshtastic.feature.docs

import org.meshtastic.feature.docs.data.docLocaleCandidates
import kotlin.test.Test
import kotlin.test.assertEquals

class DocLocaleCandidatesTest {

    @Test
    fun `a region outside the translated one still falls back to the language`() {
        assertEquals(listOf("de-rAT", "de"), docLocaleCandidates("de-AT"))
    }

    @Test
    fun `a script is tried before the region`() {
        assertEquals(listOf("b+sr+Latn", "sr-rRS", "sr"), docLocaleCandidates("sr-Latn-RS"))
        assertEquals(listOf("b+zh+Hant", "zh-rTW", "zh"), docLocaleCandidates("zh-Hant-TW"))
    }

    @Test
    fun `retired Hebrew and Indonesian codes map to the current ones`() {
        assertEquals(listOf("he-rIL", "he"), docLocaleCandidates("iw-IL"))
        assertEquals(listOf("id"), docLocaleCandidates("in"))
    }

    @Test
    fun `a numeric region and an empty tag are skipped`() {
        assertEquals(listOf("es"), docLocaleCandidates("es-419"))
        assertEquals(emptyList(), docLocaleCandidates(""))
    }
}
