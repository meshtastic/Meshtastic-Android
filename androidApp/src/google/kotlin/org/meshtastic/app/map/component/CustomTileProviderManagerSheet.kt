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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.importMbTiles
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.map.component.CustomTileProviderManager
import org.meshtastic.feature.map.layers.getFileName
import java.io.File
import kotlin.uuid.Uuid

@Composable
fun CustomTileProviderManagerSheet(mapViewModel: MapViewModel) {
    val providers by mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mbtilesPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // The archive has to be copied into app storage before it is stored as a source: the tile provider
                    // opens an MBTiles file by path, and a document picker hands back a content:// URI into another
                    // app's provider, which no File can resolve. Storing that URI left local archives silently broken
                    // on this flavour — the F-Droid map has always copied first.
                    scope.launch {
                        val name = uri.getFileName(context)
                        val stored = importMbTiles(context, uri, "mbtiles_${Uuid.random()}.mbtiles")
                        if (stored != null) {
                            mapViewModel.addCustomTileProvider(
                                name = name.substringBeforeLast('.'),
                                urlTemplate = "",
                                localUri = Uri.fromFile(File(stored)).toString(),
                            )
                        }
                    }
                }
            }
        }

    LaunchedEffect(Unit) { mapViewModel.errorFlow.collectLatest { context.showToast(it) } }

    CustomTileProviderManager(
        providers = providers,
        onAdd = mapViewModel::addCustomTileProvider,
        onUpdate = mapViewModel::updateCustomTileProvider,
        onDelete = mapViewModel::removeCustomTileProvider,
        onAddLocalMbTiles = {
            mbtilesPickerLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                },
            )
        },
    )
}
