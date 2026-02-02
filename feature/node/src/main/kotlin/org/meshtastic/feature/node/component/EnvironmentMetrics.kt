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
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.model.util.toSmallDistanceString
import org.meshtastic.core.model.util.toSpeedString
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.current
import org.meshtastic.core.strings.dew_point
import org.meshtastic.core.strings.distance
import org.meshtastic.core.strings.gas_resistance
import org.meshtastic.core.strings.humidity
import org.meshtastic.core.strings.iaq
import org.meshtastic.core.strings.lux
import org.meshtastic.core.strings.pressure
import org.meshtastic.core.strings.radiation
import org.meshtastic.core.strings.soil_moisture
import org.meshtastic.core.strings.soil_temperature
import org.meshtastic.core.strings.temperature
import org.meshtastic.core.strings.uv_lux
import org.meshtastic.core.strings.voltage
import org.meshtastic.core.strings.weight
import org.meshtastic.core.strings.wind
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
                                    Res.string.temperature,
                                    temp.toTempString(isFahrenheit),
                                    Icons.Rounded.Thermostat,
                                ),
                            )
                        }
                    }
                    relative_humidity?.let { rh ->
                        add(VectorMetricInfo(Res.string.humidity, "%.0f%%".format(rh), Icons.Rounded.WaterDrop))
                    }
                    barometric_pressure?.let { bp ->
                        add(VectorMetricInfo(Res.string.pressure, "%.0f hPa".format(bp), Icons.Rounded.Speed))
                    }
                    gas_resistance?.let { gr ->
                        add(VectorMetricInfo(Res.string.gas_resistance, "%.0f MΩ".format(gr), Icons.Rounded.BlurOn))
                    }
                    voltage?.let { v ->
                        add(VectorMetricInfo(Res.string.voltage, "%.2fV".format(v), Icons.Rounded.Bolt))
                    }
                    current?.let { c ->
                        add(VectorMetricInfo(Res.string.current, "%.1fmA".format(c), Icons.Rounded.Power))
                    }
                    iaq?.let { i -> add(VectorMetricInfo(Res.string.iaq, i.toString(), Icons.Rounded.Air)) }
                    distance?.let { d ->
                        add(
                            VectorMetricInfo(
                                Res.string.distance,
                                d.toSmallDistanceString(displayUnits),
                                Icons.Rounded.Height,
                            ),
                        )
                    }
                    lux?.let { l ->
                        add(VectorMetricInfo(Res.string.lux, "%.0f lx".format(l), Icons.Rounded.LightMode))
                    }
                    uv_lux?.let { uvl ->
                        add(VectorMetricInfo(Res.string.uv_lux, "%.0f lx".format(uvl), Icons.Rounded.LightMode))
                    }
                    wind_speed?.let { ws ->
                        @Suppress("MagicNumber")
                        val normalizedBearing = ((wind_direction ?: 0) + 180) % 360
                        add(
                            VectorMetricInfo(
                                Res.string.wind,
                                ws.toFloat().toSpeedString(displayUnits),
                                Icons.Outlined.Navigation,
                                normalizedBearing.toFloat(),
                            ),
                        )
                    }
                    weight?.let { w ->
                        add(VectorMetricInfo(Res.string.weight, "%.2f kg".format(w), Icons.Rounded.Scale))
                    }
                    if (temperature != null && relative_humidity != null) {
                        val dewPoint = UnitConversions.calculateDewPoint(temperature!!, relative_humidity!!)
                        if (!dewPoint.isNaN()) {
                            add(
                                DrawableMetricInfo(
                                    Res.string.dew_point,
                                    dewPoint.toTempString(isFahrenheit),
                                    org.meshtastic.feature.node.R.drawable.ic_outlined_dew_point_24,
                                ),
                            )
                        }
                    }
                    soil_temperature?.let { st ->
                        if (!st.isNaN()) {
                            add(
                                DrawableMetricInfo(
                                    Res.string.soil_temperature,
                                    st.toTempString(isFahrenheit),
                                    org.meshtastic.feature.node.R.drawable.soil_temperature,
                                ),
                            )
                        }
                    }
                    soil_moisture?.let { sm ->
                        add(
                            DrawableMetricInfo(
                                Res.string.soil_moisture,
                                "%d%%".format(sm),
                                org.meshtastic.feature.node.R.drawable.soil_moisture,
                            ),
                        )
                    }
                    radiation?.let { r ->
                        add(
                            DrawableMetricInfo(
                                Res.string.radiation,
                                "%.1f µR/h".format(r),
                                org.meshtastic.feature.node.R.drawable.ic_filled_radioactive_24,
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
