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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.model.util.GeoConstants.HEADING_DEG
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.alt
import org.meshtastic.core.resources.heading
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.core.resources.sats
import org.meshtastic.core.resources.speed_kmh
import org.meshtastic.core.ui.theme.GraphColors
import org.meshtastic.proto.Config
import org.meshtastic.proto.Position

/**
 * A [SelectableMetricCard]-based position item that matches the visual style of [DeviceMetricsCard],
 * [SignalMetricsCard], and other metric cards. Replaces the previous table-row layout with a card that shows timestamp,
 * coordinates, satellites, altitude, speed, and heading.
 */
@Composable
@Suppress("LongMethod")
fun PositionCard(
    position: Position,
    displayUnits: Config.DisplayConfig.DisplayUnits,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val time = position.time.toLong() * MS_PER_SEC
    val latitude = formatString("%.5f", (position.latitude_i ?: 0) * DEG_D)
    val longitude = formatString("%.5f", (position.longitude_i ?: 0) * DEG_D)

    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            /* Timestamp */
            Text(
                text = DateFormatter.formatDateTime(time),
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            /* Coordinates */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricValueRow(color = GraphColors.Blue, text = "${stringResource(Res.string.latitude)}: $latitude")
                    Spacer(Modifier.width(12.dp))
                    MetricValueRow(
                        color = GraphColors.Green,
                        text = "${stringResource(Res.string.longitude)}: $longitude",
                    )
                }
                Text(
                    text = "${stringResource(Res.string.sats)}: ${position.sats_in_view}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            /* Alt, Speed, Heading */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricValueRow(
                        color = GraphColors.Purple,
                        text =
                        "${stringResource(Res.string.alt)}: ${
                            (position.altitude ?: 0).metersIn(displayUnits).toString(displayUnits)
                        }",
                    )
                    if (position.ground_speed != null && position.ground_speed != 0) {
                        Spacer(Modifier.width(12.dp))
                        MetricValueRow(
                            color = GraphColors.Gold,
                            text = stringResource(Res.string.speed_kmh, position.ground_speed ?: 0),
                        )
                    }
                }
                if (position.ground_track != null && position.ground_track != 0) {
                    Text(
                        text =
                        "${stringResource(Res.string.heading)}: ${
                            formatString("%.0f", (position.ground_track ?: 0) * HEADING_DEG)
                        }\u00B0",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
        }
    }
}
