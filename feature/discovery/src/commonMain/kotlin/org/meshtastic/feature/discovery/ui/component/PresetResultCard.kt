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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.discovery.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.discovery_stat_avg_airtime_rate
import org.meshtastic.core.resources.discovery_stat_avg_channel_utilization
import org.meshtastic.core.resources.discovery_stat_direct
import org.meshtastic.core.resources.discovery_stat_mesh
import org.meshtastic.core.resources.discovery_stat_messages
import org.meshtastic.core.resources.discovery_stat_sensor_pkts
import org.meshtastic.core.resources.discovery_stat_unique_nodes
import org.meshtastic.feature.discovery.ui.StatRow
import org.meshtastic.feature.discovery.ui.formatDuration

@Composable
fun PresetResultCard(
    result: DiscoveryPresetResultEntity,
    @Suppress("UnusedParameter") nodes: List<DiscoveredNodeEntity>,
    modifier: Modifier = Modifier,
    aiSummary: String? = null,
    rank: Int? = null,
    isTied: Boolean = false,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            PresetHeader(result = result, rank = rank, isTied = isTied)
            Spacer(modifier = Modifier.height(12.dp))

            StatsGrid(result = result)
            Spacer(modifier = Modifier.height(8.dp))

            NodeBreakdown(result = result)
            Spacer(modifier = Modifier.height(8.dp))

            MessageBreakdown(result = result)

            // Per-preset AI summary
            val summaryText = aiSummary ?: result.aiSummary
            if (!summaryText.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (result.numPacketsTx > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                RfHealthSection(result = result)
            }
        }
    }
}

@Composable
private fun PresetHeader(result: DiscoveryPresetResultEntity, rank: Int?, isTied: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = result.presetName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (rank != null) {
                val rankLabel = if (isTied) "#$rank (tied)" else "#$rank"
                val rankColor =
                    if (rank == 1 && !isTied) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Text(text = rankLabel, style = MaterialTheme.typography.labelMedium, color = rankColor)
            }
        }
        Text(
            text = formatDuration(result.dwellDurationSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsGrid(result: DiscoveryPresetResultEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StatRow(label = stringResource(Res.string.discovery_stat_unique_nodes), value = result.uniqueNodes.toString())
        StatRow(
            label = stringResource(Res.string.discovery_stat_avg_channel_utilization),
            value = "${NumberFormatter.format(result.avgChannelUtilization, 1)}%",
        )
        StatRow(
            label = stringResource(Res.string.discovery_stat_avg_airtime_rate),
            value = "${NumberFormatter.format(result.avgAirtimeRate, 1)}%",
        )
    }
}

@Composable
private fun NodeBreakdown(result: DiscoveryPresetResultEntity) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MetricChip(
            label = stringResource(Res.string.discovery_stat_direct),
            value = result.directNeighborCount.toString(),
            modifier = Modifier.weight(1f),
        )
        MetricChip(
            label = stringResource(Res.string.discovery_stat_mesh),
            value = result.meshNeighborCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MessageBreakdown(result: DiscoveryPresetResultEntity) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MetricChip(
            label = stringResource(Res.string.discovery_stat_messages),
            value = result.messageCount.toString(),
            modifier = Modifier.weight(1f),
        )
        MetricChip(
            label = stringResource(Res.string.discovery_stat_sensor_pkts),
            value = result.sensorPacketCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
