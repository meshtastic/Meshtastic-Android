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
package org.meshtastic.core.navigation

import org.meshtastic.core.common.util.CommonUri
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationDeepLinksTest {

    private fun route(path: String) = DeepLinkRouter.route(CommonUri.parse(NotificationDeepLinks.uri(path)))

    @Test
    fun `every notification destination routes`() {
        assertEquals(
            listOf(ContactsRoute.Contacts, ContactsRoute.Messages("0^all")),
            route(NotificationDeepLinks.conversation("0^all")),
        )
        assertEquals(
            listOf(NodesRoute.Nodes, NodesRoute.NodeDetail(destNum = 42)),
            route(NotificationDeepLinks.nodeDetail(42)),
        )
        assertEquals(listOf(MapRoute.Map(waypointId = 7)), route(NotificationDeepLinks.waypoint(7)))
        assertEquals(listOf(DiscoveryRoute.DiscoveryGraph), route(NotificationDeepLinks.MESH_BEACON))
        assertEquals(
            listOf(FirmwareRoute.FirmwareGraph, FirmwareRoute.FirmwareUpdate),
            route(NotificationDeepLinks.FIRMWARE_UPDATE),
        )
        assertEquals(listOf(ConnectionsRoute.Connections()), route(NotificationDeepLinks.CONNECTIONS))
    }
}
