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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.model.util.GeoConstants.HEADING_DEG
import org.meshtastic.core.model.util.kmhIn
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.alt
import org.meshtastic.core.resources.heading
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.core.resources.position
import org.meshtastic.core.resources.sats
import org.meshtastic.core.resources.speed
import org.meshtastic.core.resources.speed_kmh
import org.meshtastic.core.resources.speed_mph
import org.meshtastic.core.resources.timestamp
import org.meshtastic.core.ui.util.formatPositionTime
import org.meshtastic.proto.Position

/**
 * Detail for the track point the user tapped.
 *
 * The Google flavor shows exactly these rows in a marker info window. A MapLibre marker is a layer feature and has no
 * info window, so the same detail sits at the foot of the map instead — a card that appears when a point is selected
 * and goes away when nothing is. Draws nothing for a null [position], which is also how it hides.
 */
@Composable
internal fun TrackPointCard(position: Position?, displayUnits: MeasurementSystem, modifier: Modifier = Modifier) {
    if (position == null) return

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(CARD_PADDING.dp)) {
            Text(text = stringResource(Res.string.position), style = MaterialTheme.typography.labelLarge)
            DetailRow(
                label = stringResource(Res.string.latitude),
                value = NumberFormatter.format((position.latitude_i ?: 0) * DEG_D, COORDINATE_DECIMALS),
            )
            DetailRow(
                label = stringResource(Res.string.longitude),
                value = NumberFormatter.format((position.longitude_i ?: 0) * DEG_D, COORDINATE_DECIMALS),
            )
            DetailRow(label = stringResource(Res.string.sats), value = position.sats_in_view.toString())
            DetailRow(
                label = stringResource(Res.string.alt),
                value = (position.altitude ?: 0).metersIn(displayUnits).toString(displayUnits),
            )
            DetailRow(
                label = stringResource(Res.string.speed),
                // ground_speed is km/h on the wire (proto canon), not m/s.
                value =
                stringResource(
                    if (displayUnits == MeasurementSystem.IMPERIAL) Res.string.speed_mph else Res.string.speed_kmh,
                    (position.ground_speed ?: 0).kmhIn(displayUnits),
                ),
            )
            DetailRow(
                label = stringResource(Res.string.heading),
                value = NumberFormatter.format((position.ground_track ?: 0) * HEADING_DEG, 0) + "°",
            )
            DetailRow(label = stringResource(Res.string.timestamp), value = position.formatPositionTime())
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(LABEL_GAP.dp))
        Text(text = value, style = MaterialTheme.typography.labelMedium)
    }
}

private const val CARD_PADDING = 8
private const val LABEL_GAP = 16
private const val COORDINATE_DECIMALS = 5
