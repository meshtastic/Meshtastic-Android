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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Power
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.channel_1
import org.meshtastic.core.strings.channel_2
import org.meshtastic.core.strings.channel_3
import org.meshtastic.feature.node.model.VectorMetricInfo

/**
 * Displays environmental metrics for a node, including temperature, humidity, pressure, and other sensor data.
 *
 * WARNING: All metrics must be added in pairs (e.g., voltage and current for each channel) due to the display logic,
 * which arranges metrics in columns of two. If an odd number of metrics is provided, the UI may not display as
 * intended.
 */
@Composable
internal fun PowerMetrics(node: Node) {
    val metrics =
        remember(node.powerMetrics) {
            buildList {
                with(node.powerMetrics) {
                    if (ch1Voltage != 0f) {
                        add(VectorMetricInfo(Res.string.channel_1, "%.2fV".format(ch1Voltage), Icons.Default.Bolt))
                        add(VectorMetricInfo(Res.string.channel_1, "%.1fmA".format(ch1Current), Icons.Default.Power))
                    }
                    if (ch2Voltage != 0f) {
                        add(VectorMetricInfo(Res.string.channel_2, "%.2fV".format(ch2Voltage), Icons.Default.Bolt))
                        add(VectorMetricInfo(Res.string.channel_2, "%.1fmA".format(ch2Current), Icons.Default.Power))
                    }
                    if (ch3Voltage != 0f) {
                        add(VectorMetricInfo(Res.string.channel_3, "%.2fV".format(ch3Voltage), Icons.Default.Bolt))
                        add(VectorMetricInfo(Res.string.channel_3, "%.1fmA".format(ch3Current), Icons.Default.Power))
                    }
                }
            }
        }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Column {
                rowMetrics.forEach { metric ->
                    InfoCard(icon = metric.icon, text = stringResource(metric.label), value = metric.value)
                }
            }
        }
    }
}
