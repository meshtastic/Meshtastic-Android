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
package org.meshtastic.feature.map.maplibre

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.spatialk.geojson.BoundingBox
import org.meshtastic.feature.map.component.MapEngineUnavailable
import org.meshtastic.feature.map.maplibre.component.BasemapSelection
import org.meshtastic.feature.map.maplibre.component.MapZoom
import org.meshtastic.feature.map.maplibre.component.SecondaryMapControls
import org.meshtastic.feature.map.maplibre.layers.RasterBasemapLayer
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.toBaseStyle
import org.meshtastic.feature.map.maplibre.style.zoomRange

/**
 * The map surface the maps outside the main one all draw on.
 *
 * They differ in what they draw and which controls they carry, but not in how the map itself is set up: the chosen
 * basemap supplies both the style and the zoom range it can actually serve, and a raster basemap needs a layer of its
 * own over the empty style. Repeating that in four places is how three of them ended up with slightly different
 * versions of it.
 *
 * [content] is a plain `@Composable`, matching the layer composables it will hold — `NodeLayers` and
 * `RasterBasemapLayer` are declared that way and compose inside `MaplibreMap` without the applier annotation. Spelling
 * `@MaplibreComposable` here is what spotless and detekt disagree about formatting.
 *
 * The raster underlay is composed **before** [content] and must stay that way: layers stack in composition order, so a
 * basemap added afterwards paints over the mesh data instead of sitting under it.
 */
@Composable
internal fun SecondaryMapSurface(
    basemaps: BasemapSelection,
    cameraState: CameraState,
    modifier: Modifier = Modifier.fillMaxSize(),
    options: MapOptions = SecondaryMapOptions,
    content: @Composable () -> Unit,
) {
    // Same guard as MeshMap: the secondary maps compose MaplibreMap directly, so they crash the same way.
    if (!isMapLibreRuntimeAvailable()) return MapEngineUnavailable(modifier)

    MaplibreMap(
        baseStyle = basemaps.current.toBaseStyle(),
        cameraState = cameraState,
        modifier = modifier,
        options = options,
        zoomRange = basemaps.current.zoomRange(),
    ) {
        (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }
        content()
    }
}

/**
 * Zoom in the lower trailing corner and the toolbar along the top, which is every secondary map's chrome.
 *
 * [filterMenu] is passed only by the maps that have something to filter; see [SecondaryMapControls]. The node-detail
 * mini-map is the one map that does not use this — it is a thumbnail with no room for a toolbar, and carries only the
 * compact zoom pair.
 */
@Composable
internal fun BoxScope.SecondaryMapChrome(
    cameraState: CameraState,
    basemaps: BasemapSelection,
    filterMenu: (@Composable (expanded: Boolean, onDismissRequest: () -> Unit) -> Unit)? = null,
) {
    MapZoom(cameraState = cameraState, basemap = basemaps.current)
    SecondaryMapControls(
        cameraState = cameraState,
        basemaps = basemaps,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
        filterMenu = filterMenu,
    )
}

/**
 * Frames [bounds] once the map can report a viewport.
 *
 * Fitting a bounding box needs a viewport size, and on first composition there is none — the fit silently lands on a
 * default instead, which is what made these maps open zoomed past the very thing they exist to show. Null bounds is a
 * no-op, which covers having nothing to frame yet.
 *
 * [key] is what re-frames the camera, and it is deliberately the caller's choice rather than the bounds themselves:
 * each map has its own answer for when the user would want the camera moved under them.
 */
@Composable
internal fun FitBoundsOnceVisible(
    cameraState: CameraState,
    key: Any?,
    padding: PaddingValues = SecondaryMapFitPadding,
    bounds: () -> BoundingBox?,
) {
    val hasViewport = cameraState.viewport != null
    // The effect restarts on [key], not on `bounds`, so it must read the current lambda rather than the one captured
    // when it last restarted — otherwise a recomposition that passes new bounds keeps framing the old ones.
    val currentBounds by rememberUpdatedState(bounds)
    LaunchedEffect(key, hasViewport) {
        if (hasViewport) {
            currentBounds()?.let { cameraState.jumpTo(boundingBox = it, padding = padding) }
        }
    }
}
