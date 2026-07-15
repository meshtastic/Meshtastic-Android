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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the RUM view-name convention consumed by the analytics layer. View names must be the route's fully-qualified
 * class name so per-screen RUM data lines up with historical Datadog dashboards. A rename of a route interface or the
 * package would break cross-platform data continuity, so this test pins the format.
 */
class RumViewNameTest {

    @Test
    fun `rumViewName is the fully qualified route name for data objects`() {
        assertEquals("org.meshtastic.core.navigation.NodesRoute.Nodes", NodesRoute.Nodes.rumViewName())
    }

    @Test
    fun `rumViewName is stable across argument values for data classes`() {
        val expected = "org.meshtastic.core.navigation.NodeDetailRoute.DeviceMetrics"
        assertEquals(expected, NodeDetailRoute.DeviceMetrics(destNum = 1).rumViewName())
        assertEquals(expected, NodeDetailRoute.DeviceMetrics(destNum = 2).rumViewName())
    }
}
