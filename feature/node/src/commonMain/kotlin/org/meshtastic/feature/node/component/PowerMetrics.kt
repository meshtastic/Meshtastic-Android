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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_1
import org.meshtastic.core.resources.channel_2
import org.meshtastic.core.resources.channel_3
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PowerSupply
import org.meshtastic.core.ui.icon.Voltage
import org.meshtastic.feature.node.model.VectorMetricInfo

/**
 * Displays power metrics for a node, grouped by channel with voltage and current readings.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun PowerMetrics(node: Node) {
    val metrics = buildList {
        with(node.powerMetrics) {
            if ((ch1_voltage ?: 0f) != 0f) {
                add(
                    Pair(
                        VectorMetricInfo(
                            Res.string.channel_1,
                            "${NumberFormatter.format(ch1_voltage ?: 0f, 2)}V",
                            MeshtasticIcons.Voltage,
                        ),
                        VectorMetricInfo(
                            Res.string.channel_1,
                            "${NumberFormatter.format(ch1_current ?: 0f, 1)}mA",
                            MeshtasticIcons.PowerSupply,
                        ),
                    ),
                )
            }
            if ((ch2_voltage ?: 0f) != 0f) {
                add(
                    Pair(
                        VectorMetricInfo(
                            Res.string.channel_2,
                            "${NumberFormatter.format(ch2_voltage ?: 0f, 2)}V",
                            MeshtasticIcons.Voltage,
                        ),
                        VectorMetricInfo(
                            Res.string.channel_2,
                            "${NumberFormatter.format(ch2_current ?: 0f, 1)}mA",
                            MeshtasticIcons.PowerSupply,
                        ),
                    ),
                )
            }
            if ((ch3_voltage ?: 0f) != 0f) {
                add(
                    Pair(
                        VectorMetricInfo(
                            Res.string.channel_3,
                            "${NumberFormatter.format(ch3_voltage ?: 0f, 2)}V",
                            MeshtasticIcons.Voltage,
                        ),
                        VectorMetricInfo(
                            Res.string.channel_3,
                            "${NumberFormatter.format(ch3_current ?: 0f, 1)}mA",
                            MeshtasticIcons.PowerSupply,
                        ),
                    ),
                )
            }
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        metrics.forEach { (voltageMetric, currentMetric) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoCard(
                    icon = voltageMetric.icon,
                    text = stringResource(voltageMetric.label),
                    value = voltageMetric.value,
                )
                InfoCard(
                    icon = currentMetric.icon,
                    text = stringResource(currentMetric.label),
                    value = currentMetric.value,
                )
            }
        }
    }
}
