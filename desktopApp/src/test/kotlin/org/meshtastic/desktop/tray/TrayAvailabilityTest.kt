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
package org.meshtastic.desktop.tray

import org.meshtastic.desktop.notification.DesktopOS
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrayAvailabilityTest {

    @Test
    fun `hides to tray on macOS when AWT reports a tray`() {
        assertTrue(canHideToTray(awtTraySupported = true, sniTrayRegistered = false, os = DesktopOS.MacOS))
    }

    @Test
    fun `hides to tray on Windows when AWT reports a tray`() {
        assertTrue(canHideToTray(awtTraySupported = true, sniTrayRegistered = false, os = DesktopOS.Windows))
    }

    @Test
    fun `quits rather than hiding on Linux when only AWT reports a tray`() {
        // The regression this guards: GNOME and Plasma render AWT's icon through a proxy that swallows
        // clicks, so a reported tray is not a reachable one and hiding into it leaves the app unquittable.
        assertFalse(canHideToTray(awtTraySupported = true, sniTrayRegistered = false, os = DesktopOS.Linux))
    }

    @Test
    fun `hides to tray on Linux once the StatusNotifierItem registered`() {
        // SniTray owns the icon over D-Bus here, so Activate and the dbusmenu actually arrive.
        assertTrue(canHideToTray(awtTraySupported = false, sniTrayRegistered = true, os = DesktopOS.Linux))
    }

    @Test
    fun `never hides to tray on macOS or Windows when AWT reports no tray`() {
        listOf(DesktopOS.MacOS, DesktopOS.Windows).forEach { os ->
            assertFalse(canHideToTray(awtTraySupported = false, sniTrayRegistered = true, os = os))
        }
    }
}
