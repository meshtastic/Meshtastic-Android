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

package com.geeksville.mesh.ui.map.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.map.MapViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapControlsOverlay(
    modifier: Modifier = Modifier,
    mapFilterMenuExpanded: Boolean,
    onMapFilterMenuDismissRequest: () -> Unit,
    onToggleMapFilterMenu: () -> Unit,
    mapViewModel: MapViewModel, // For MapFilterDropdown and MapTypeDropdown
    mapTypeMenuExpanded: Boolean,
    onMapTypeMenuDismissRequest: () -> Unit,
    onToggleMapTypeMenu: () -> Unit,
    onManageLayersClicked: () -> Unit,
    onManageCustomTileProvidersClicked: () -> Unit, // New parameter
    showFilterButton: Boolean,
    scrollBehavior: FloatingToolbarScrollBehavior,
) {
    VerticalFloatingToolbar(
        modifier = modifier,
        expanded = true,
        leadingContent = { },
        trailingContent = { },
        scrollBehavior = scrollBehavior,
        content = {
            if (showFilterButton) {
                Box {
                    MapButton(
                        icon = Icons.Outlined.Tune,
                        contentDescription = stringResource(id = R.string.map_filter),
                        onClick = onToggleMapFilterMenu
                    )
                    MapFilterDropdown(
                        expanded = mapFilterMenuExpanded,
                        onDismissRequest = onMapFilterMenuDismissRequest,
                        mapViewModel = mapViewModel,
                    )
                }
            }

            Box {
                MapButton(
                    icon = Icons.Outlined.Map,
                    contentDescription = stringResource(id = R.string.map_tile_source),
                    onClick = onToggleMapTypeMenu
                )
                MapTypeDropdown(
                    expanded = mapTypeMenuExpanded,
                    onDismissRequest = onMapTypeMenuDismissRequest,
                    mapViewModel = mapViewModel, // Pass mapViewModel
                    onManageCustomTileProvidersClicked = onManageCustomTileProvidersClicked // Pass new callback
                )
            }

            MapButton(
                icon = Icons.Outlined.Layers,
                contentDescription = stringResource(id = R.string.manage_map_layers),
                onClick = onManageLayersClicked
            )
        }

    )
}
