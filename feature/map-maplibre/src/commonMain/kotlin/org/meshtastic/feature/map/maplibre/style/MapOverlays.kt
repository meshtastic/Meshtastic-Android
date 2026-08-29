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
package org.meshtastic.feature.map.maplibre.style

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.feature.map.tiles.DemEncoding
import org.meshtastic.feature.map.tiles.MapTileCatalogue
import org.meshtastic.feature.map.tiles.RasterOverlaySource
import org.meshtastic.feature.map.tiles.RasterTileSpec

/**
 * Overlays that composite on top of whichever [Basemap] is selected. Unlike basemaps these are independently toggleable
 * and may be stacked.
 */
sealed interface MapOverlay {
    val id: String

    /**
     * Menu label. A resource, not a literal: unlike the basemaps — whose labels are provider and style names
     * (OpenStreetMap, Liberty, Esri) that stay the same in every language — these describe what the overlay shows, and
     * a phrase like weather radar is ordinary UI text that has to translate.
     */
    val label: StringResource

    /** Terrain shading derived from a raster-DEM source. */
    data class Hillshade(
        override val id: String,
        override val label: StringResource,
        val spec: RasterTileSpec,
        val encoding: DemEncoding,
    ) : MapOverlay

    /** A plain raster overlay, e.g. weather imagery. */
    data class Raster(override val id: String, override val label: StringResource, val spec: RasterTileSpec) :
        MapOverlay
}

/**
 * Overlay registry.
 *
 * Terrain shading is the one addition the OSMdroid map never had, and it earns its place here: LoRa range is
 * terrain-limited, so hillshade explains a failed link in a way a flat basemap cannot.
 */
object MapOverlays {

    /** The shared catalogue's overlays, as overlays this renderer can draw. */
    val all: List<MapOverlay> = MapTileCatalogue.overlays.map { it.toMapOverlay() }

    fun byId(id: String?): MapOverlay? = all.firstOrNull { it.id == id }

    /** The catalogue's key-gated precipitation overlay, as an overlay this renderer can draw. */
    fun openWeatherPrecipitation(apiKey: String): MapOverlay.Raster? =
        MapTileCatalogue.openWeatherPrecipitation(apiKey)?.toMapOverlay() as? MapOverlay.Raster
}

/**
 * A catalogue source as the overlay type this renderer composes.
 *
 * The split on [RasterOverlaySource.demEncoding] is the whole reason the two are separate types: a DEM source is drawn
 * as hillshading, which is a MapLibre capability, while an imagery source is a plain raster either engine can draw.
 */
private fun RasterOverlaySource.toMapOverlay(): MapOverlay = when (val encoding = demEncoding) {
    null -> MapOverlay.Raster(id = id, label = label, spec = spec)
    else -> MapOverlay.Hillshade(id = id, label = label, spec = spec, encoding = encoding)
}
