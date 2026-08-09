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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.AirQualityIndex
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.aqi
import org.meshtastic.core.resources.aqi_value_with_severity
import org.meshtastic.core.resources.co2
import org.meshtastic.core.resources.co2_humidity
import org.meshtastic.core.resources.co2_temperature
import org.meshtastic.core.resources.micrograms_per_cubic_meter
import org.meshtastic.core.resources.pm10
import org.meshtastic.core.resources.pm1_0
import org.meshtastic.core.resources.pm2_5
import org.meshtastic.core.resources.ppm
import org.meshtastic.core.ui.component.Co2Severity
import org.meshtastic.core.ui.component.PmAqiSeverity
import org.meshtastic.core.ui.icon.AirQuality
import org.meshtastic.core.ui.icon.Humidity
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.feature.node.model.VectorMetricInfo
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.Telemetry

/** Computes the EPA NowCast AQI (value + severity) from [pm25History], or null if there isn't enough data yet. */
private fun nowCastAqi(pm25History: List<Telemetry>): Pair<Int, PmAqiSeverity>? {
    val readings =
        pm25History.mapNotNull { telemetry ->
            telemetry.air_quality_metrics?.pm25_standard?.let { telemetry.time.toLong() to it.toDouble() }
        }
    val nowCastPm25 = AirQualityIndex.computeNowCastPm25(readings, nowSeconds) ?: return null
    val aqiValue = AirQualityIndex.pm25ToAqi(nowCastPm25)
    return PmAqiSeverity.fromAqi(aqiValue)?.let { aqiValue to it }
}

private fun buildAirQualityCards(
    metrics: AirQualityMetrics,
    aqiText: String?,
    ugm3: String,
    ppmUnit: String,
    icon: ImageVector,
    tempIcon: ImageVector,
    humidityIcon: ImageVector,
    isFahrenheit: Boolean,
): List<MetricGroup> = buildList {
    // A present reading of 0 is a valid value (e.g. clean air at 0 µg/m³), so only the `?.` null-check (an
    // absent metric) hides a card — matching the #5793 chart/CSV zero-suppression fix.
    metrics.pm10_standard?.let { pm -> add(VectorMetricInfo(Res.string.pm1_0, "$pm $ugm3", icon).asGroup()) }
    metrics.pm25_standard?.let { pm ->
        add(
            listOfNotNull(
                VectorMetricInfo(Res.string.pm2_5, "$pm $ugm3", icon),
                // AQI is derived from the raw PM2.5 reading, so it stacks directly under it — and is only shown when
                // that raw reading is present.
                aqiText?.let { VectorMetricInfo(Res.string.aqi, it, icon) },
            ),
        )
    }
    metrics.pm100_standard?.let { pm -> add(VectorMetricInfo(Res.string.pm10, "$pm $ugm3", icon).asGroup()) }
    // The SCD4x CO₂ sensor also reports its own temperature/humidity (#5873) — surfaced here so a node can double as a
    // weather station without a separate BME sensor. All three come from that one sensor, so they share a column.
    // `?.` hides only genuinely-absent readings.
    add(
        listOfNotNull(
            metrics.co2?.let { co2 -> VectorMetricInfo(Res.string.co2, "$co2 $ppmUnit", icon) },
            metrics.co2_temperature?.let { temp ->
                VectorMetricInfo(Res.string.co2_temperature, temp.toTempString(isFahrenheit), tempIcon)
            },
            metrics.co2_humidity?.let { hum ->
                VectorMetricInfo(Res.string.co2_humidity, "${NumberFormatter.format(hum, 0)}%", humidityIcon)
            },
        ),
    )
}

/**
 * Severity color for a metric's value text, or null to keep the default card color. The AQI tone is resolved by the
 * caller because [PmAqiSeverity.color] is `@Composable`.
 */
private fun metricValueColor(label: StringResource, co2Color: Color?, aqiColor: Color?): Color? = when (label) {
    Res.string.co2 -> co2Color
    Res.string.aqi -> aqiColor
    else -> null
}

/**
 * Displays air quality info cards for a node showing PM1.0, PM2.5, PM10 and CO₂ values. A card is shown for each metric
 * the node actually reports; a present reading of 0 (e.g. clean air at 0 µg/m³) is a valid value and is shown — only
 * absent metrics are hidden. CO₂ value text is color-coded by severity.
 *
 * When [pm25History] has enough recent readings for an EPA NowCast (design#54), an additional AQI card is shown
 * alongside the raw PM2.5 card, color-coded by EPA severity category. Below that threshold, only the raw readings are
 * shown — never a computed AQI from insufficient data.
 */
@Composable
internal fun AirQualityInfoCards(
    node: Node,
    pm25History: List<Telemetry> = emptyList(),
    isFahrenheit: Boolean = false,
) {
    val metrics = node.airQualityMetrics
    val ugm3 = stringResource(Res.string.micrograms_per_cubic_meter)
    val ppmUnit = stringResource(Res.string.ppm)

    val aqi = nowCastAqi(pm25History)
    // The category name always accompanies the value, so the AQI card never relies on color alone.
    val aqiText =
        aqi?.let { (value, severity) ->
            stringResource(Res.string.aqi_value_with_severity, value, stringResource(severity.labelRes))
        }
    val icon = MeshtasticIcons.AirQuality
    val tempIcon = MeshtasticIcons.Temperature
    val humidityIcon = MeshtasticIcons.Humidity
    val cards =
        remember(metrics, aqiText, ugm3, ppmUnit, icon, tempIcon, humidityIcon, isFahrenheit) {
            buildAirQualityCards(metrics, aqiText, ugm3, ppmUnit, icon, tempIcon, humidityIcon, isFahrenheit)
        }

    if (cards.none { it.isNotEmpty() }) return

    val co2Color = Co2Severity.fromPpm(metrics.co2 ?: 0)?.color
    val aqiColor = aqi?.second?.color()

    MetricCardFlow(groups = cards, valueColor = { metric -> metricValueColor(metric.label, co2Color, aqiColor) })
}
