/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IntroViewModelTest {

    @Test
    fun `viewModel can be initialized`() {
        val viewModel = IntroViewModel()
        assertNotNull(viewModel)
    }

    @Test
    fun `getNextKey returns Bluetooth after Welcome`() {
        val viewModel = IntroViewModel()
        val next = viewModel.getNextKey(Welcome, false)
        assertEquals(Bluetooth, next)
    }

    @Test
    fun `getNextKey returns Location after Bluetooth`() {
        val viewModel = IntroViewModel()
        val next = viewModel.getNextKey(Bluetooth, false)
        assertEquals(Location, next)
    }

    @Test
    fun `getNextKey returns Notifications after Location`() {
        val viewModel = IntroViewModel()
        val next = viewModel.getNextKey(Location, false)
        assertEquals(Notifications, next)
    }

    @Test
    fun `getNextKey returns CriticalAlerts after Notifications if granted`() {
        val viewModel = IntroViewModel()
        val next = viewModel.getNextKey(Notifications, true)
        assertEquals(CriticalAlerts, next)
    }

    @Test
    fun `getNextKey returns null after Notifications if not granted`() {
        val viewModel = IntroViewModel()
        val next = viewModel.getNextKey(Notifications, false)
        assertNull(next)
    }

    @Test
    fun `getNextKey returns null after CriticalAlerts`() {
        val viewModel = IntroViewModel()
        val next = viewModel.getNextKey(CriticalAlerts, false)
        assertNull(next)
    }
}
