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

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the RUM view-name format that Datadog dashboards and monitors filter `@view.name` on. */
class RumViewNameTest {

    @Test
    fun `rumViewName drops the package and keeps the enclosing route`() {
        assertEquals("NodesRoute.Nodes", NodesRoute.Nodes.rumViewName())
        assertEquals("SettingsRoute.Bluetooth", SettingsRoute.Bluetooth.rumViewName())
    }

    @Test
    fun `rumViewName is stable across argument values for data classes`() {
        val expected = "NodeDetailRoute.DeviceMetrics"
        assertEquals(expected, NodeDetailRoute.DeviceMetrics(destNum = 1).rumViewName())
        assertEquals(expected, NodeDetailRoute.DeviceMetrics(destNum = 2).rumViewName())
    }

    @Test
    fun `rumViewName of a top level key is its simple name`() {
        assertEquals("TopLevelKey", TopLevelKey.rumViewName())
    }

    @Test
    fun `binary names from minified builds match the qualified form`() {
        assertEquals("NodesRoute.Nodes", rumViewName("org.meshtastic.core.navigation.NodesRoute\$Nodes"))
    }

    @Test
    fun `a name with no uppercase segment is kept whole`() {
        assertEquals("a.b", rumViewName("a.b"))
    }
}

private data object TopLevelKey : NavKey
