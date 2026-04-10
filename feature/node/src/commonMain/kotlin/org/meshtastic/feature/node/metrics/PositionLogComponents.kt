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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.model.util.GeoConstants.HEADING_DEG
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.alt
import org.meshtastic.core.resources.heading
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.core.resources.sats
import org.meshtastic.core.resources.speed
import org.meshtastic.core.resources.speed_kmh
import org.meshtastic.core.resources.timestamp
import org.meshtastic.core.ui.util.formatPositionTime
import org.meshtastic.proto.Config
import org.meshtastic.proto.Position

@Composable
private fun RowScope.PositionText(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    )
}

private const val WEIGHT_10 = .10f
private const val WEIGHT_15 = .15f
private const val WEIGHT_20 = .20f
private const val WEIGHT_40 = .40f

@Composable
fun PositionLogHeader(compactWidth: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        PositionText(stringResource(Res.string.latitude), WEIGHT_20)
        PositionText(stringResource(Res.string.longitude), WEIGHT_20)
        PositionText(stringResource(Res.string.sats), WEIGHT_10)
        PositionText(stringResource(Res.string.alt), WEIGHT_15)
        if (!compactWidth) {
            PositionText(stringResource(Res.string.speed), WEIGHT_15)
            PositionText(stringResource(Res.string.heading), WEIGHT_15)
        }
        PositionText(stringResource(Res.string.timestamp), WEIGHT_40)
    }
}

@Composable
fun PositionItem(compactWidth: Boolean, position: Position, system: Config.DisplayConfig.DisplayUnits) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PositionText(formatString("%.5f", (position.latitude_i ?: 0) * DEG_D), WEIGHT_20)
        PositionText(formatString("%.5f", (position.longitude_i ?: 0) * DEG_D), WEIGHT_20)
        PositionText(position.sats_in_view.toString(), WEIGHT_10)
        PositionText((position.altitude ?: 0).metersIn(system).toString(system), WEIGHT_15)
        if (!compactWidth) {
            PositionText(stringResource(Res.string.speed_kmh, position.ground_speed ?: 0), WEIGHT_15)
            PositionText(formatString("%.0f°", (position.ground_track ?: 0) * HEADING_DEG), WEIGHT_15)
        }
        PositionText(position.formatPositionTime(), WEIGHT_40)
    }
}

@Composable
fun ColumnScope.PositionList(
    compactWidth: Boolean,
    positions: List<Position>,
    displayUnits: Config.DisplayConfig.DisplayUnits,
) {
    LazyColumn(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        items(positions) { position -> PositionItem(compactWidth, position, displayUnits) }
    }
}
