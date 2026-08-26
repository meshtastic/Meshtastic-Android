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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.MapTileProviderPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_tile_source
import org.meshtastic.core.resources.selected_map_type
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.Basemaps

/** The basemap in use, everything selectable, and how to change it. */
@Stable
internal class BasemapSelection(
    val current: Basemap,
    val builtIns: List<Basemap>,
    val customs: List<Basemap>,
    val onSelect: (Basemap) -> Unit,
)

/**
 * Resolves the active basemap from the built-in list plus any [customs] the host supplies.
 *
 * Two preferences back this, matching how the Google flavor stores it: an index into the built-in list, and a separate
 * id for a user-defined source. Keeping them apart means a custom source being added or removed cannot silently repoint
 * the built-in selection, which an index over a mixed list would.
 */
@Composable
internal fun rememberBasemapSelection(customs: List<Basemap.Raster>): BasemapSelection {
    val mapPrefs: MapPrefs = koinInject()
    val tilePrefs: MapTileProviderPrefs = koinInject()
    val scope = rememberCoroutineScope()

    val styleIndex by mapPrefs.mapStyle.collectAsStateWithLifecycle()
    val selectedCustomId by tilePrefs.selectedCustomTileProviderId.collectAsStateWithLifecycle()

    val current =
        customs.firstOrNull { it.id == selectedCustomId } ?: Basemaps.all.getOrElse(styleIndex) { Basemaps.default }

    return BasemapSelection(
        current = current,
        builtIns = Basemaps.all,
        customs = customs,
        onSelect = { chosen ->
            scope.launch {
                if (customs.any { it.id == chosen.id }) {
                    tilePrefs.setSelectedCustomTileProviderId(chosen.id)
                } else {
                    tilePrefs.setSelectedCustomTileProviderId(null)
                    mapPrefs.setMapStyle(Basemaps.all.indexOf(chosen))
                }
            }
        },
    )
}

@Composable
internal fun BasemapMenu(selection: BasemapSelection, extra: @Composable () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MapButton(
            icon = MeshtasticIcons.Map,
            contentDescription = stringResource(Res.string.map_tile_source),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                selection.builtIns.forEach { entry ->
                    BasemapItem(entry, entry.id == selection.current.id) {
                        selection.onSelect(entry)
                        expanded = false
                    }
                }
            }
            // Second group, as the Google flavor does: the user's own sources read as a separate list.
            if (selection.customs.isNotEmpty()) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                    selection.customs.forEach { entry ->
                        BasemapItem(entry, entry.id == selection.current.id) {
                            selection.onSelect(entry)
                            expanded = false
                        }
                    }
                }
            }
            extra()
        }
    }
}

@Composable
private fun BasemapItem(basemap: Basemap, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = basemap.label) },
        onClick = onClick,
        trailingIcon =
        if (selected) {
            { Icon(MeshtasticIcons.Check, contentDescription = stringResource(Res.string.selected_map_type)) }
        } else {
            null
        },
    )
}
