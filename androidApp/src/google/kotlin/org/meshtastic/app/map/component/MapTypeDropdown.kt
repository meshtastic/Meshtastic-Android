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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.maps.android.compose.MapType
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.manage_custom_tile_sources
import org.meshtastic.core.resources.map_type_hybrid
import org.meshtastic.core.resources.map_type_normal
import org.meshtastic.core.resources.map_type_satellite
import org.meshtastic.core.resources.map_type_terrain
import org.meshtastic.feature.map.component.BasemapChoice
import org.meshtastic.feature.map.component.BasemapMenu
import org.meshtastic.feature.map.tiles.MapTileCatalogue

/**
 * Ids for Google's own map types.
 *
 * Prefixed so they cannot collide with a catalogue source or with the random id a user's own source carries, which lets
 * one selected id stand for the whole menu.
 */
private const val GOOGLE_MAP_TYPE_PREFIX = "google:"

@Composable
internal fun MapTypeDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    mapViewModel: MapViewModel,
    onManageCustomTileProvidersClick: () -> Unit,
) {
    val customTileProviders by mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle()
    val selectedRasterBasemapId by mapViewModel.selectedRasterBasemapId.collectAsStateWithLifecycle()
    val selectedGoogleMapType by mapViewModel.selectedGoogleMapType.collectAsStateWithLifecycle()

    val googleMapTypes =
        listOf(
            BasemapChoice(MapType.NORMAL.toChoiceId(), stringResource(Res.string.map_type_normal)),
            BasemapChoice(MapType.SATELLITE.toChoiceId(), stringResource(Res.string.map_type_satellite)),
            BasemapChoice(MapType.TERRAIN.toChoiceId(), stringResource(Res.string.map_type_terrain)),
            BasemapChoice(MapType.HYBRID.toChoiceId(), stringResource(Res.string.map_type_hybrid)),
        )
    val catalogueBasemaps = MapTileCatalogue.basemaps.map { BasemapChoice(it.id, it.label) }
    val customBasemaps = customTileProviders.map { BasemapChoice(it.id, it.name) }

    BasemapMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        groups = listOf(googleMapTypes, catalogueBasemaps, customBasemaps),
        selectedId = selectedRasterBasemapId ?: selectedGoogleMapType.toChoiceId(),
        onSelect = { choice ->
            when {
                choice.id.startsWith(GOOGLE_MAP_TYPE_PREFIX) ->
                    mapViewModel.setSelectedGoogleMapType(choice.id.toGoogleMapType())

                catalogueBasemaps.any { it.id == choice.id } -> mapViewModel.selectCatalogueBasemap(choice.id)

                else -> mapViewModel.selectCustomTileProvider(customTileProviders.firstOrNull { it.id == choice.id })
            }
        },
        trailingContent = {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.manage_custom_tile_sources)) },
                onClick = {
                    onManageCustomTileProvidersClick()
                    onDismissRequest()
                },
            )
        },
    )
}

private fun MapType.toChoiceId(): String = GOOGLE_MAP_TYPE_PREFIX + name

private fun String.toGoogleMapType(): MapType =
    runCatching { MapType.valueOf(removePrefix(GOOGLE_MAP_TYPE_PREFIX)) }.getOrDefault(MapType.NORMAL)
