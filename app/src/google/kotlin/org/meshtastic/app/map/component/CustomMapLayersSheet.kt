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
package org.meshtastic.app.map.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.app.map.MapLayerItem
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_layer
import org.meshtastic.core.resources.add_network_layer
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.hide_layer
import org.meshtastic.core.resources.manage_map_layers
import org.meshtastic.core.resources.map_layer_formats
import org.meshtastic.core.resources.name
import org.meshtastic.core.resources.network_layer_url_hint
import org.meshtastic.core.resources.no_map_layers_loaded
import org.meshtastic.core.resources.refresh
import org.meshtastic.core.resources.remove_layer
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.show_layer
import org.meshtastic.core.resources.url
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff

@Suppress("LongMethod")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CustomMapLayersSheet(
    mapLayers: List<MapLayerItem>,
    onToggleVisibility: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onAddLayerClicked: () -> Unit,
    onRefreshLayer: (String) -> Unit,
    onAddNetworkLayer: (String, String) -> Unit,
) {
    var showAddNetworkLayerDialog by remember { mutableStateOf(false) }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (layer.isNetwork) {
                                if (layer.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).padding(4.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    IconButton(onClick = { onRefreshLayer(layer.id) }) {
                                        Icon(
                                            imageVector = MeshtasticIcons.Refresh,
                                            contentDescription = stringResource(Res.string.refresh),
                                        )
                                    }
                                }
                            }
                            IconToggleButton(
                                checked = layer.isVisible,
                                onCheckedChange = { onToggleVisibility(layer.id) },
                            ) {
                                Icon(
                                    imageVector =
                                    if (layer.isVisible) {
                                        MeshtasticIcons.Visibility
                                    } else {
                                        MeshtasticIcons.VisibilityOff
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
                                    imageVector = MeshtasticIcons.Delete,
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddLayerClicked) {
                    Text(stringResource(Res.string.add_layer))
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = { showAddNetworkLayerDialog = true }) {
                    Text(stringResource(Res.string.add_network_layer))
                }
            }
        }
    }

    if (showAddNetworkLayerDialog) {
        AddNetworkLayerDialog(
            onDismiss = { showAddNetworkLayerDialog = false },
            onConfirm = { name, url ->
                onAddNetworkLayer(name, url)
                showAddNetworkLayerDialog = false
            },
        )
    }
}

@Composable
fun AddNetworkLayerDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    MeshtasticDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.add_network_layer),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.url)) },
                    placeholder = { Text(stringResource(Res.string.network_layer_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        onConfirm = { onConfirm(name, url) },
        confirmTextRes = Res.string.save,
        dismissTextRes = Res.string.cancel,
    )
}
