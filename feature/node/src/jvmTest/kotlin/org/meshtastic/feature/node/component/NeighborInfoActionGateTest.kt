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
package org.meshtastic.feature.node.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.neighbor_info
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test

/** Node detail offers the neighbor-info request only while the connected radio has the module enabled. */
@OptIn(ExperimentalTestApi::class)
class NeighborInfoActionGateTest {

    private fun showActions(canRequestNeighborInfo: Boolean) = runComposeUiTest {
        setContent {
            AppTheme {
                DeviceActions(
                    node = Node(num = 2),
                    ourNode = Node(num = 1),
                    lastTracerouteTime = null,
                    lastRequestNeighborsTime = null,
                    availableLogs = emptySet(),
                    onAction = {},
                    displayUnits = MeasurementSystem.METRIC,
                    isFahrenheit = false,
                    canRequestNeighborInfo = canRequestNeighborInfo,
                )
            }
        }

        val row = onNodeWithText(getString(Res.string.neighbor_info))
        if (canRequestNeighborInfo) row.assertExists() else row.assertDoesNotExist()
    }

    @Test fun `node detail offers the neighbor info request when the radio has the module on`() = showActions(true)

    @Test fun `node detail hides the neighbor info request while the radio has the module off`() = showActions(false)
}
