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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.app.map.repository.CustomTileProviderRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.manage_custom_tile_sources

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
    val providers by repository.getCustomTileProviders().collectAsStateWithLifecycle(emptyList())

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
            )
        }
    }
}
