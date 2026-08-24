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

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeTakPrefs
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Tests for TAK permission denied behavior (T075).
 *
 * Verifies that when ACCESS_LOCAL_NETWORK permission is denied on Android 17+, the TAK server is disabled (not crashed)
 * and the UI reflects the disabled state.
 *
 * The actual UI composition test requires a Compose test rule, but these drive the exact function
 * [handleTakPermissionResult] that [TakServerScreen]'s [TakPermissionHandler] callback calls, rather than duplicating
 * its conditional — so a change to that production logic fails these tests instead of passing silently.
 */
class TAKConfigPermissionDeniedTest {

    @Test
    fun `permission denied disables TAK server`() = runTest {
        val prefs = FakeTakPrefs()
        prefs.isTakServerEnabled.value = true // Scenario starts with the server already enabled.

        handleTakPermissionResult(
            granted = false,
            isTakServerEnabled = prefs.isTakServerEnabled.value,
            takPrefs = prefs,
        )

        prefs.isTakServerEnabled.test { assertFalse(awaitItem()) }
    }

    @Test
    fun `permission denied when already disabled is no-op`() = runTest {
        val prefs = FakeTakPrefs()
        prefs.setTakServerEnabled(false) // Already disabled

        // Should not crash or throw
        handleTakPermissionResult(
            granted = false,
            isTakServerEnabled = prefs.isTakServerEnabled.value,
            takPrefs = prefs,
        )

        prefs.isTakServerEnabled.test {
            assertFalse(awaitItem()) // Still false, no crash
        }
    }

    @Test
    fun `permission granted does not disable TAK server`() = runTest {
        val prefs = FakeTakPrefs()
        prefs.isTakServerEnabled.value = true // Scenario starts with the server already enabled.

        handleTakPermissionResult(granted = true, isTakServerEnabled = prefs.isTakServerEnabled.value, takPrefs = prefs)

        prefs.isTakServerEnabled.test {
            // Server should still be enabled
            kotlin.test.assertTrue(awaitItem())
        }
    }
}
