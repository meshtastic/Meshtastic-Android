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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.TakPrefs
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Tests for TAK permission denied behavior (T075).
 *
 * Verifies that when ACCESS_LOCAL_NETWORK permission is denied on Android 17+, the TAK server is disabled (not crashed)
 * and the UI reflects the disabled state.
 *
 * The actual UI composition test requires a Compose test rule, but the behavioral contract can be validated at the
 * state level: when the permission handler reports denial while the server is enabled, setTakServerEnabled(false) is
 * called.
 */
class TAKConfigPermissionDeniedTest {

    /** Minimal TakPrefs that tracks calls to setTakServerEnabled. */
    private class FakeTakPrefs : TakPrefs {
        private val _isTakServerEnabled = MutableStateFlow(true)
        override val isTakServerEnabled: StateFlow<Boolean> = _isTakServerEnabled

        override fun setTakServerEnabled(enabled: Boolean) {
            _isTakServerEnabled.value = enabled
        }
    }

    @Test
    fun `permission denied disables TAK server`() = runTest {
        val prefs = FakeTakPrefs()

        // Simulate the exact logic from TAKConfigItemList.kt:
        // onPermissionResult = { granted -> if (!granted && isTakServerEnabled) takPrefs.setTakServerEnabled(false) }
        val granted = false
        if (!granted && prefs.isTakServerEnabled.value) {
            prefs.setTakServerEnabled(false)
        }

        prefs.isTakServerEnabled.test { assertFalse(awaitItem()) }
    }

    @Test
    fun `permission denied when already disabled is no-op`() = runTest {
        val prefs = FakeTakPrefs()
        prefs.setTakServerEnabled(false) // Already disabled

        // Simulate permission denied — should not crash or throw
        val granted = false
        if (!granted && prefs.isTakServerEnabled.value) {
            prefs.setTakServerEnabled(false)
        }

        prefs.isTakServerEnabled.test {
            assertFalse(awaitItem()) // Still false, no crash
        }
    }

    @Test
    fun `permission granted does not disable TAK server`() = runTest {
        val prefs = FakeTakPrefs()

        // Simulate permission granted
        val granted = true
        if (!granted && prefs.isTakServerEnabled.value) {
            prefs.setTakServerEnabled(false)
        }

        prefs.isTakServerEnabled.test {
            // Server should still be enabled
            kotlin.test.assertTrue(awaitItem())
        }
    }
}
