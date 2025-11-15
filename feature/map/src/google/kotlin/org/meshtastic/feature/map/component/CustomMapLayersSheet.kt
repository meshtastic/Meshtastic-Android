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

package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add_layer
import org.meshtastic.core.strings.hide_layer
import org.meshtastic.core.strings.manage_map_layers
import org.meshtastic.core.strings.map_layer_formats
import org.meshtastic.core.strings.no_map_layers_loaded
import org.meshtastic.core.strings.remove_layer
import org.meshtastic.core.strings.show_layer
import org.meshtastic.feature.map.MapLayerItem

@Suppress("LongMethod")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CustomMapLayersSheet(
    mapLayers: List<MapLayerItem>,
    onToggleVisibility: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onAddLayerClicked: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(Res.string.manage_map_layers),
                style = MaterialTheme.typography.headlineSmall,
            )
            HorizontalDivider()
        }
        item {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
                text = stringResource(Res.string.map_layer_formats),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (mapLayers.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(Res.string.no_map_layers_loaded),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(mapLayers, key = { it.id }) { layer ->
                ListItem(
                    headlineContent = { Text(layer.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onToggleVisibility(layer.id) }) {
                                Icon(
                                    imageVector =
                                    if (layer.isVisible) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription =
                                    stringResource(
                                        if (layer.isVisible) {
                                            Res.string.hide_layer
                                        } else {
                                            Res.string.show_layer
                                        },
                                    ),
                                )
                            }
                            IconButton(onClick = { onRemoveLayer(layer.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(Res.string.remove_layer),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
        item {
            Button(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = onAddLayerClicked) {
                Text(stringResource(Res.string.add_layer))
            }
        }
    }
}
