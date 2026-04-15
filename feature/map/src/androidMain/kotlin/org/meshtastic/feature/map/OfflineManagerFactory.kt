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
package org.meshtastic.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.material3.OfflinePackListItem
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.rememberOfflineManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.done
import org.meshtastic.core.resources.offline_download
import org.meshtastic.core.resources.offline_download_visible_region
import org.meshtastic.core.resources.offline_downloaded_regions
import org.meshtastic.core.resources.offline_maps
import org.meshtastic.core.resources.offline_saves_tiles
import org.meshtastic.core.resources.offline_unnamed_region
import org.meshtastic.core.ui.icon.CloudDownload
import org.meshtastic.core.ui.icon.MeshtasticIcons

@Suppress("LongMethod")
@Composable
actual fun OfflineMapContent(styleUri: String, cameraState: CameraState) {
    val offlineManager = rememberOfflineManager()
    val coroutineScope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        val unnamedRegion = stringResource(Res.string.offline_unnamed_region)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(Res.string.offline_maps)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Download button for current viewport
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                        Modifier.fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    val projection = cameraState.awaitProjection()
                                    val bounds = projection.queryVisibleBoundingBox()
                                    val pack =
                                        offlineManager.create(
                                            definition =
                                            OfflinePackDefinition.TilePyramid(
                                                styleUrl = styleUri,
                                                bounds = bounds,
                                            ),
                                            metadata = "Region".encodeToByteArray(),
                                        )
                                    offlineManager.resume(pack)
                                }
                            }
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = MeshtasticIcons.CloudDownload,
                            contentDescription = stringResource(Res.string.offline_download),
                            modifier = Modifier.padding(end = 16.dp),
                        )
                        Column {
                            Text(
                                text = stringResource(Res.string.offline_download_visible_region),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(Res.string.offline_saves_tiles),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Existing packs
                    if (offlineManager.packs.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.offline_downloaded_regions),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                        offlineManager.packs.toList().forEach { pack ->
                            key(pack.hashCode()) {
                                OfflinePackListItem(pack = pack, offlineManager = offlineManager) {
                                    Text(pack.metadata?.decodeToString().orEmpty().ifBlank { unnamedRegion })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(Res.string.done)) } },
        )
    }

    // Expose the toggle via a side effect — the parent screen will call this
    // by rendering OfflineMapContent and using the showDialog state
    IconButton(onClick = { showDialog = true }) {
        Icon(imageVector = MeshtasticIcons.CloudDownload, contentDescription = stringResource(Res.string.offline_maps))
    }
}
