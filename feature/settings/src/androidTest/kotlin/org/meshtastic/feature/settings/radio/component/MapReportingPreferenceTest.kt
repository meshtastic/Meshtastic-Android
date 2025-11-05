/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.strings.R as Res

@RunWith(AndroidJUnit4::class)
class MapReportingPreferenceTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun getString(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    var mapReportingEnabled = false
    var shouldReportLocation = false
    var positionPrecision = 5
    var positionReportingInterval = 60

    var mapReportingEnabledChanged = { enabled: Boolean -> mapReportingEnabled = enabled }
    var shouldReportLocationChanged = { enabled: Boolean -> shouldReportLocation = enabled }
    var positionPrecisionChanged = { precision: Int -> positionPrecision = precision }
    var positionReportingIntervalChanged = { interval: Int -> positionReportingInterval = interval }

    private fun testMapReportingPreference() = composeTestRule.setContent {
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

    @Test
    fun testMapReportingPreference_showsText() {
        composeTestRule.apply {
            testMapReportingPreference()
            // Verify that the dialog title is displayed
            onNodeWithText(getString(Res.string.map_reporting)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.map_reporting_summary)).assertIsDisplayed()
        }
    }

    @Test
    fun testMapReportingPreference_toggleMapReporting() {
        composeTestRule.apply {
            testMapReportingPreference()
            onNodeWithText(getString(Res.string.i_agree)).assertIsNotDisplayed()
            onNodeWithText(getString(Res.string.map_reporting)).performClick()
            Assert.assertFalse(mapReportingEnabled)
            Assert.assertFalse(shouldReportLocation)
            onNodeWithText(getString(Res.string.i_agree)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.i_agree)).performClick()
            Assert.assertTrue(shouldReportLocation)
            Assert.assertTrue(mapReportingEnabled)
            onNodeWithText(getString(Res.string.map_reporting)).performClick()
            onNodeWithText(getString(Res.string.i_agree)).assertIsNotDisplayed()
            Assert.assertTrue(shouldReportLocation)
            Assert.assertFalse(mapReportingEnabled)
        }
    }
}
