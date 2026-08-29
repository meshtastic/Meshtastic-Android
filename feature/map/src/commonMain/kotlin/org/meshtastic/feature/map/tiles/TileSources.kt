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
package org.meshtastic.feature.map.tiles

import org.jetbrains.compose.resources.StringResource

/**
 * A raster basemap: XYZ tiles that stand in for the base map itself.
 *
 * The label is a provider or product name — OpenStreetMap, USGS Topo, Esri Imagery — so it is deliberately not
 * translated. Compare [RasterOverlaySource], whose labels describe what the layer shows and therefore are.
 */
data class RasterBasemapSource(val id: String, val label: String, val spec: RasterTileSpec)

/** A raster layer composited over whichever basemap is selected. */
data class RasterOverlaySource(
    val id: String,
    val label: StringResource,
    val spec: RasterTileSpec,
    /** Set when the tiles encode elevation rather than imagery, which only a renderer with hillshading can use. */
    val demEncoding: DemEncoding? = null,
)

/** How a raster-DEM source packs elevation into its pixels. */
enum class DemEncoding {
    /** Mapbox Terrain-RGB. */
    MAPBOX,

    /** Terrarium PNG, which every keyless public DEM source here uses. */
    TERRARIUM,
}
