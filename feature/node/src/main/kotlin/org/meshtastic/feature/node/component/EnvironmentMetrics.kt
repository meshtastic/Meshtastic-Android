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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Navigation
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
                    val temp = temperature
                    if (temp != null && temp != 0f) {
                        add(
                            VectorMetricInfo(
                                Res.string.temperature,
                                temp.toTempString(isFahrenheit),
                                Icons.Default.Thermostat,
                            ),
                        )
                    }
                    val rh = relative_humidity
                    if (rh != null && rh != 0f) {
                        add(VectorMetricInfo(Res.string.humidity, "%.0f%%".format(rh), Icons.Default.WaterDrop))
                    }
                    val bp = barometric_pressure
                    if (bp != null && bp != 0f) {
                        add(VectorMetricInfo(Res.string.pressure, "%.0f hPa".format(bp), Icons.Default.Speed))
                    }
                    val gr = gas_resistance
                    if (gr != null && gr != 0f) {
                        add(VectorMetricInfo(Res.string.gas_resistance, "%.0f MΩ".format(gr), Icons.Default.BlurOn))
                    }
                    val v = voltage
                    if (v != null && v != 0f) {
                        add(VectorMetricInfo(Res.string.voltage, "%.2fV".format(v), Icons.Default.Bolt))
                    }
                    val c = current
                    if (c != null && c != 0f) {
                        add(VectorMetricInfo(Res.string.current, "%.1fmA".format(c), Icons.Default.Power))
                    }
                    val i = iaq
                    if (i != null && i != 0) add(VectorMetricInfo(Res.string.iaq, i.toString(), Icons.Default.Air))
                    val d = distance
                    if (d != null && d != 0f) {
                        add(
                            VectorMetricInfo(
                                Res.string.distance,
                                d.toSmallDistanceString(displayUnits),
                                Icons.Default.Height,
                            ),
                        )
                    }
                    val l = lux
                    if (l != null && l != 0f) {
                        add(VectorMetricInfo(Res.string.lux, "%.0f lx".format(l), Icons.Default.LightMode))
                    }
                    val uv = uv_lux
                    if (uv != null && uv != 0f) {
                        add(VectorMetricInfo(Res.string.uv_lux, "%.0f lx".format(uv), Icons.Default.LightMode))
                    }
                    val ws = wind_speed
                    if (ws != null && ws != 0f) {
                        @Suppress("MagicNumber")
                        val normalizedBearing = ((wind_direction ?: 0) + 180) % 360
                        add(
                            VectorMetricInfo(
                                Res.string.wind,
                                ws.toSpeedString(displayUnits),
                                Icons.Outlined.Navigation,
                                normalizedBearing.toFloat(),
                            ),
                        )
                    }
                    val w = weight
                    if (w != null && w != 0f) {
                        add(VectorMetricInfo(Res.string.weight, "%.2f kg".format(w), Icons.Default.Scale))
                    }
                }
            }
        }
    val drawableMetrics =
        remember(node.environmentMetrics, isFahrenheit) {
            buildList {
                with(node.environmentMetrics) {
                    val temp = temperature
                    val rh = relative_humidity
                    if (temp != null && temp != 0f && rh != null && rh != 0f) {
                        val dewPoint = UnitConversions.calculateDewPoint(temp, rh)
                        add(
                            DrawableMetricInfo(
                                Res.string.dew_point,
                                dewPoint.toTempString(isFahrenheit),
                                org.meshtastic.feature.node.R.drawable.ic_outlined_dew_point_24,
                            ),
                        )
                    }
                    val st = soil_temperature
                    if (st != null && st != 0f) {
                        add(
                            DrawableMetricInfo(
                                Res.string.soil_temperature,
                                st.toTempString(isFahrenheit),
                                org.meshtastic.feature.node.R.drawable.soil_temperature,
                            ),
                        )
                    }
                    val sm = soil_moisture
                    if (sm != null && sm != 0) {
                        add(
                            DrawableMetricInfo(
                                Res.string.soil_moisture,
                                "%d%%".format(sm),
                                org.meshtastic.feature.node.R.drawable.soil_moisture,
                            ),
                        )
                    }
                    val r = radiation
                    if (r != null && r != 0f) {
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
            InfoCard(
                icon = metric.icon,
                text = stringResource(metric.label),
                value = metric.value,
                rotateIcon = metric.rotateIcon,
            )
        }
        drawableMetrics.forEach { metric ->
            DrawableInfoCard(
                iconRes = metric.icon,
                text = stringResource(metric.label),
                value = metric.value,
                rotateIcon = metric.rotateIcon,
            )
        }
    }
}
