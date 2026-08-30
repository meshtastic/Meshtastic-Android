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
package org.meshtastic.feature.map.layers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MapPrefs

/**
 * The user's per-layer opacity, for every layer either engine can draw.
 *
 * Its own store rather than a field on [MapLayersManager]: built-in raster overlays are not imported layers and that
 * class knows nothing about them, but both need the same setting and both flavours read it. Keys are documented on
 * [MapPrefs.layerOpacity].
 */
@Single
class LayerOpacityStore(private val mapPrefs: MapPrefs, dispatchers: CoroutineDispatchers) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val opacity: StateFlow<Map<String, Float>> =
        mapPrefs.layerOpacity
            .map(::decodeLayerOpacity)
            .stateIn(scope, SharingStarted.Eagerly, decodeLayerOpacity(mapPrefs.layerOpacity.value))

    /** Sets one layer's opacity; [LAYER_OPACITY_OPAQUE] removes its entry rather than storing the default. */
    fun setOpacity(key: String, opacity: Float) {
        mapPrefs.updateLayerOpacity { stored ->
            encodeLayerOpacity(decodeLayerOpacity(stored) + (key to opacity.coerceIn(0f, LAYER_OPACITY_OPAQUE)))
        }
    }
}
