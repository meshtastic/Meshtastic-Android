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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.getFileName
import org.meshtastic.core.ui.util.showToast

@Composable
fun CustomTileProviderManagerSheet(mapViewModel: MapViewModel) {
    val providers by mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mbtilesPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    mapViewModel.addCustomTileProvider(
                        name = uri.getFileName(context).substringBeforeLast('.'),
                        urlTemplate = "",
                        localUri = uri.toString(),
                    )
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
