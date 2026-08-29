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
package org.meshtastic.feature.map.maplibre.layers

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.HillshadeLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.RasterDemEncoding
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberRasterDemSource
import org.maplibre.compose.sources.rememberRasterSource
import org.meshtastic.feature.map.layers.opacityOf
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.MapOverlay
import org.meshtastic.feature.map.tiles.DemEncoding
import org.meshtastic.feature.map.tiles.RasterTileSpec

internal fun RasterTileSpec.toTileSetOptions(): TileSetOptions =
    TileSetOptions(minZoom = minZoom, maxZoom = maxZoom, attributionHtml = attributionHtml)

/**
 * Renders a raster basemap.
 *
 * Vector basemaps are handed to MapLibre as a style URL and need nothing here; raster ones are drawn as an ordinary
 * layer over the empty style, which keeps both kinds interchangeable from the caller's point of view.
 */
@Composable
internal fun RasterBasemapLayer(basemap: Basemap.Raster) {
    val source = rememberRasterSource(tiles = basemap.spec.tiles, options = basemap.spec.toTileSetOptions())
    RasterLayer(id = "basemap-${basemap.id}", source = source)
}

/**
 * Renders the toggled overlays, in registry order, above the basemap and below the mesh data.
 *
 * @param opacity per-overlay opacity, keyed by [MapOverlay.id]. See
 *   [org.meshtastic.core.repository.MapPrefs.layerOpacity].
 */
@Composable
internal fun MapOverlayLayers(overlays: List<MapOverlay>, opacity: Map<String, Float>) {
    overlays.forEach { overlay ->
        val layerOpacity = opacity.opacityOf(overlay.id)
        when (overlay) {
            is MapOverlay.Hillshade -> HillshadeOverlayLayer(overlay, layerOpacity)

            is MapOverlay.Raster -> {
                val source = rememberRasterSource(tiles = overlay.spec.tiles, options = overlay.spec.toTileSetOptions())
                RasterLayer(id = "overlay-${overlay.id}", source = source, opacity = const(layerOpacity))
            }
        }
    }
}

/**
 * Terrain shading, faded through its own colours.
 *
 * The style spec gives hillshade no opacity property — those colours *are* what gets composited, so scaling their alpha
 * is the fade. The three values restated here are the spec's own defaults; naming them is the only way to reach their
 * alpha.
 */
@Composable
private fun HillshadeOverlayLayer(overlay: MapOverlay.Hillshade, opacity: Float) {
    val source =
        rememberRasterDemSource(
            tiles = overlay.spec.tiles,
            options = overlay.spec.toTileSetOptions(),
            // Not the default. MapLibre assumes Mapbox Terrain-RGB; every keyless public DEM is
            // Terrarium, and the mismatch is silent — shading looks plausible but is wrong.
            encoding =
            when (overlay.encoding) {
                DemEncoding.TERRARIUM -> RasterDemEncoding.Terrarium
                DemEncoding.MAPBOX -> RasterDemEncoding.Mapbox
            },
        )
    HillshadeLayer(
        id = "overlay-${overlay.id}",
        source = source,
        shadowColor = const(Color.Black.copy(alpha = opacity)),
        highlightColor = const(Color.White.copy(alpha = opacity)),
        accentColor = const(Color.Black.copy(alpha = opacity)),
    )
}
