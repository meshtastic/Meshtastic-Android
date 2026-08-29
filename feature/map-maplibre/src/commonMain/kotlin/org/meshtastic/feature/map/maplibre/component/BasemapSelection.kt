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
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.map.component.BasemapChoice
import org.meshtastic.feature.map.component.BasemapMenu
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

/**
 * The basemap button and the menu it opens.
 *
 * The menu itself is [org.meshtastic.feature.map.component.BasemapMenu], shared with the Google map; this adds the
 * toolbar button and maps [BasemapSelection] onto the choices it renders.
 */
@Composable
internal fun BasemapButton(selection: BasemapSelection, extra: @Composable () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MapButton(
            icon = MeshtasticIcons.Map,
            contentDescription = stringResource(Res.string.map_tile_source),
            onClick = { expanded = true },
        )
        BasemapMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // The user's own sources read as a separate list from the ones we ship.
            groups = listOf(selection.builtIns.toChoices(), selection.customs.toChoices()),
            selectedId = selection.current.id,
            onSelect = { choice -> selection.byId(choice.id)?.let(selection.onSelect) },
            trailingContent = extra,
        )
    }
}

private fun List<Basemap>.toChoices(): List<BasemapChoice> = map { BasemapChoice(id = it.id, label = it.label) }

private fun BasemapSelection.byId(id: String): Basemap? =
    builtIns.firstOrNull { it.id == id } ?: customs.firstOrNull { it.id == id }
