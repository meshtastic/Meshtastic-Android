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
import org.meshtastic.core.ui.component.AirQualityInfo
import org.meshtastic.core.ui.component.HardwareInfo
import org.meshtastic.core.ui.component.HumidityInfo
import org.meshtastic.core.ui.component.IndoorAirQuality
import org.meshtastic.core.ui.component.LoraSignalIndicator
import org.meshtastic.core.ui.component.NodeIdInfo
import org.meshtastic.core.ui.component.NodeSignalQuality
import org.meshtastic.core.ui.component.PaxcountInfo
import org.meshtastic.core.ui.component.PowerInfo
import org.meshtastic.core.ui.component.PressureInfo
import org.meshtastic.core.ui.component.RoleInfo
import org.meshtastic.core.ui.component.SoilMoistureInfo
import org.meshtastic.core.ui.component.SoilTemperatureInfo
import org.meshtastic.core.ui.component.TemperatureInfo
import org.meshtastic.core.ui.component.UptimeInfo
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun TemperatureAndHumidityPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TemperatureInfo(temp = "25.3°C")
                HumidityInfo(humidity = "60%")
                PressureInfo(pressure = "1013.25 hPa")
                SoilTemperatureInfo(temp = "18.5°C")
                SoilMoistureInfo(moisture = "42%")
            }
        }
    }
}

@MultiPreview
@Composable
fun DeviceTelemetryPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HardwareInfo(hwModel = "TBEAM")
                RoleInfo(role = "ROUTER")
                NodeIdInfo(id = "!a1b2c3d4")
                UptimeInfo(uptime = "3h 42m")
                PowerInfo(value = "3.7V", label = "Battery")
                PaxcountInfo(pax = "15 (BLE: 10, WiFi: 5)")
                AirQualityInfo(iaq = "Good (85)")
            }
        }
    }
}

@MultiPreview
@Composable
fun LoraSignalPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Good signal:")
                NodeSignalQuality(snr = 10f, rssi = -90)
                LoraSignalIndicator(snr = 10f, rssi = -90)

                Text("Fair signal:")
                NodeSignalQuality(snr = -10f, rssi = -120)
                LoraSignalIndicator(snr = -10f, rssi = -120)

                Text("Bad signal:")
                NodeSignalQuality(snr = -20f, rssi = -130)
                LoraSignalIndicator(snr = -20f, rssi = -130)
            }
        }
    }
}

@MultiPreview
@Composable
fun IndoorAirQualityPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Excellent:")
                IndoorAirQuality(iaq = 25)
                Text("Good:")
                IndoorAirQuality(iaq = 75)
                Text("Moderate:")
                IndoorAirQuality(iaq = 150)
                Text("Poor:")
                IndoorAirQuality(iaq = 250)
                Text("Unknown:")
                IndoorAirQuality(iaq = null)
            }
        }
    }
}
