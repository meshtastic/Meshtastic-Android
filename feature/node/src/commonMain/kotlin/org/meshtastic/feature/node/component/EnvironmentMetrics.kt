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
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.TELEMETRY_CHANNEL_COUNT
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.model.util.adcVoltage
import org.meshtastic.core.model.util.oneWireTemperature
import org.meshtastic.core.model.util.toSmallDistanceString
import org.meshtastic.core.model.util.toSpeedString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.adc_voltage
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.dew_point
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.gas_resistance
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq
import org.meshtastic.core.resources.ic_dew_point
import org.meshtastic.core.resources.ic_electric_bolt
import org.meshtastic.core.resources.ic_radioactive
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.one_wire_temperature
import org.meshtastic.core.resources.pressure
import org.meshtastic.core.resources.radiation
import org.meshtastic.core.resources.soil_moisture
import org.meshtastic.core.resources.soil_temperature
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.uv_lux
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.resources.weight
import org.meshtastic.core.resources.wind
import org.meshtastic.core.ui.icon.AirQuality
import org.meshtastic.core.ui.icon.Altitude
import org.meshtastic.core.ui.icon.Humidity
import org.meshtastic.core.ui.icon.LightMode
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Particulate
import org.meshtastic.core.ui.icon.PowerSupply
import org.meshtastic.core.ui.icon.Pressure
import org.meshtastic.core.ui.icon.SoilMoisture
import org.meshtastic.core.ui.icon.SoilTemperature
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.core.ui.icon.Voltage
import org.meshtastic.core.ui.icon.Weight
import org.meshtastic.core.ui.icon.WindDirection
import org.meshtastic.feature.node.model.DrawableMetricInfo
import org.meshtastic.feature.node.model.VectorMetricInfo
import org.meshtastic.proto.Config

