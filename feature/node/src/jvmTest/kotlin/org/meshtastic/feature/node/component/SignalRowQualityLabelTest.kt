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

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bad
import org.meshtastic.core.resources.fair
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.good
import org.meshtastic.core.resources.none_quality
import org.meshtastic.core.resources.snr
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test

/**
 * Node Details' SignalRow reuses the already-tested [determineSignalQuality] to label SNR (see
 * [LoraSignalIndicatorTest] for the underlying threshold coverage). RSSI intentionally keeps showing the raw value
 * only - RSSI alone cannot indicate quality without the noise floor.
 */
@OptIn(ExperimentalTestApi::class)
class SignalRowQualityLabelTest {

    // Boundary values relative to ModemPreset.LONG_FAST's -17.5 dB demod floor, mirroring LoraSignalIndicatorTest.
    private val preset = ModemPreset.LONG_FAST

    @Test
    fun signalRow_labelsGoodSnr() = runComposeUiTest {
        assertSnrLabel(snr = -17f, expectedQualityRes = Res.string.good)
    }

    @Test
    fun signalRow_labelsFairSnr() = runComposeUiTest {
        assertSnrLabel(snr = -17.5f, expectedQualityRes = Res.string.fair)
    }

    @Test
    fun signalRow_labelsBadSnr() = runComposeUiTest { assertSnrLabel(snr = -23f, expectedQualityRes = Res.string.bad) }

    @Test
    fun signalRow_labelsNoneSnr() = runComposeUiTest {
        assertSnrLabel(snr = -30f, expectedQualityRes = Res.string.none_quality)
    }

    @Test
    fun signalRow_rssiShowsOnlyTheRawValue() = runComposeUiTest {
        setNodeDetails(Node(num = 1, snr = -17f, rssi = -100, hopsAway = 0))
        onNodeWithText(MetricFormatter.rssi(-100)).assertExists()
    }

    @Test
    fun signalRow_hidesWhenNodeHasNoSnrReading() = runComposeUiTest {
        // hopsAway = 0 so SignalRow itself renders; default snr is the SNR_UNSET sentinel -> snrOrNull is null.
        setNodeDetails(Node(num = 1, hopsAway = 0))
        onNodeWithText(getString(Res.string.snr)).assertDoesNotExist()
    }

    private fun ComposeUiTest.assertSnrLabel(snr: Float, expectedQualityRes: StringResource) {
        // hopsAway = 0 - matches MainNodeDetails' guard for rendering SignalRow at all.
        setNodeDetails(Node(num = 1, snr = snr, rssi = -100, hopsAway = 0))
        // Value-before-quality order matches the node-list signal pill in SignalInfo.kt.
        val expected = "${MetricFormatter.snr(snr)} · ${getString(expectedQualityRes)}"
        onNodeWithText(expected).assertExists()
    }

    private fun ComposeUiTest.setNodeDetails(node: Node) = setContent {
        CompositionLocalProvider(LocalModemPreset provides preset) {
            AppTheme { Surface { NodeDetailsSection(node = node) } }
        }
    }
}
