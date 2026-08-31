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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.drop
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

/** A preference value that has been read from disk; distinguishes "not read yet" from a persisted null. */
private data class Loaded<T>(val value: T)

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
 *
 * Returns null until both preferences have been read from disk once. [MapPrefs.mapStyle] and
 * [MapTileProviderPrefs.selectedCustomTileProviderId] are eager StateFlows that start at a hardcoded default (`0`,
 * `null`) and only take on the persisted value after their first disk read — so a map composed before that read
 * resolves opens on the default basemap and swaps to the persisted one moments later. That swap is not cosmetic: every
 * caller hands `current` to `MaplibreMap`'s `baseStyle`, and a changed `baseStyle` unloads the live style synchronously
 * (maplibre-compose 0.15.0, `MlnFfiMapSession.setBaseStyle`), so map content still attaching its sources to that style
 * throws "Source ... was not added: its style is no longer loaded" — an error dialog over a dead map. Fast hardware
 * masks the race, which is why development never saw it: the preference read settles in milliseconds, long before the
 * style's network fetch completes, so the swap lands while nothing is attached yet. On slow storage the read can land
 * exactly in the attach window, on every open. Waiting for the persisted values means the map only ever opens with one
 * style. (The initial values come from the awaited reads themselves — each live flow's replayed current value is
 * dropped, since it can still be the default — so the gate cannot open on a value the renderer would immediately swap
 * away from.)
 */
@Composable
internal fun rememberBasemapSelection(customs: List<Basemap.Raster>): BasemapSelection? {
    val mapPrefs: MapPrefs = koinInject()
    val tilePrefs: MapTileProviderPrefs = koinInject()
    val scope = rememberCoroutineScope()

    var styleIndex by remember { mutableStateOf<Loaded<Int>?>(null) }
    var selectedCustomId by remember { mutableStateOf<Loaded<String?>?>(null) }

    // Subscribe before reading, and drop each flow's replayed current value: it can predate the first disk read and
    // so may still be the eager default. The awaited reads supply the initial values — re-delivering anything a
    // dropped replay carried — and every later emission is a genuine update that overwrites them. The reads fill only
    // a still-null slot, so they never clobber a newer collected value. Plain collect rather than a lifecycle-aware
    // collector: drop(1) must drop only the subscription replay, and a lifecycle restart would re-drop a real value.
    LaunchedEffect(mapPrefs, tilePrefs) {
        launch { mapPrefs.mapStyle.drop(1).collect { styleIndex = Loaded(it) } }
        launch { tilePrefs.selectedCustomTileProviderId.drop(1).collect { selectedCustomId = Loaded(it) } }
        val style = Loaded(mapPrefs.awaitMapStyle())
        val custom = Loaded(tilePrefs.awaitSelectedCustomTileProviderId())
        if (styleIndex == null) styleIndex = style
        if (selectedCustomId == null) selectedCustomId = custom
    }

    val loadedStyle = styleIndex
    val loadedCustom = selectedCustomId
    if (loadedStyle == null || loadedCustom == null) return null

    val current =
        customs.firstOrNull { it.id == loadedCustom.value }
            ?: Basemaps.all.getOrElse(loadedStyle.value) { Basemaps.default }

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