/**
 * Displays environmental metrics for a node.
 *
 * Readings that come from one physical sensor, or that are derived from each other, are grouped into a shared vertical
 * column (temperature with its dew point, voltage with current, soil temperature with soil moisture, visible with UV
 * light) — see issue #4507. Every other reading is its own single-card column.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun EnvironmentMetrics(
    node: Node,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    isFahrenheit: Boolean = false,
) {
    val groups: List<MetricGroup> = buildList {
        with(node.environmentMetrics) {
            val temperatureCard =
                temperature
                    ?.takeUnless { it.isNaN() }
                    ?.let {
                        VectorMetricInfo(
                            label = Res.string.temperature,
                            value = it.toTempString(isFahrenheit),
                            icon = MeshtasticIcons.Temperature,
                        )
                    }
            // Dew point is computed from temperature and humidity, so it belongs under the temperature it came from.
            val dewPointCard =
                temperature
                    ?.let { temp -> relative_humidity?.let { rh -> UnitConversions.calculateDewPoint(temp, rh) } }
                    ?.takeUnless { it.isNaN() }
                    ?.let {
                        DrawableMetricInfo(
                            label = Res.string.dew_point,
                            value = it.toTempString(isFahrenheit),
                            icon = Res.drawable.ic_dew_point,
                        )
                    }
            add(listOfNotNull(temperatureCard, dewPointCard))
            relative_humidity?.let { rh ->
                add(
                    VectorMetricInfo(
                        label = Res.string.humidity,
                        value = "${NumberFormatter.format(rh, 0)}%",
                        icon = MeshtasticIcons.Humidity,
                    )
                        .asGroup(),
                )
            }
            barometric_pressure?.let { bp ->
                add(
                    VectorMetricInfo(
                        label = Res.string.pressure,
                        value = "${NumberFormatter.format(bp, 0)} hPa",
                        icon = MeshtasticIcons.Pressure,
                    )
                        .asGroup(),
                )
            }
            gas_resistance?.let { gr ->
                add(
                    VectorMetricInfo(
                        label = Res.string.gas_resistance,
                        value = "${NumberFormatter.format(gr, 0)} MΩ",
                        icon = MeshtasticIcons.Particulate,
                    )
                        .asGroup(),
                )
            }
            // Voltage and current describe the same supply, so they stack like a power channel does.
            add(
                listOfNotNull(
                    voltage?.let {
                        VectorMetricInfo(
                            label = Res.string.voltage,
                            value = "${NumberFormatter.format(it, 2)}V",
                            icon = MeshtasticIcons.Voltage,
                        )
                    },
                    current?.let {
                        VectorMetricInfo(
                            label = Res.string.current,
                            value = "${NumberFormatter.format(it, 1)}mA",
                            icon = MeshtasticIcons.PowerSupply,
                        )
                    },
                ),
            )
            iaq?.let { i ->
                add(
                    VectorMetricInfo(label = Res.string.iaq, value = i.toString(), icon = MeshtasticIcons.AirQuality)
                        .asGroup(),
                )
            }
            distance?.let { d ->
                add(
                    VectorMetricInfo(
                        label = Res.string.distance,
                        value = d.toSmallDistanceString(displayUnits),
                        icon = MeshtasticIcons.Altitude,
                    )
                        .asGroup(),
                )
            }
            // Visible and UV illuminance are the same light sensor reported two ways.
            add(
                listOfNotNull(
                    lux?.let {
                        VectorMetricInfo(
                            label = Res.string.lux,
                            value = "${NumberFormatter.format(it, 0)} lx",
                            icon = MeshtasticIcons.LightMode,
                        )
                    },
                    uv_lux?.let {
                        VectorMetricInfo(
                            label = Res.string.uv_lux,
                            value = "${NumberFormatter.format(it, 0)} lx",
                            icon = MeshtasticIcons.LightMode,
                        )
                    },
                ),
            )
            wind_speed?.let { ws ->
                @Suppress("MagicNumber")
                val normalizedBearing = ((wind_direction ?: 0) + 180) % 360
                add(
                    VectorMetricInfo(
                        label = Res.string.wind,
                        value = ws.toSpeedString(displayUnits),
                        icon = MeshtasticIcons.WindDirection,
                        rotateIcon = normalizedBearing.toFloat(),
                    )
                        .asGroup(),
                )
            }
            weight?.let { w ->
                add(
                    VectorMetricInfo(
                        label = Res.string.weight,
                        value =
                        MetricFormatter.weight(w, displayUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL),
                        icon = MeshtasticIcons.Weight,
                    )
                        .asGroup(),
                )
            }
            // Both soil readings come from the same probe.
            add(
                listOfNotNull(
                    soil_temperature
                        ?.takeUnless { it.isNaN() }
                        ?.let {
                            VectorMetricInfo(
                                label = Res.string.soil_temperature,
                                value = it.toTempString(isFahrenheit),
                                icon = MeshtasticIcons.SoilTemperature,
                            )
                        },
                    soil_moisture?.let {
                        VectorMetricInfo(
                            label = Res.string.soil_moisture,
                            value = "$it%",
                            icon = MeshtasticIcons.SoilMoisture,
                        )
                    },
                ),
            )
            radiation?.let { r ->
                add(
                    DrawableMetricInfo(
                        label = Res.string.radiation,
                        value = "${NumberFormatter.format(r, 1)} µR/h",
                        icon = Res.drawable.ic_radioactive,
                    )
                        .asGroup(),
                )
            }
            // 1-Wire probes and ADC inputs are independent channels, so one card each. Absent channels are null; a
            // reported 0°C or 0 V is a real reading and stays visible.
            for (channel in 0 until TELEMETRY_CHANNEL_COUNT) {
                oneWireTemperature(channel)
                    ?.takeIf { !it.isNaN() }
                    ?.let { temp ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.one_wire_temperature,
                                value = temp.toTempString(isFahrenheit),
                                icon = MeshtasticIcons.SoilTemperature,
                                channelNumber = channel + 1,
                            )
                                .asGroup(),
                        )
                    }
            }
            for (channel in 0 until TELEMETRY_CHANNEL_COUNT) {
                adcVoltage(channel)
                    ?.takeIf { !it.isNaN() }
                    ?.let { volts ->
                        add(
                            DrawableMetricInfo(
                                label = Res.string.adc_voltage,
                                value = MetricFormatter.voltage(volts),
                                icon = Res.drawable.ic_electric_bolt,
                                channelNumber = channel + 1,
                            )
                                .asGroup(),
                        )
                    }
            }
        }
    }
    MetricCardFlow(groups = groups)
}
