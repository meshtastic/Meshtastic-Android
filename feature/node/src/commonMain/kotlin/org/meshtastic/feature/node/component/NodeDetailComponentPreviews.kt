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
@file:Suppress("TooManyFunctions", "MagicNumber")

package org.meshtastic.feature.node.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.proto.Config

// ---------------------------------------------------------------------------
// Sample data for previews
// ---------------------------------------------------------------------------

private val previewData = NodePreviewParameterProvider()

// ---------------------------------------------------------------------------
// DeviceActions previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun DeviceActionsRemotePreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            DeviceActions(
                node = node,
                ourNode = previewData.mickeyMouse.copy(num = 9999),
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                availableLogs =
                setOf(
                    LogsType.DEVICE,
                    LogsType.POSITIONS,
                    LogsType.ENVIRONMENT,
                    LogsType.SIGNAL,
                    LogsType.TRACEROUTE,
                ),
                onAction = {},
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun DeviceActionsLocalPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            DeviceActions(
                node = node,
                ourNode = node,
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                availableLogs = setOf(LogsType.DEVICE, LogsType.POSITIONS),
                onAction = {},
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
                isLocal = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// TelemetricActionsSection previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun TelemetricActionsSectionPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            TelemetricActionsSection(
                node = node,
                ourNode = previewData.mickeyMouse.copy(num = 9999),
                availableLogs =
                setOf(
                    LogsType.DEVICE,
                    LogsType.POSITIONS,
                    LogsType.ENVIRONMENT,
                    LogsType.SIGNAL,
                    LogsType.TRACEROUTE,
                    LogsType.NEIGHBOR_INFO,
                ),
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
                onAction = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
fun TelemetricActionsSectionEmptyPreview() {
    val node = previewData.minnieMouse
    AppTheme {
        Surface {
            TelemetricActionsSection(
                node = node,
                ourNode = previewData.mickeyMouse,
                availableLogs = emptySet(),
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                displayUnits = Config.DisplayConfig.DisplayUnits.IMPERIAL,
                isFahrenheit = true,
                onAction = {},
            )
        }
    }
}

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun TelemetricActionsSectionLocalPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            TelemetricActionsSection(
                node = node,
                ourNode = node,
                availableLogs = emptySet(),
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
                onAction = {},
                isLocal = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PowerMetrics previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun PowerMetricsPreview() {
    val node =
        previewData.mickeyMouse.copy(
            powerMetrics =
            org.meshtastic.proto.PowerMetrics(
                ch1_voltage = 4.19f,
                ch1_current = 128.4f,
                ch2_voltage = 3.72f,
                ch2_current = 12.5f,
                ch3_voltage = 5.02f,
                ch3_current = 431.7f,
            ),
        )
    AppTheme { Surface { PowerMetrics(node = node) } }
}

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun PowerMetricsPartialPreview() {
    // Only channel 1 reports a voltage — a single column, matching the partial layout in issue #4507.
    val node =
        previewData.mickeyMouse.copy(
            powerMetrics = org.meshtastic.proto.PowerMetrics(ch1_voltage = 4.19f, ch1_current = 128.4f),
        )
    AppTheme { Surface { PowerMetrics(node = node) } }
}

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun PowerMetricsNoCurrentPreview() {
    // Channels report voltage but no current at all — voltage-only columns, no fabricated 0.0mA cards.
    val node =
        previewData.mickeyMouse.copy(
            powerMetrics = org.meshtastic.proto.PowerMetrics(ch1_voltage = 4.19f, ch2_voltage = 3.72f),
        )
    AppTheme { Surface { PowerMetrics(node = node) } }
}

// ---------------------------------------------------------------------------
// EnvironmentMetrics / AirQualityInfoCards previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun EnvironmentMetricsPreview() {
    val node =
        previewData.mickeyMouse.copy(
            environmentMetrics =
            org.meshtastic.proto.EnvironmentMetrics(
                temperature = 21.5f,
                relative_humidity = 47f,
                barometric_pressure = 1013f,
                gas_resistance = 1200f,
                voltage = 4.19f,
                current = 128.4f,
                iaq = 62,
                lux = 480f,
                uv_lux = 12f,
                soil_temperature = 18.2f,
                soil_moisture = 33,
                radiation = 0.15f,
            ),
        )
    AppTheme { Surface { EnvironmentMetrics(node = node, displayUnits = Config.DisplayConfig.DisplayUnits.METRIC) } }
}

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun AirQualityInfoCardsPreview() {
    val node =
        previewData.mickeyMouse.copy(
            airQualityMetrics =
            org.meshtastic.proto.AirQualityMetrics(
                pm10_standard = 8,
                pm25_standard = 12,
                pm100_standard = 18,
                co2 = 640,
                co2_temperature = 22.1f,
                co2_humidity = 44f,
            ),
        )
    AppTheme { Surface { AirQualityInfoCards(node = node) } }
}

// ---------------------------------------------------------------------------
// PositionInlineContent preview
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun PositionInlineContentPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            PositionInlineContent(
                node = node,
                ourNode = previewData.mickeyMouse.copy(num = 9999),
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                onAction = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------
// NodeDetailsSection preview
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun NodeDetailsSectionPreview() {
    val node = previewData.mickeyMouse
    AppTheme { Surface { NodeDetailsSection(node = node) } }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun NodeDetailsSectionSignedPreview() {
    // signsPackets surfaces the "Signed node" row; manuallyVerified shows it sitting most-trusted-first.
    // lastTransport = 1 (TRANSPORT_LORA) surfaces the "Transport" row.
    val node =
        previewData.mickeyMouse.copy(
            manuallyVerified = true,
            signsPackets = true,
            lastTransport = 1,
            publicKey = ByteArray(32) { 1 }.toByteString(),
        )
    AppTheme { Surface { NodeDetailsSection(node = node) } }
}

@PreviewLightDark
@Composable
private fun NodeDetailsSectionWithDeviceHeroPreview() {
    val node = previewData.mickeyMouse
    val deviceHardware =
        org.meshtastic.core.model.DeviceHardware(
            displayName = "Heltec V3",
            activelySupported = true,
            images = listOf("heltec-v3.svg", "heltec-v3-case.svg"),
            hwModel = 43,
            hwModelSlug = "heltecV3",
        )
    AppTheme {
        Surface { NodeDetailsSection(node = node, deviceHardware = deviceHardware, reportedTarget = "heltec-v3") }
    }
}

@PreviewLightDark
@Composable
private fun DeviceLinksSectionPreview() {
    val links =
        listOf(
            org.meshtastic.core.model.DeviceLink(
                shortCode = "heltec-v3",
                description = "Heltec V3",
                isVendor = true,
                targets = listOf("heltec-v3"),
            ),
            org.meshtastic.core.model.DeviceLink(
                shortCode = "rokland-heltec-v3",
                description = "Rokland",
                regions = listOf("US"),
                targets = listOf("heltec-v3"),
            ),
            org.meshtastic.core.model.DeviceLink(
                shortCode = "heltec-v3_aliexpress",
                description = "AliExpress",
                regions = emptyList(),
                targets = listOf("heltec-v3"),
            ),
        )
    AppTheme { Surface { DeviceLinksSection(links = links) } }
}
