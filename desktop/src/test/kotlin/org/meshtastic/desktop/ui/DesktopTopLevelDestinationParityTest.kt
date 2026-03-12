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
package org.meshtastic.desktop.ui

import org.meshtastic.core.navigation.ConnectionsRoutes
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.FirmwareRoutes
import org.meshtastic.core.navigation.MapRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.navigation.TopLevelDestination
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Keeps Desktop top-level destinations aligned with Android top-level navigation (Conversations, Nodes, Map, Settings,
 * Connections).
 */
class DesktopTopLevelDestinationParityTest {

    @Test
    fun `desktop top-level routes match android parity set`() {
        val desktopRoutes: Set<KClass<out Route>> = TopLevelDestination.entries.map { it.route::class }.toSet()

        val androidParityRoutes: Set<KClass<out Route>> =
            setOf(
                ContactsRoutes.ContactsGraph::class,
                NodesRoutes.NodesGraph::class,
                MapRoutes.Map::class,
                SettingsRoutes.SettingsGraph::class,
                ConnectionsRoutes.ConnectionsGraph::class,
            )

        assertEquals(
            expected = androidParityRoutes,
            actual = desktopRoutes,
            message = "Desktop top-level destinations must stay aligned with Android parity set",
        )
    }

    @Test
    fun `firmware is not a desktop top-level destination`() {
        val desktopRoutes: Set<KClass<out Route>> = TopLevelDestination.entries.map { it.route::class }.toSet()

        assertFalse(
            actual = desktopRoutes.contains(FirmwareRoutes.FirmwareGraph::class),
            message = "Firmware must stay in-flow and not appear in the desktop top-level rail",
        )
    }
}
