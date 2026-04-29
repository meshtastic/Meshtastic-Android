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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.discovery.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.feature.discovery.ui.StatRow

@Composable
fun RfHealthSection(result: DiscoveryPresetResultEntity, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "RF Health", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        StatRow(label = "Packets TX", value = result.numPacketsTx.toString())
        StatRow(label = "Packets RX", value = result.numPacketsRx.toString())
        StatRow(label = "Bad packets", value = result.numPacketsRxBad.toString())
        StatRow(label = "Duplicate packets", value = result.numRxDupe.toString())
        StatRow(label = "Success rate", value = "${NumberFormatter.format(result.packetSuccessRate, 1)}%")
        StatRow(label = "Failure rate", value = "${NumberFormatter.format(result.packetFailureRate, 1)}%")

        if (result.numOnlineNodes > 0 || result.numTotalNodes > 0) {
            StatRow(label = "Online / Total nodes", value = "${result.numOnlineNodes} / ${result.numTotalNodes}")
        }
    }
}
