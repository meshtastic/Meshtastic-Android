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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fair
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.good
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.core.ui.util.LocalNoiseFloor
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test

/**
 * The node-list signal pill ([SignalInfo]) reuses [determineSignalQuality] the same way NodeDetailsSection's SignalRow
 * does (see [SignalRowQualityLabelTest] in `feature/node`) — covering it here too so the noise-floor blend
 * (design#15, #6826) is proven at both threaded call sites, not just one.
 */
@OptIn(ExperimentalTestApi::class)
class SignalInfoUiTest {

    private val preset = ModemPreset.LONG_FAST // limit -17.5

    @Test
    fun signalInfoBlendsInTheNoiseFloorWhenKnown() = runComposeUiTest {
        // SNR -10 alone rates GOOD (margin +7.5). rssi(-90) - noiseFloor(-70) = -20; margin -2.5 -> FAIR, the worse
        // tier - same case as LoraSignalIndicatorTest's "picks the worse tier" and SignalRowQualityLabelTest's
        // downgrade case, now proven at this third call site.
        setSignalInfo(snr = -10f, rssi = -90, noiseFloor = -70)

        val expected = "${MetricFormatter.snr(-10f)} · ${MetricFormatter.rssi(-90)} · ${getString(Res.string.fair)}"
        onNodeWithText(expected).assertExists()
    }

    @Test
    fun signalInfoStaysSnrOnlyWithoutANoiseFloor() = runComposeUiTest {
        // Identical SNR/RSSI to the case above, but no LocalNoiseFloor provided -> unchanged GOOD rating.
        setSignalInfo(snr = -10f, rssi = -90, noiseFloor = null)

        val expected = "${MetricFormatter.snr(-10f)} · ${MetricFormatter.rssi(-90)} · ${getString(Res.string.good)}"
        onNodeWithText(expected).assertExists()
    }

    private fun ComposeUiTest.setSignalInfo(snr: Float, rssi: Int, noiseFloor: Int?) = setContent {
        CompositionLocalProvider(LocalModemPreset provides preset, LocalNoiseFloor provides noiseFloor) {
            AppTheme { SignalInfo(node = Node(num = 1, snr = snr, rssi = rssi, hopsAway = 0)) }
        }
    }
}
