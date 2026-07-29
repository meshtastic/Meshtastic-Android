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
 * Displays power metrics for a node: for every channel reporting a voltage, its voltage and — when the channel reports
 * one — its current are stacked in a single vertical column so the pair stays visually grouped and the columns flow
 * side by side.
 */
@Composable
internal fun PowerMetrics(node: Node) {
    val channels =
        with(node.powerMetrics) {
            listOf(
                Triple(Res.string.channel_1, ch1_voltage, ch1_current),
                Triple(Res.string.channel_2, ch2_voltage, ch2_current),
                Triple(Res.string.channel_3, ch3_voltage, ch3_current),
            )
        }
            .mapNotNull { (label, voltage, current) ->
                // Voltage and current both carry presence: an absent reading is hidden, but a reported 0V / 0mA is a
                // real measurement and is shown.
                voltage?.let { volts ->
                    listOfNotNull(
                        VectorMetricInfo(label, "${NumberFormatter.format(volts, 2)}V", MeshtasticIcons.Voltage),
                        current?.let { milliAmps ->
                            VectorMetricInfo(
                                label,
                                "${NumberFormatter.format(milliAmps, 1)}mA",
                                MeshtasticIcons.PowerSupply,
                            )
                        },
                    )
                }
            }

    MetricCardFlow(groups = channels)
}
