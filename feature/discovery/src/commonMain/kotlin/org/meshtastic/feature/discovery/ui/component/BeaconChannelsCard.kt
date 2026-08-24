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
package org.meshtastic.feature.discovery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_beacon_channels_title
import org.meshtastic.core.resources.mesh_beacon_preset_indicator
import org.meshtastic.core.ui.icon.CellTower
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.discovery.BeaconChannel

private val CARD_PADDING = 16.dp
private val ROW_SPACING = 8.dp

/**
 * Scan-setup section listing distinct custom channels that beacons advertised (Apple 014-mesh-beacons FR-007).
 * Selecting a row adds a custom-channel scan target that tunes the radio's primary channel to that name during its
 * dwell.
 */
@Composable
fun BeaconChannelsCard(
    channels: List<BeaconChannel>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
            Text(
                text = stringResource(Res.string.mesh_beacon_channels_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            channels.forEach { channel ->
                val selected = channel.id in selectedIds
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onToggle(channel.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ROW_SPACING),
                ) {
                    Checkbox(checked = selected, onCheckedChange = { onToggle(channel.id) }, enabled = enabled)
                    Icon(
                        imageVector = MeshtasticIcons.CellTower,
                        contentDescription = stringResource(Res.string.mesh_beacon_preset_indicator),
                    )
                    Column {
                        Text(text = channel.name, style = MaterialTheme.typography.bodyMedium)
                        channel.preset?.let {
                            Text(
                                text = it.displayName(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
