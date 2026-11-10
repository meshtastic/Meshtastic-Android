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
package org.meshtastic.feature.settings.radio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioConfigRowsEnabledTest {

    @Test
    fun `local config rows stay readable while disconnected`() {
        assertTrue(radioConfigRowsReadable(connected = false, isLocal = true, isWaiting = false, isManaged = false))
    }

    @Test
    fun `remote config rows need a connection`() {
        assertFalse(radioConfigRowsReadable(connected = false, isLocal = false, isWaiting = false, isManaged = false))
        assertTrue(radioConfigRowsReadable(connected = true, isLocal = false, isWaiting = false, isManaged = false))
    }

    @Test
    fun `managed mode and pending responses lock config rows`() {
        assertFalse(radioConfigRowsReadable(connected = true, isLocal = true, isWaiting = false, isManaged = true))
        assertFalse(radioConfigRowsReadable(connected = true, isLocal = true, isWaiting = true, isManaged = false))
    }

    @Test
    fun `action rows need a connection`() {
        assertFalse(radioActionRowsEnabled(connected = false, isWaiting = false, isManaged = false))
        assertTrue(radioActionRowsEnabled(connected = true, isWaiting = false, isManaged = false))
        assertFalse(radioActionRowsEnabled(connected = true, isWaiting = false, isManaged = true))
    }
}
