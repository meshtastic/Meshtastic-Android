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
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_overlay_hillshade
import org.meshtastic.core.resources.map_overlay_precipitation
import org.meshtastic.core.resources.map_overlay_weather_radar

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

    /** Elevation encodings we support. Kept local so the registry stays free of MapLibre types. */
    enum class DemEncoding {
        /** Mapbox Terrain-RGB. */
        MAPBOX,

        /** Terrarium PNG, the encoding every keyless public DEM source below uses. */
        TERRARIUM,
    }
}

/**
 * Overlay registry.
 *
 * Terrain shading is the one addition the OSMdroid map never had, and it earns its place here: LoRa range is
 * terrain-limited, so hillshade explains a failed link in a way a flat basemap cannot.
 */
object MapOverlays {

    /**
     * AWS Open Data terrain tiles (the former Mazen/Tilezen set) — free, keyless, global.
     *
     * The encoding MUST stay [MapOverlay.DemEncoding.TERRARIUM]: MapLibre defaults raster-DEM sources to Mapbox
     * Terrain-RGB, and feeding Terrarium PNGs to that decoder yields silently wrong elevations — plausible-looking
     * shading, no error anywhere.
     */
    val Hillshade =
        MapOverlay.Hillshade(
            id = "hillshade",
            label = Res.string.map_overlay_hillshade,
            spec =
            RasterTileSpec(
                tiles = listOf("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"),
                maxZoom = 15,
                attributionHtml = "&copy; AWS Open Data Terrain Tiles",
            ),
            encoding = MapOverlay.DemEncoding.TERRARIUM,
        )

    /**
     * NEXRAD base reflectivity, quality-controlled, from NCEP's own GeoServer.
     *
     * Not nowCOAST, which is where the OSMdroid map pointed and where this pointed until a tester reported the layer
     * drawing nothing: `new.nowcoast.noaa.gov` no longer resolves — NXDOMAIN, not a 404 — so the overlay had been dead
     * on the OSMdroid map too and the swap carried the URL over faithfully. Verified against the live service rather
     * than swapped on faith: a CONUS-wide `GetMap` returns a populated RGBA PNG.
     *
     * WMS is not an XYZ scheme, so this leans on MapLibre's `{bbox-epsg-3857}` placeholder, which expands to the tile's
     * projected bounds. Kept at 1.1.0 with `SRS` (not `CRS`), which is the pairing GeoServer accepts for that version.
     *
     * The id is unchanged, so a user who had this overlay switched on keeps it switched on across the fix.
     */
    val NoaaRadar =
        MapOverlay.Raster(
            id = "noaa-radar",
            label = Res.string.map_overlay_weather_radar,
            spec =
            RasterTileSpec(
                tiles =
                listOf(
                    "https://opengeo.ncep.noaa.gov/geoserver/conus/conus_bref_qcd/ows" +
                        "?SERVICE=WMS&VERSION=1.1.0&REQUEST=GetMap&LAYERS=conus_bref_qcd&STYLES=" +
                        "&FORMAT=image/png&TRANSPARENT=true&SRS=EPSG:3857" +
                        "&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256",
                ),
                maxZoom = 12,
                attributionHtml = "NOAA/NCEP",
            ),
        )

    /** Every overlay that needs no credentials, in menu order. */
    val all: List<MapOverlay> = listOf(Hillshade, NoaaRadar)

    fun byId(id: String?): MapOverlay? = all.firstOrNull { it.id == id }

    /**
     * OpenWeatherMap precipitation. Carried over from the OSMdroid map, where it was wired up with an empty appid and
     * so never actually rendered; it stays behind a caller-supplied key rather than shipping another broken entry in
     * the layer menu.
     */
    fun openWeatherPrecipitation(apiKey: String): MapOverlay.Raster? = apiKey
        .takeIf { it.isNotBlank() }
        ?.let { key ->
            MapOverlay.Raster(
                id = "owm-precipitation",
                label = Res.string.map_overlay_precipitation,
                spec =
                RasterTileSpec(
                    tiles =
                    listOf(
                        "https://tile.openweathermap.org/map/precipitation_new/{z}/{x}/{y}.png?appid=$key",
                    ),
                    maxZoom = 19,
                    attributionHtml = "&copy; OpenWeather",
                ),
            )
        }
}
