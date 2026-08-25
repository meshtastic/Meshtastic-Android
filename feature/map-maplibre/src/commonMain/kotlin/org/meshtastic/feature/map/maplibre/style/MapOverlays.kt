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

/**
 * Overlays that composite on top of whichever [Basemap] is selected. Unlike basemaps these are
 * independently toggleable and may be stacked.
 */
sealed interface MapOverlay {
    val id: String

    /** Terrain shading derived from a raster-DEM source. */
    data class Hillshade(override val id: String, val spec: RasterTileSpec, val encoding: DemEncoding) : MapOverlay

    /** A plain raster overlay, e.g. weather imagery. */
    data class Raster(override val id: String, val spec: RasterTileSpec) : MapOverlay

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
 * Terrain shading is the one addition the OSMdroid map never had, and it earns its place here: LoRa
 * range is terrain-limited, so hillshade explains a failed link in a way a flat basemap cannot.
 */
object MapOverlays {

    /**
     * AWS Open Data terrain tiles (the former Mazen/Tilezen set) — free, keyless, global.
     *
     * The encoding MUST stay [MapOverlay.DemEncoding.TERRARIUM]: MapLibre defaults raster-DEM
     * sources to Mapbox Terrain-RGB, and feeding Terrarium PNGs to that decoder yields silently
     * wrong elevations — plausible-looking shading, no error anywhere.
     */
    val Hillshade =
        MapOverlay.Hillshade(
            id = "hillshade",
            spec =
            RasterTileSpec(
                tiles = listOf("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"),
                maxZoom = 15,
                attributionHtml = "&copy; AWS Open Data Terrain Tiles",
            ),
            encoding = MapOverlay.DemEncoding.TERRARIUM,
        )

    /**
     * NOAA nowCOAST NEXRAD reflectivity, carried over from the OSMdroid map.
     *
     * WMS is not an XYZ scheme, so this leans on MapLibre's `{bbox-epsg-3857}` placeholder, which
     * expands to the tile's projected bounds. Kept at 1.1.0 with `SRS` (not `CRS`) to match the
     * request the OSMdroid source was making.
     */
    val NoaaRadar =
        MapOverlay.Raster(
            id = "noaa-radar",
            spec =
            RasterTileSpec(
                tiles =
                listOf(
                    "https://new.nowcoast.noaa.gov/arcgis/services/nowcoast/" +
                        "radar_meteo_imagery_nexrad_time/MapServer/WmsServer" +
                        "?SERVICE=WMS&VERSION=1.1.0&REQUEST=GetMap&LAYERS=1&STYLES=" +
                        "&FORMAT=image/png&TRANSPARENT=true&SRS=EPSG:3857" +
                        "&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256",
                ),
                maxZoom = 12,
                attributionHtml = "NOAA nowCOAST",
            ),
        )

    /** Every overlay that needs no credentials, in menu order. */
    val all: List<MapOverlay> = listOf(Hillshade, NoaaRadar)

    fun byId(id: String?): MapOverlay? = all.firstOrNull { it.id == id }

    /**
     * OpenWeatherMap precipitation. Carried over from the OSMdroid map, where it was wired up with
     * an empty appid and so never actually rendered; it stays behind a caller-supplied key rather
     * than shipping another broken entry in the layer menu.
     */
    fun openWeatherPrecipitation(apiKey: String): MapOverlay.Raster? = apiKey.takeIf { it.isNotBlank() }?.let { key ->
        MapOverlay.Raster(
            id = "owm-precipitation",
            spec =
            RasterTileSpec(
                tiles = listOf("https://tile.openweathermap.org/map/precipitation_new/{z}/{x}/{y}.png?appid=$key"),
                maxZoom = 19,
                attributionHtml = "&copy; OpenWeather",
            ),
        )
    }
}
