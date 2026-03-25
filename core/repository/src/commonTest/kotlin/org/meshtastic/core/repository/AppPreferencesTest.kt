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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.core.testing.FakeRadioPrefs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPreferencesTest {

    @Test
    fun `RadioPrefs isBle returns true for x prefix`() {
        val prefs = FakeRadioPrefs()
        prefs.setDevAddr("x12345678")
        assertTrue(prefs.isBle())
    }

    @Test
    fun `RadioPrefs isBle returns false for other prefix`() {
        val prefs = FakeRadioPrefs()
        prefs.setDevAddr("s12345678")
        assertFalse(prefs.isBle())
    }

    @Test
    fun `RadioPrefs isSerial returns true for s prefix`() {
        val prefs = FakeRadioPrefs()
        prefs.setDevAddr("s12345678")
        assertTrue(prefs.isSerial())
    }

    @Test
    fun `RadioPrefs isTcp returns true for t prefix`() {
        val prefs = FakeRadioPrefs()
        prefs.setDevAddr("t192.168.1.1")
        assertTrue(prefs.isTcp())
    }

    @Test
    fun `RadioPrefs isMock returns true for m prefix`() {
        val prefs = FakeRadioPrefs()
        prefs.setDevAddr("m12345678")
        assertTrue(prefs.isMock())
    }
}
