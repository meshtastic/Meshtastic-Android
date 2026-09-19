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
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.model.LogsType

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
                displayUnits = MeasurementSystem.METRIC,
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
                displayUnits = MeasurementSystem.METRIC,
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
                displayUnits = MeasurementSystem.METRIC,
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
                displayUnits = MeasurementSystem.IMPERIAL,
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
                displayUnits = MeasurementSystem.METRIC,
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
            org.meshtastic.proto.PowerMetrics.Builder()
                .also { wb ->
                    wb.ch1_voltage = 4.19f
                    wb.ch1_current = 128.4f
                    wb.ch2_voltage = 3.72f
                    wb.ch2_current = 12.5f
                    wb.ch3_voltage = 5.02f
                    wb.ch3_current = 431.7f
                }
                .build(),
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
            powerMetrics =
            org.meshtastic.proto.PowerMetrics.Builder()
                .also { wb ->
                    wb.ch1_voltage = 4.19f
                    wb.ch1_current = 128.4f
                }
                .build(),
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
            powerMetrics =
            org.meshtastic.proto.PowerMetrics.Builder()
                .also { wb ->
                    wb.ch1_voltage = 4.19f
                    wb.ch2_voltage = 3.72f
                }
                .build(),
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
            org.meshtastic.proto.EnvironmentMetrics.Builder()
                .also { wb ->
                    wb.temperature = 21.5f
                    wb.relative_humidity = 47f
                    wb.barometric_pressure = 1013f
                    wb.gas_resistance = 1200f
                    wb.voltage = 4.19f
                    wb.current = 128.4f
                    wb.iaq = 62
                    wb.lux = 480f
                    wb.uv_lux = 12f
                    wb.soil_temperature = 18.2f
                    wb.soil_moisture = 33
                    wb.radiation = 0.15f
                }
                .build(),
        )
    AppTheme { Surface { EnvironmentMetrics(node = node, displayUnits = MeasurementSystem.METRIC) } }
}

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun AirQualityInfoCardsPreview() {
    val node =
        previewData.mickeyMouse.copy(
            airQualityMetrics =
            org.meshtastic.proto.AirQualityMetrics.Builder()
                .also { wb ->
                    wb.pm10_standard = 8
                    wb.pm25_standard = 12
                    wb.pm100_standard = 18
                    wb.co2 = 640
                    wb.co2_temperature = 22.1f
                    wb.co2_humidity = 44f
                }
                .build(),
        )
    AppTheme { Surface { AirQualityInfoCards(node = node) } }
}

// ---------------------------------------------------------------------------
// SoilWaterMetrics / lightning / PM status previews
// ---------------------------------------------------------------------------

/**
 * Every soil and water field populated, so each probe group renders. Built in a function rather than a top-level `val`
 * so the proto construction stays out of this file's class initializer.
 */
private fun sampleSoilWaterMetrics(): org.meshtastic.proto.SoilWaterMetrics =
    org.meshtastic.proto.SoilWaterMetrics.Builder()
        .also { wb ->
            wb.soil_ph = 6.8f
            wb.nitrogen = 42f
            wb.phosphorus = 18f
            wb.potassium = 96f
            wb.ph = 7.4f
            wb.electrical_conductivity = 1.24f
            wb.salinity = 620f
            wb.dissolved_oxygen = 8.3f
            wb.orp = 215f
            wb.chemical_oxygen_demand = 48f
            wb.biochemical_oxygen_demand = 12f
            wb.turbidity = 3.6f
            wb.nitrate = 5.2f
            wb.ammonium = 0.4f
            wb.solar_irradiance = 780f
        }
        .build()

@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun SoilWaterMetricsPreview() {
    val node = previewData.mickeyMouse.copy(soilWaterMetrics = sampleSoilWaterMetrics())
    AppTheme { Surface { SoilWaterMetrics(node = node) } }
}

/** Temperature and humidity for context, plus the AS3935 pair that shares a column. */
@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun EnvironmentMetricsLightningPreview() {
    val node =
        previewData.mickeyMouse.copy(
            environmentMetrics =
            org.meshtastic.proto.EnvironmentMetrics.Builder()
                .also { wb ->
                    wb.temperature = 19.4f
                    wb.relative_humidity = 71f
                    wb.lightning_strike_count_1h = 3
                    wb.lightning_distance_km = 12f
                }
                .build(),
        )
    AppTheme { Surface { EnvironmentMetrics(node = node, displayUnits = MeasurementSystem.METRIC) } }
}

/**
 * A status register setting a fault (fan, bit 4), a warning (fan speed, bit 21) and a bit this build does not name (bit
 * 30) — the three value tones the status cards can take.
 */
@PreviewLightDark
@Suppress("PreviewPublic")
@Composable
fun AirQualityInfoCardsStatusPreview() {
    val node =
        previewData.mickeyMouse.copy(
            airQualityMetrics =
            org.meshtastic.proto.AirQualityMetrics.Builder()
                .also { wb ->
                    wb.pm10_standard = 8
                    wb.pm25_standard = 12
                    wb.pm100_standard = 18
                    wb.co2 = 640
                    wb.pm_status_flags = (1 shl 4) or (1 shl 21) or (1 shl 30)
                }
                .build(),
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
                displayUnits = MeasurementSystem.METRIC,
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
