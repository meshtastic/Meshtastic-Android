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
package org.meshtastic.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportFabTest {

    @Test
    fun normalizeImportContents_returnsNullForNull() {
        assertNull(normalizeImportContents(null))
    }

    @Test
    fun normalizeImportContents_returnsNullForBlank() {
        assertNull(normalizeImportContents(" \n\t "))
    }

    @Test
    fun normalizeImportContents_trimsSurroundingWhitespace() {
        val url = "https://meshtastic.org/e/#payload"

        assertEquals(url, normalizeImportContents(" \n$url\t"))
    }

    @Test
    fun normalizeImportContents_preservesValidUrlAfterTrim() {
        val url = "https://meshtastic.org/e/?add=true#payload"

        assertEquals(url, normalizeImportContents(url))
    }
}
