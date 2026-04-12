/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.MaterialSignalInfo
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.SatelliteCountInfo
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun BatteryInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Full battery:")
                MaterialBatteryInfo(level = 100, voltage = 4.2f)
                Text("Medium battery:")
                MaterialBatteryInfo(level = 50, voltage = 3.7f)
                Text("Low battery:")
                MaterialBatteryInfo(level = 10, voltage = 3.3f)
                Text("Powered (101):")
                MaterialBatteryInfo(level = 101, voltage = 5.0f)
                Text("Unknown:")
                MaterialBatteryInfo(level = null)
            }
        }
    }
}

@MultiPreview
@Composable
fun SignalInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Excellent (4 bars):")
                MaterialSignalInfo(signalBars = 4, signalStrengthValue = "-55 dBm")
                Text("Good (3 bars):")
                MaterialSignalInfo(signalBars = 3, signalStrengthValue = "-65 dBm")
                Text("Fair (2 bars):")
                MaterialSignalInfo(signalBars = 2, signalStrengthValue = "-75 dBm")
                Text("Weak (1 bar):")
                MaterialSignalInfo(signalBars = 1, signalStrengthValue = "-85 dBm")
                Text("No signal (0 bars):")
                MaterialSignalInfo(signalBars = 0, signalStrengthValue = "-95 dBm")
            }
        }
    }
}

@MultiPreview
@Composable
fun SatelliteCountPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SatelliteCountInfo(satCount = 12)
                SatelliteCountInfo(satCount = 4)
                SatelliteCountInfo(satCount = 0)
            }
        }
    }
}

@MultiPreview
@Composable
fun NodeChipPreview() {
    val provider = NodePreviewParameterProvider()
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeChip(node = provider.mickeyMouse)
                NodeChip(node = provider.minnieMouse)
            }
        }
    }
}
