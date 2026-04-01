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
package org.meshtastic.feature.firmware

import org.meshtastic.core.resources.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirmwareUpdateStateTest {

    @Test
    fun `ProgressState defaults are correct`() {
        val state = ProgressState()
        assertTrue(state.message is UiText.DynamicString)
        assertEquals(0f, state.progress)
        assertEquals(null, state.details)
    }

    @Test
    fun `ProgressState can be instantiated with values`() {
        val state = ProgressState(UiText.DynamicString("Downloading"), 0.5f, "1MB/s")
        assertTrue(state.message is UiText.DynamicString)
        assertEquals(0.5f, state.progress)
        assertEquals("1MB/s", state.details)
    }

    @Test
    fun `stripFormatArgs removes positional format argument`() {
        assertEquals("Battery low", "Battery low: %1\$d%".stripFormatArgs())
    }

    @Test
    fun `stripFormatArgs removes format arg without colon prefix`() {
        assertEquals("Battery low", "Battery low %1\$d".stripFormatArgs())
    }

    @Test
    fun `stripFormatArgs leaves string without format args unchanged`() {
        assertEquals("No args here", "No args here".stripFormatArgs())
    }

    @Test
    fun `stripFormatArgs handles empty string`() {
        assertEquals("", "".stripFormatArgs())
    }
}
