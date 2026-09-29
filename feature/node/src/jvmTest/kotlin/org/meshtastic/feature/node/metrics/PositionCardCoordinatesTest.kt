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
package org.meshtastic.feature.node.metrics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.proto.Position
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PositionCardCoordinatesTest {

    @Test
    fun `a position without latitude shows no coordinates`() = runComposeUiTest {
        setPositionCard(latitude = null, longitude = 134_050_000)

        assertNoCoordinates()
    }

    @Test
    fun `a position without longitude shows no coordinates`() = runComposeUiTest {
        setPositionCard(latitude = 525_200_000, longitude = null)

        assertNoCoordinates()
    }

    @Test
    fun `a position at zero latitude and zero longitude shows no coordinates`() = runComposeUiTest {
        setPositionCard(latitude = 0, longitude = 0)

        assertNoCoordinates()
    }

    @Test
    fun `a position on the equator shows both coordinates`() = runComposeUiTest {
        setPositionCard(latitude = 0, longitude = 134_050_000)

        onNodeWithText(coordinate(Res.string.latitude, 0.0), useUnmergedTree = true).assertExists()
        onNodeWithText(coordinate(Res.string.longitude, 13.405), useUnmergedTree = true).assertExists()
    }

    private fun ComposeUiTest.assertNoCoordinates() {
        onNodeWithText(getString(Res.string.latitude), substring = true, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(getString(Res.string.longitude), substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun coordinate(label: StringResource, degrees: Double) =
        "${getString(label)}: ${formatString("%.5f", degrees)}"

    private fun ComposeUiTest.setPositionCard(latitude: Int?, longitude: Int?) {
        val position =
            Position.Builder()
                .also { wb ->
                    wb.latitude_i = latitude
                    wb.longitude_i = longitude
                    wb.time = 1_700_000_000
                }
                .build()
        setContent {
            MaterialTheme {
                PositionCard(
                    position = position,
                    displayUnits = MeasurementSystem.METRIC,
                    isSelected = false,
                    onClick = {},
                )
            }
        }
    }
}
