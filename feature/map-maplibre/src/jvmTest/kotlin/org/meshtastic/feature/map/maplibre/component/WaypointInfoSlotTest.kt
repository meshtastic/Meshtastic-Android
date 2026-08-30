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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.delete_for_me
import org.meshtastic.core.resources.edit
import org.meshtastic.core.resources.getString
import org.meshtastic.proto.Waypoint
import kotlin.test.Test

/**
 * The info dialog is the only route to the editor on this map — a long press always creates a new waypoint — so
 * anything it withholds is unreachable. The gates must match the Google flavor's, which dispatches on `isModifiableBy`.
 */
@OptIn(ExperimentalTestApi::class)
class WaypointInfoSlotTest {

    private val myNodeNum = 0x433D0D3C
    private val otherNodeNum = 0x1A2B3C4D

    @Composable
    private fun InfoSlot(waypoint: Waypoint, isConnected: Boolean) = WaypointInfoSlot(
        waypoint = waypoint,
        myNodeNum = myNodeNum,
        isConnected = isConnected,
        displayUnits = MeasurementSystem.METRIC,
        alertsEnabled = false,
        onToggleAlerts = {},
        onDismiss = {},
        onEdit = {},
        onDeleteForMe = {},
    )

    @Test
    fun `a waypoint locked to our own node is still editable`() = runComposeUiTest {
        setContent { InfoSlot(waypoint = Waypoint(id = 1, locked_to = myNodeNum), isConnected = true) }

        onNodeWithText(getString(Res.string.edit)).assertIsDisplayed()
    }

    @Test
    fun `an unlocked waypoint is editable while connected`() = runComposeUiTest {
        setContent { InfoSlot(waypoint = Waypoint(id = 1, locked_to = 0), isConnected = true) }

        onNodeWithText(getString(Res.string.edit)).assertIsDisplayed()
    }

    @Test
    fun `editing is withheld while disconnected because saving re-broadcasts`() = runComposeUiTest {
        setContent { InfoSlot(waypoint = Waypoint(id = 1, locked_to = 0), isConnected = false) }

        onNodeWithText(getString(Res.string.edit)).assertDoesNotExist()
    }

    @Test
    fun `a waypoint locked to another node offers no editor`() = runComposeUiTest {
        setContent { InfoSlot(waypoint = Waypoint(id = 1, locked_to = otherNodeNum), isConnected = true) }

        onNodeWithText(getString(Res.string.edit)).assertDoesNotExist()
    }

    @Test
    fun `a waypoint locked to another node can still be dropped locally`() = runComposeUiTest {
        // Dropping our own copy is not a mesh operation, so a foreign lock does not withhold it.
        setContent { InfoSlot(waypoint = Waypoint(id = 1, locked_to = otherNodeNum), isConnected = false) }

        onNodeWithText(getString(Res.string.delete_for_me)).assertIsDisplayed()
    }
}
