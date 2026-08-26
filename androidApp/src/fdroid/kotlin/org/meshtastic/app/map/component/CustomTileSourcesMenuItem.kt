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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.app.map.getFileName
import org.meshtastic.app.map.importMbTiles
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.app.map.repository.CustomTileProviderRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.manage_custom_tile_sources
import java.io.File
import kotlin.uuid.Uuid

/**
 * The F-Droid map's entry point for editing custom raster tile sources.
 *
 * Fills the basemap menu's trailing slot on the MapLibre map. Kept in the flavor rather than the shared map module
 * because the editor and its store live in `androidApp`, and the shared module cannot depend on the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTileSourcesMenuItem(modifier: Modifier = Modifier) {
    val repository: CustomTileProviderRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val providers by repository.getCustomTileProviders().collectAsStateWithLifecycle(emptyList())

    // The picked archive is copied into app storage before it is stored as a source: MapLibre opens an MBTiles file
    // by path, and a document picker hands back a content:// URI into another app's provider.
    val mbTilesPicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                scope.launch {
                    val name = uri.getFileName(context)
                    val stored = importMbTiles(context, uri, "mbtiles_${Uuid.random()}.mbtiles")
                    if (stored != null) {
                        repository.addCustomTileProvider(
                            CustomTileProviderConfig(
                                name = name.substringBeforeLast('.'),
                                urlTemplate = "",
                                localUri = Uri.fromFile(File(stored)).toString(),
                            ),
                        )
                    }
                }
            }
        }

    var sheetVisible by remember { mutableStateOf(false) }

    DropdownMenuItem(
        text = { Text(text = stringResource(Res.string.manage_custom_tile_sources)) },
        onClick = { sheetVisible = true },
        modifier = modifier,
    )

    if (sheetVisible) {
        ModalBottomSheet(onDismissRequest = { sheetVisible = false }) {
            CustomTileProviderManager(
                providers = providers,
                onAdd = { config -> scope.launch { repository.addCustomTileProvider(config) } },
                onUpdate = { config -> scope.launch { repository.updateCustomTileProvider(config) } },
                onDelete = { id -> scope.launch { repository.deleteCustomTileProvider(id) } },
                onAddLocalMbTiles = {
                    // Any MIME type: providers hand back application/octet-stream for a perfectly good .mbtiles.
                    mbTilesPicker.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        },
                    )
                },
            )
        }
    }
}
