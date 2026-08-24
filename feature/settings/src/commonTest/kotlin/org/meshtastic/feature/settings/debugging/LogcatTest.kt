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
import kotlin.test.assertNull

class LogcatTest {

    private val sample =
        """
        01-02 03:04:05.678 D/Tag(  123): debug line
        01-02 03:04:05.679 E/Boom( 123): error line
        01-02 03:04:05.680 F/Fatal(123): fatal line
            at continuation.frame(File.kt:1)
        """
            .trimIndent()

    @Test
    fun `logcatLineLevel reads the priority letter`() {
        assertEquals('D', logcatLineLevel("01-02 03:04:05.678 D/Tag(  123): hi"))
        assertNull(logcatLineLevel("    at continuation.frame(File.kt:1)"))
    }

    @Test
    fun `filterLogcat hides deselected known levels but keeps unknown and continuation lines`() {
        // Everything selected: all non-blank lines pass.
        assertEquals(4, filterLogcat(sample, LogLevel.entries.toSet(), "").size)

        // Deselect DEBUG: the D line drops; E, F (unknown toggle), and the continuation line stay.
        val noDebug = filterLogcat(sample, LogLevel.entries.toSet() - LogLevel.DEBUG, "")
        assertEquals(3, noDebug.size)
    }

    @Test
    fun `filterLogcat applies case-insensitive query`() {
        assertEquals(1, filterLogcat(sample, LogLevel.entries.toSet(), "ERROR LINE").size)
    }
}
