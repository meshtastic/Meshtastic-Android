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
package org.meshtastic.feature.settings.radio.component

import kotlin.test.Test
import kotlin.test.assertEquals

class TakServerStatusTest {

    @Test
    fun `disabled server is off`() {
        assertEquals(TakServerStatus.Off, TakServerStatus.resolve(true, false, false, false, 0, false))
    }

    @Test
    fun `enabled server reports startup failure`() {
        assertEquals(TakServerStatus.Failed, TakServerStatus.resolve(true, true, false, false, 0, true))
    }

    @Test
    fun `enabled server reports starting until it listens`() {
        assertEquals(TakServerStatus.Starting, TakServerStatus.resolve(true, true, false, true, 0, false))
    }

    @Test
    fun `listening server distinguishes waiting and connected clients`() {
        assertEquals(TakServerStatus.WaitingForClient, TakServerStatus.resolve(true, true, true, false, 0, false))
        assertEquals(TakServerStatus.Connected, TakServerStatus.resolve(true, true, true, false, 1, false))
    }

    @Test
    fun `stopped server ignores stale client count`() {
        assertEquals(TakServerStatus.NotRunning, TakServerStatus.resolve(true, true, false, false, 1, false))
    }

    @Test
    fun `unsupported platform and inactive service report their distinct states`() {
        assertEquals(TakServerStatus.Unavailable, TakServerStatus.resolve(false, true, false, false, 0, false))
        assertEquals(TakServerStatus.NotRunning, TakServerStatus.resolve(true, true, false, false, 0, false))
    }
}
