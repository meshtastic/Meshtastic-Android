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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.i_agree
import org.meshtastic.core.resources.map_reporting
import org.meshtastic.core.resources.map_reporting_summary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MapReportingPreferenceTest {

    var mapReportingEnabled = false
    var shouldReportLocation = false
    var positionPrecision = 5
    var positionReportingInterval = 60

    var mapReportingEnabledChanged = { enabled: Boolean -> mapReportingEnabled = enabled }
    var shouldReportLocationChanged = { enabled: Boolean -> shouldReportLocation = enabled }
    var positionPrecisionChanged = { precision: Int -> positionPrecision = precision }
    var positionReportingIntervalChanged = { interval: Int -> positionReportingInterval = interval }

    @Test
    fun testMapReportingPreference_showsText() = runComposeUiTest {
        setContent {
            Column {
                MapReportingPreference(
                    mapReportingEnabled = mapReportingEnabled,
                    shouldReportLocation = shouldReportLocation,
                    positionPrecision = positionPrecision,
                    onMapReportingEnabledChanged = mapReportingEnabledChanged,
                    onShouldReportLocationChanged = shouldReportLocationChanged,
                    onPositionPrecisionChanged = positionPrecisionChanged,
                    publishIntervalSecs = positionReportingInterval,
                    onPublishIntervalSecsChanged = positionReportingIntervalChanged,
                    enabled = true,
                )
            }
        }
        // Verify that the dialog title is displayed
        onNodeWithText(getString(Res.string.map_reporting)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.map_reporting_summary)).assertIsDisplayed()
    }

    @Test
    fun testMapReportingPreference_toggleMapReporting() = runComposeUiTest {
        setContent {
            Column {
                MapReportingPreference(
                    mapReportingEnabled = mapReportingEnabled,
                    shouldReportLocation = shouldReportLocation,
                    positionPrecision = positionPrecision,
                    onMapReportingEnabledChanged = mapReportingEnabledChanged,
                    onShouldReportLocationChanged = shouldReportLocationChanged,
                    onPositionPrecisionChanged = positionPrecisionChanged,
                    publishIntervalSecs = positionReportingInterval,
                    onPublishIntervalSecsChanged = positionReportingIntervalChanged,
                    enabled = true,
                )
            }
        }
        onNodeWithText(getString(Res.string.i_agree)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.map_reporting)).performClick()
        assertFalse(mapReportingEnabled)
        assertFalse(shouldReportLocation)
        onNodeWithText(getString(Res.string.i_agree)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.i_agree)).performClick()
        assertTrue(shouldReportLocation)
        assertTrue(mapReportingEnabled)
        onNodeWithText(getString(Res.string.map_reporting)).performClick()
        onNodeWithText(getString(Res.string.i_agree)).assertDoesNotExist()
        assertTrue(shouldReportLocation)
        assertFalse(mapReportingEnabled)
    }
}
