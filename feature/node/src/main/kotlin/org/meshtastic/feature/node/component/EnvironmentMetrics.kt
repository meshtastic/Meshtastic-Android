/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.compose.ui.res.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.model.util.UnitConversions.toTempString
import org.meshtastic.core.model.util.toSmallDistanceString
import org.meshtastic.core.model.util.toSpeedString
import org.meshtastic.core.strings.R
import org.meshtastic.feature.node.model.DrawableMetricInfo
import org.meshtastic.feature.node.model.VectorMetricInfo
import org.meshtastic.proto.ConfigProtos

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun EnvironmentMetrics(
    node: Node,
    displayUnits: ConfigProtos.Config.DisplayConfig.DisplayUnits,
    isFahrenheit: Boolean = false,
) {
    val vectorMetrics =
        remember(node.environmentMetrics, isFahrenheit, displayUnits) {
            buildList {
                with(node.environmentMetrics) {
                    if (hasTemperature()) {
                        add(
                            VectorMetricInfo(
                                R.string.temperature,
                                temperature.toTempString(isFahrenheit),
                                Icons.Default.Thermostat,
                            ),
                        )
                    }
                    if (hasRelativeHumidity()) {
                        add(
                            VectorMetricInfo(
                                R.string.humidity,
                                "%.0f%%".format(relativeHumidity),
                                Icons.Default.WaterDrop,
                            ),
                        )
                    }
                    if (hasBarometricPressure()) {
                        add(
                            VectorMetricInfo(
                                R.string.pressure,
                                "%.0f hPa".format(barometricPressure),
                                Icons.Default.Speed,
                            ),
                        )
                    }
                    if (hasGasResistance()) {
                        add(
                            VectorMetricInfo(
                                R.string.gas_resistance,
                                "%.0f MΩ".format(gasResistance),
                                Icons.Default.BlurOn,
                            ),
                        )
                    }
                    if (hasVoltage()) {
                        add(VectorMetricInfo(R.string.voltage, "%.2fV".format(voltage), Icons.Default.Bolt))
                    }
                    if (hasCurrent()) {
                        add(VectorMetricInfo(R.string.current, "%.1fmA".format(current), Icons.Default.Power))
                    }
                    if (hasIaq()) add(VectorMetricInfo(R.string.iaq, iaq.toString(), Icons.Default.Air))
                    if (hasDistance()) {
                        add(
                            VectorMetricInfo(
                                R.string.distance,
                                distance.toSmallDistanceString(displayUnits),
                                Icons.Default.Height,
                            ),
                        )
                    }
                    if (hasLux()) add(VectorMetricInfo(R.string.lux, "%.0f lx".format(lux), Icons.Default.LightMode))
                    if (hasUvLux()) {
                        add(VectorMetricInfo(R.string.uv_lux, "%.0f lx".format(uvLux), Icons.Default.LightMode))
                    }
                    if (hasWindSpeed()) {
                        @Suppress("MagicNumber")
                        val normalizedBearing = (windDirection + 180) % 360
                        add(
                            VectorMetricInfo(
                                R.string.wind,
                                windSpeed.toSpeedString(displayUnits),
                                Icons.Outlined.Navigation,
                                normalizedBearing.toFloat(),
                            ),
                        )
                    }
                    if (hasWeight()) {
                        add(VectorMetricInfo(R.string.weight, "%.2f kg".format(weight), Icons.Default.Scale))
                    }
                }
            }
        }
    val drawableMetrics =
        remember(node.environmentMetrics, isFahrenheit) {
            buildList {
                with(node.environmentMetrics) {
                    if (hasTemperature() && hasRelativeHumidity()) {
                        val dewPoint = UnitConversions.calculateDewPoint(temperature, relativeHumidity)
                        add(
                            DrawableMetricInfo(
                                R.string.dew_point,
                                dewPoint.toTempString(isFahrenheit),
                                org.meshtastic.feature.node.R.drawable.ic_outlined_dew_point_24,
                            ),
                        )
                    }
                    if (hasSoilTemperature()) {
                        add(
                            DrawableMetricInfo(
                                R.string.soil_temperature,
                                soilTemperature.toTempString(isFahrenheit),
                                org.meshtastic.feature.node.R.drawable.soil_temperature,
                            ),
                        )
                    }
                    if (hasSoilMoisture()) {
                        add(
                            DrawableMetricInfo(
                                R.string.soil_moisture,
                                "%d%%".format(soilMoisture),
                                org.meshtastic.feature.node.R.drawable.soil_moisture,
                            ),
                        )
                    }
                    if (hasRadiation()) {
                        add(
                            DrawableMetricInfo(
                                R.string.radiation,
                                "%.1f µR/h".format(radiation),
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
