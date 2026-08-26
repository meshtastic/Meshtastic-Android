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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.getFileName

/**
 * The imported GeoJSON/KML layer manager, for the foot of the MapLibre map's layers sheet.
 *
 * The same manager the Google flavor reaches through its own layers button. It lives in the flavor rather than the
 * shared map module because adding a layer opens a document picker, which is Android-only — the shared module is
 * compiled for desktop too.
 */
@Composable
fun ImportedLayersSlot() {
    val viewModel: MapViewModel = koinViewModel()
    val context = LocalContext.current
    val layers by viewModel.mapLayers.collectAsStateWithLifecycle()

    val filePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> viewModel.addMapLayer(uri, uri.getFileName(context)) }
            }
        }

    CustomMapLayersSheet(
        mapLayers = layers,
        onToggleVisibility = viewModel::toggleLayerVisibility,
        onRemoveLayer = viewModel::removeMapLayer,
        onAddLayerClicked = {
            // Providers hand back generic MIME types for perfectly good map files, so the extension is validated
            // after selection rather than filtered here.
            filePicker.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                },
            )
        },
        onRefreshLayer = viewModel::refreshMapLayer,
        onAddNetworkLayer = viewModel::addNetworkMapLayer,
    )
}
