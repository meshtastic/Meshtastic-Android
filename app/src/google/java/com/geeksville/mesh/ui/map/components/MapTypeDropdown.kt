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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.map.MapViewModel
import com.google.maps.android.compose.MapType

@Suppress("LongMethod")
@Composable
internal fun MapTypeDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    mapViewModel: MapViewModel,
    onManageCustomTileProvidersClicked: () -> Unit,
) {
    val customTileProviders by mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle()
    val selectedCustomUrl by mapViewModel.selectedCustomTileProviderUrl.collectAsStateWithLifecycle()
    val selectedGoogleMapType by mapViewModel.selectedGoogleMapType.collectAsStateWithLifecycle()

    val googleMapTypes =
        listOf(
            stringResource(id = R.string.map_type_normal) to MapType.NORMAL,
            stringResource(id = R.string.map_type_satellite) to MapType.SATELLITE,
            stringResource(id = R.string.map_type_terrain) to MapType.TERRAIN,
            stringResource(id = R.string.map_type_hybrid) to MapType.HYBRID,
        )

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        googleMapTypes.forEach { (name, type) ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = {
                    mapViewModel.setSelectedGoogleMapType(type)
                    onDismissRequest() // Close menu
                },
                trailingIcon =
                if (selectedCustomUrl == null && selectedGoogleMapType == type) {
                    { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.selected_map_type)) }
                } else {
                    null
                },
            )
        }

        if (customTileProviders.isNotEmpty()) {
            HorizontalDivider()
            customTileProviders.forEach { config ->
                DropdownMenuItem(
                    text = { Text(config.name) },
                    onClick = {
                        mapViewModel.selectCustomTileProvider(config)
                        onDismissRequest() // Close menu
                    },
                    trailingIcon =
                    if (selectedCustomUrl == config.urlTemplate) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.selected_map_type),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.manage_custom_tile_sources)) },
            onClick = {
                onManageCustomTileProvidersClicked()
                onDismissRequest()
            },
        )
    }
}
