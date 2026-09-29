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
package org.meshtastic.feature.settings.lockdown

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockdownPassphraseValidationTest {

    @Test
    fun `an empty passphrase is rejected`() {
        assertFalse(isValidLockdownPassphrase(""))
    }

    @Test
    fun `the firmware limit is counted in bytes not characters`() {
        assertTrue(isValidLockdownPassphrase("a".repeat(64)))
        assertFalse(isValidLockdownPassphrase("a".repeat(65)))
        // Two bytes each in UTF-8: 32 fit, 33 do not.
        assertTrue(isValidLockdownPassphrase("é".repeat(32)))
        assertFalse(isValidLockdownPassphrase("é".repeat(33)))
    }
}
