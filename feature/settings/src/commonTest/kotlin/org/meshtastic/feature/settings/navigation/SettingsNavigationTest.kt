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
    fun `duplicate current route is not pushed again`() {
        assertFalse(shouldAddSettingsRoute(SettingsRoute.DeviceConfiguration, SettingsRoute.DeviceConfiguration))
        assertTrue(shouldAddSettingsRoute(SettingsRoute.DeviceConfiguration, SettingsRoute.ModuleConfiguration))
    }
}
