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
package org.meshtastic.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.app.preview.DeviceTelemetryPreview
import org.meshtastic.app.preview.IndoorAirQualityPreview
import org.meshtastic.app.preview.LoraSignalPreview
import org.meshtastic.app.preview.TemperatureAndHumidityPreview

/** Screenshot tests for telemetry and signal display components. */
class TelemetryScreenshotTests {
    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun temperatureAndHumidityScreenshot() {
        TemperatureAndHumidityPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun deviceTelemetryScreenshot() {
        DeviceTelemetryPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun loraSignalScreenshot() {
        LoraSignalPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun indoorAirQualityScreenshot() {
        IndoorAirQualityPreview()
    }
}
