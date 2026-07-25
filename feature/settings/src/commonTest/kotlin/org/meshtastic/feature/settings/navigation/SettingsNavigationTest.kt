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
package org.meshtastic.feature.settings.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsNavigationTest {

    @Test
    fun `settings destination follows the latest settings root through submenus`() {
        val stack =
            listOf<NavKey>(
                SettingsRoute.Settings(destNum = 1234),
                SettingsRoute.DeviceConfiguration,
                SettingsRoute.LoRa,
            )

        assertEquals(1234, settingsDestination(stack))
    }

    @Test
    fun `settings destination returns to local for a newer local root`() {
        val stack =
            listOf<NavKey>(
                SettingsRoute.Settings(destNum = 1234),
                SettingsRoute.DeviceConfiguration,
                SettingsRoute.Settings(),
                SettingsRoute.ModuleConfiguration,
            )

        assertNull(settingsDestination(stack))
    }

    @Test
    fun `settings session uses stable local and remote keys`() {
        val localSession = settingsRadioConfigSession(listOf(SettingsRoute.Settings()))
        val remoteSession = settingsRadioConfigSession(listOf(SettingsRoute.Settings(destNum = 1234)))

        assertNull(localSession.destination)
        assertEquals("settings-local", localSession.viewModelKey)
        assertNull(localSession.entryKey)
        assertEquals(1234, remoteSession.destination)
        assertEquals("settings-remote-1234", remoteSession.viewModelKey)
        assertEquals("1234", remoteSession.entryKey)
    }

    @Test
    fun `clearing a settings session store clears its view models`() {
        val owner = SettingsRadioConfigViewModelStoreOwner()
        val viewModel = TrackingViewModel()
        owner.viewModelStore.put("remote", viewModel)

        owner.clear()

        assertTrue(viewModel.wasCleared)
    }

    @Test
    fun `duplicate current route is not pushed again`() {
        assertFalse(shouldAddSettingsRoute(SettingsRoute.DeviceConfiguration, SettingsRoute.DeviceConfiguration))
        assertTrue(shouldAddSettingsRoute(SettingsRoute.DeviceConfiguration, SettingsRoute.ModuleConfiguration))
    }

    private class TrackingViewModel : ViewModel() {
        var wasCleared = false
            private set

        override fun onCleared() {
            wasCleared = true
        }
    }
}
