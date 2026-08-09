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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LoraSignalIndicatorUiTest {

    @Test
    fun rssiUsesCallerProvidedLabel() = runComposeUiTest {
        setContent { AppTheme { Rssi(rssi = -70, label = "Signal strength") } }

        onNodeWithText("Signal strength -70 dBm").assertIsDisplayed()
    }

    @Test
    fun snrRendersAZeroReading() = runComposeUiTest {
        // 0 dB is a measurement and must be shown, not suppressed as "no reading".
        setContent { AppTheme { Snr(snr = 0f) } }

        onNodeWithText("SNR 0.00 dB").assertIsDisplayed()
    }

    @Test
    fun snrRendersNothingWhenAbsent() = runComposeUiTest {
        setContent { AppTheme { Snr(snr = null) } }

        onNodeWithText("SNR 0.00 dB").assertDoesNotExist()
    }

    @Test
    fun loraSignalIndicatorShowsUnknownWhenSnrIsAbsent() = runComposeUiTest {
        // Absence must not render as "Signal None" — that band means a measured, undemodulable signal.
        setContent { AppTheme { LoraSignalIndicator(snr = null) } }

        onNodeWithText("Signal Unknown").assertIsDisplayed()
    }

    @Test
    fun batteryUsesCallerProvidedUnknownLabel() = runComposeUiTest {
        setContent { AppTheme { MaterialBatteryInfo(level = null, unknownLabel = "Unavailable") } }

        onNodeWithContentDescription("Unavailable").assertIsDisplayed()
    }
}
