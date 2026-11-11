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
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PowerSupply
import org.meshtastic.core.ui.icon.Voltage
import org.meshtastic.feature.node.metrics.powerChannelTexts
import org.meshtastic.feature.node.metrics.reportingPowerChannels
import org.meshtastic.feature.node.model.VectorMetricInfo

/**
 * Displays power metrics for a node: every channel that reports a voltage, a current or both, under the user's label or
 * "Channel N". Each reported half stacks in one column so a channel stays grouped, and a half the channel did not
 * report is not shown.
 */
@Composable
internal fun PowerMetrics(node: Node) {
    val pm = node.powerMetrics
    val groups =
        reportingPowerChannels(pm).map { channel ->
            val texts = powerChannelTexts(channel.voltage(pm), channel.current(pm))
            val label = node.powerChannelLabels.getOrNull(channel.ordinal)?.takeIf { it.isNotBlank() }
            listOfNotNull(
                texts.voltage?.let {
                    VectorMetricInfo(channel.strRes, it, MeshtasticIcons.Voltage, customLabel = label)
                },
                texts.current?.let {
                    VectorMetricInfo(
                        channel.strRes,
                        it,
                        MeshtasticIcons.PowerSupply,
                        customLabel = label,
                    )
                },
            )
        }

    MetricCardFlow(groups = groups)
}
