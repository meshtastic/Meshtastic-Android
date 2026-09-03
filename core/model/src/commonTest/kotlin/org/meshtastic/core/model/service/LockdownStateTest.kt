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
package org.meshtastic.core.model.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockdownStateTest {

    @Test
    fun `absence of lockdown status allows config writes`() {
        // Pre-2.8 firmware and newer builds without runtime lockdown support do not participate in the lockdown-status
        // handshake, so None does not withhold configuration writes on lockdown grounds.
        assertTrue(LockdownState.None.allowsConfigWrites)
    }

    @Test
    fun `lockdown capable but disabled devices allow config writes`() {
        assertTrue(LockdownState.Disabled.allowsConfigWrites)
    }

    @Test
    fun `unlocked devices allow config writes`() {
        assertTrue(LockdownState.Unlocked.allowsConfigWrites)
    }

    @Test
    fun `pending locked and not-yet-authorized states withhold config writes`() {
        assertFalse(LockdownState.AwaitingResponse.allowsConfigWrites)
        assertFalse(LockdownState.Locked("needs_auth").allowsConfigWrites)
        assertFalse(LockdownState.NeedsProvision.allowsConfigWrites)
        assertFalse(LockdownState.LockNowAcknowledged.allowsConfigWrites)
        assertFalse(LockdownState.UnlockFailed.allowsConfigWrites)
        assertFalse(LockdownState.UnlockBackoff(backoffSeconds = 10).allowsConfigWrites)
    }
}
