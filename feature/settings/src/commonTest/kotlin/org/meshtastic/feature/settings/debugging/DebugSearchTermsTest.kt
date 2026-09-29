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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugSearchTermsTest {

    @Test
    fun `blank terms from repeated spaces are dropped`() {
        assertEquals(2, compileSearchTerms("alpha  beta ").size)
        assertTrue(compileSearchTerms("").isEmpty())
    }

    @Test
    fun `terms match literally and ignore case`() {
        val (term) = compileSearchTerms("a.b")

        assertTrue(term.containsMatchIn("xA.By"))
        assertFalse(term.containsMatchIn("axb"))
    }
}
