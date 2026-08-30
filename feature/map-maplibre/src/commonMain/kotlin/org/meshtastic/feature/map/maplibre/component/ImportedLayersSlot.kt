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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import org.meshtastic.feature.map.component.CustomMapLayersSheet
import org.meshtastic.feature.map.layers.LayerOpacityStore
import org.meshtastic.feature.map.layers.MapLayersManager
import org.meshtastic.feature.map.layers.rememberMapLayerPicker

/**
 * The imported-layers manager, wired to the shared store and the platform's own file picker.
 *
 * One composable for every MapLibre host — the F-Droid map and the desktop map both mount this in their layers sheet,
 * which is what makes "import a KML on desktop" the same feature rather than a port of one.
 */
@Composable
fun ImportedLayersSlot() {
    val manager: MapLayersManager = koinInject()
    val opacityStore: LayerOpacityStore = koinInject()
    val layers by manager.mapLayers.collectAsState()
    val opacity by opacityStore.opacity.collectAsState()
    val picker = rememberMapLayerPicker(onPick = manager::addMapLayer)

    CustomMapLayersSheet(
        mapLayers = layers,
        onToggleVisibility = manager::toggleLayerVisibility,
        onRemoveLayer = manager::removeMapLayer,
        onAddLayerClicked = picker::pick,
        onRefreshLayer = manager::refreshMapLayer,
        onAddNetworkLayer = manager::addNetworkMapLayer,
        opacity = opacity,
        onOpacityChange = opacityStore::setOpacity,
    )
}
