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
package org.meshtastic.core.ui.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.iaq_level_excellent
import org.meshtastic.core.resources.iaq_level_extremely_polluted
import org.meshtastic.core.resources.iaq_level_good
import org.meshtastic.core.resources.iaq_level_heavily_polluted
import org.meshtastic.core.resources.iaq_level_lightly_polluted
import org.meshtastic.core.resources.iaq_level_moderately_polluted
import org.meshtastic.core.resources.iaq_level_range
import org.meshtastic.core.resources.iaq_level_range_open
import org.meshtastic.core.resources.iaq_level_severely_polluted
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IndoorAirQualityLabelTest {

    @Test
    fun `every IAQ level names a string resource so it translates`() {
        assertEquals(
            listOf(
                Res.string.iaq_level_excellent,
                Res.string.iaq_level_good,
                Res.string.iaq_level_lightly_polluted,
                Res.string.iaq_level_moderately_polluted,
                Res.string.iaq_level_heavily_polluted,
                Res.string.iaq_level_severely_polluted,
                Res.string.iaq_level_extremely_polluted,
            ),
            Iaq.entries.map { it.label },
        )
    }

    @Test
    fun `text mode shows the level label with its range`() = runComposeUiTest {
        setContent { AppTheme { IndoorAirQuality(iaq = 75, displayMode = IaqDisplayMode.Text) } }

        onNodeWithText(getString(Res.string.iaq_level_range, getString(Res.string.iaq_level_good), 51, 100))
            .assertIsDisplayed()
    }

    @Test
    fun `the open-ended top level shows its floor with a plus`() = runComposeUiTest {
        setContent { AppTheme { IndoorAirQuality(iaq = 600, displayMode = IaqDisplayMode.Text) } }

        onNodeWithText(
            getString(Res.string.iaq_level_range_open, getString(Res.string.iaq_level_extremely_polluted), 351),
        )
            .assertIsDisplayed()
    }

    @Test
    fun `IAQ bands follow the Bosch scale at every boundary`() {
        val expected =
            listOf(
                0 to Iaq.Excellent,
                50 to Iaq.Excellent,
                51 to Iaq.Good,
                100 to Iaq.Good,
                101 to Iaq.LightlyPolluted,
                150 to Iaq.LightlyPolluted,
                151 to Iaq.ModeratelyPolluted,
                200 to Iaq.ModeratelyPolluted,
                201 to Iaq.HeavilyPolluted,
                250 to Iaq.HeavilyPolluted,
                251 to Iaq.SeverelyPolluted,
                350 to Iaq.SeverelyPolluted,
                351 to Iaq.ExtremelyPolluted,
                500 to Iaq.ExtremelyPolluted,
                600 to Iaq.ExtremelyPolluted,
            )
        expected.forEach { (value, band) -> assertEquals(band, getIaq(value), "iaq $value") }
    }

    @Test
    fun `a uint32 reading that wraps negative is above the scale`() {
        assertEquals(Iaq.ExtremelyPolluted, getIaq(-1))
    }

    @Test
    fun `an unset IAQ has no band`() {
        assertEquals(null, getIaq(Int.MIN_VALUE))
    }
}
