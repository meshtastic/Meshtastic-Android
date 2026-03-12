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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.model.util.toSmallDistanceString
import org.meshtastic.core.model.util.toSpeedString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.dew_point
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.gas_resistance
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq
import org.meshtastic.core.resources.ic_dew_point
import org.meshtastic.core.resources.ic_radioactive
import org.meshtastic.core.resources.ic_soil_moisture
import org.meshtastic.core.resources.ic_soil_temperature
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.pressure
import org.meshtastic.core.resources.radiation
import org.meshtastic.core.resources.soil_moisture
import org.meshtastic.core.resources.soil_temperature
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.uv_lux
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.resources.weight
import org.meshtastic.core.resources.wind
import org.meshtastic.feature.node.model.DrawableMetricInfo
import org.meshtastic.feature.node.model.VectorMetricInfo
import org.meshtastic.proto.Config

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun EnvironmentMetrics(
    node: Node,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    isFahrenheit: Boolean = false,
) {
    val vectorMetrics =
        remember(node.environmentMetrics, isFahrenheit, displayUnits) {
            buildList {
                with(node.environmentMetrics) {
                    temperature?.let { temp ->
                        if (!temp.isNaN()) {
                            add(
                                VectorMetricInfo(
                                    label = Res.string.temperature,
                                    value = temp.toTempString(isFahrenheit),
                                    icon = Icons.Rounded.Thermostat,
                                ),
                            )
                        }
                    }
                    relative_humidity?.let { rh ->
                        add(
                            VectorMetricInfo(
                                Res.string.humidity,
                                "${NumberFormatter.format(rh, 0)}%",
                                Icons.Rounded.WaterDrop,
                            ),
                        )
                    }
                    barometric_pressure?.let { bp ->
                        add(
                            VectorMetricInfo(
                                Res.string.pressure,
                                "${NumberFormatter.format(bp, 0)} hPa",
                                Icons.Rounded.Speed,
                            ),
                        )
                    }
                    gas_resistance?.let { gr ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.gas_resistance,
                                value = "${NumberFormatter.format(gr, 0)} MΩ",
                                icon = Icons.Rounded.BlurOn,
                            ),
                        )
                    }
                    voltage?.let { v ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.voltage,
                                value = "${NumberFormatter.format(v, 2)}V",
                                icon = Icons.Rounded.Bolt,
                            ),
                        )
                    }
                    current?.let { c ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.current,
                                value = "${NumberFormatter.format(c, 1)}mA",
                                icon = Icons.Rounded.Power,
                            ),
                        )
                    }
                    iaq?.let { i -> add(VectorMetricInfo(Res.string.iaq, i.toString(), Icons.Rounded.Air)) }
                    distance?.let { d ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.distance,
                                value = d.toSmallDistanceString(displayUnits),
                                icon = Icons.Rounded.Height,
                            ),
                        )
                    }
                    lux?.let { l ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.lux,
                                value = "${NumberFormatter.format(l, 0)} lx",
                                icon = Icons.Rounded.LightMode,
                            ),
                        )
                    }
                    uv_lux?.let { uvl ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.uv_lux,
                                value = "${NumberFormatter.format(uvl, 0)} lx",
                                icon = Icons.Rounded.LightMode,
                            ),
                        )
                    }
                    wind_speed?.let { ws ->
                        @Suppress("MagicNumber")
                        val normalizedBearing = ((wind_direction ?: 0) + 180) % 360
                        add(
                            VectorMetricInfo(
                                label = Res.string.wind,
                                value = ws.toSpeedString(displayUnits),
                                icon = Icons.Outlined.Navigation,
                                rotateIcon = normalizedBearing.toFloat(),
                            ),
                        )
                    }
                    weight?.let { w ->
                        add(
                            VectorMetricInfo(
                                label = Res.string.weight,
                                value = "${NumberFormatter.format(w, 2)} kg",
                                icon = Icons.Rounded.Scale,
                            ),
                        )
                    }
                    if (temperature != null && relative_humidity != null) {
                        val dewPoint = UnitConversions.calculateDewPoint(temperature!!, relative_humidity!!)
                        if (!dewPoint.isNaN()) {
                            add(
                                DrawableMetricInfo(
                                    label = Res.string.dew_point,
                                    value = dewPoint.toTempString(isFahrenheit),
                                    icon = Res.drawable.ic_dew_point,
                                ),
                            )
                        }
                    }
                    soil_temperature?.let { st ->
                        if (!st.isNaN()) {
                            add(
                                DrawableMetricInfo(
                                    label = Res.string.soil_temperature,
                                    value = st.toTempString(isFahrenheit),
                                    icon = Res.drawable.ic_soil_temperature,
                                ),
                            )
                        }
                    }
                    soil_moisture?.let { sm ->
                        add(DrawableMetricInfo(Res.string.soil_moisture, "$sm%", Res.drawable.ic_soil_moisture))
                    }
                    radiation?.let { r ->
                        add(
                            DrawableMetricInfo(
                                label = Res.string.radiation,
                                value = "${NumberFormatter.format(r, 1)} µR/h",
                                icon = Res.drawable.ic_radioactive,
                            ),
                        )
                    }
                }
            }
        }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        vectorMetrics.forEach { metric ->
            if (metric is DrawableMetricInfo) {
                DrawableInfoCard(
                    iconRes = metric.icon,
                    text = stringResource(metric.label),
                    value = metric.value,
                    rotateIcon = metric.rotateIcon,
                )
            } else if (metric is VectorMetricInfo) {
                InfoCard(
                    icon = metric.icon,
                    text = stringResource(metric.label),
                    value = metric.value,
                    rotateIcon = metric.rotateIcon,
                )
            }
        }
    }
}
