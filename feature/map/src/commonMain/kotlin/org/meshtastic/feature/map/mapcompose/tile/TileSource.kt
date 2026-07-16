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
package org.meshtastic.feature.map.mapcompose.tile

/**
 * Axis order of a slippy-map URL template. OSM-style servers order tiles `{z}/{x}/{y}` (column before row); ArcGIS
 * REST tile servers (ESRI, USGS) order them `{z}/{y}/{x}` (row before column). MapCompose hands us `(row, col)`, so
 * every URL goes through [TileSource.tileUrl], the single place this distinction is interpreted.
 */
enum class TileUrlScheme {
    ZXY,
    ZYX,
}

/**
 * A raster XYZ tile source. [id] is the stable persistence key (stored in `MapPrefs.selectedTileSourceId`) — never
 * rename an id without a pref migration.
 */
data class TileSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val scheme: TileUrlScheme,
    val imageExtension: String,
    val minZoom: Int,
    val maxZoom: Int,
    val attribution: String,
) {
    fun tileUrl(zoom: Int, row: Int, col: Int): String = when (scheme) {
        TileUrlScheme.ZXY -> "$baseUrl$zoom/$col/$row$imageExtension"
        TileUrlScheme.ZYX -> "$baseUrl$zoom/$row/$col$imageExtension"
    }
}

/**
 * The keyless raster sources available to the shared MapCompose renderer, ported from the osmdroid catalog in the
 * fdroid flavor (`CustomTileSource.kt`). Keyed sources (OpenWeatherMap radar) and WMS are deliberately absent — see
 * the workpad's deferred list.
 */
object TileSourceCatalog {

    val OSM_MAPNIK =
        TileSource(
            id = "osm_mapnik",
            name = "OpenStreetMap",
            baseUrl = "https://tile.openstreetmap.org/",
            scheme = TileUrlScheme.ZXY,
            imageExtension = ".png",
            minZoom = 0,
            maxZoom = 19,
            attribution = "© OpenStreetMap contributors",
        )

    val USGS_TOPO =
        TileSource(
            id = "usgs_topo",
            name = "USGS TOPO",
            baseUrl = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/",
            scheme = TileUrlScheme.ZYX,
            imageExtension = "",
            minZoom = 0,
            maxZoom = 16,
            attribution = "USGS The National Map",
        )

    val OPEN_TOPO =
        TileSource(
            id = "open_topo",
            name = "Open TOPO",
            baseUrl = "https://tile.opentopomap.org/",
            scheme = TileUrlScheme.ZXY,
            imageExtension = ".png",
            minZoom = 0,
            maxZoom = 17,
            attribution = "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
        )

    val ESRI_WORLD_TOPO =
        TileSource(
            id = "esri_world_topo",
            name = "ESRI World TOPO",
            baseUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/",
            scheme = TileUrlScheme.ZYX,
            imageExtension = ".jpg",
            minZoom = 0,
            maxZoom = 20,
            attribution = "Esri, HERE, Garmin, FAO, NOAA, USGS, © OpenStreetMap contributors, and the GIS User Community",
        )

    val USGS_SATELLITE =
        TileSource(
            id = "usgs_satellite",
            name = "USGS Satellite",
            baseUrl = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/",
            scheme = TileUrlScheme.ZYX,
            imageExtension = "",
            minZoom = 0,
            maxZoom = 16,
            attribution = "USGS The National Map",
        )

    val ESRI_IMAGERY =
        TileSource(
            id = "esri_imagery",
            name = "ESRI World Overview",
            baseUrl = "https://clarity.maptiles.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/",
            scheme = TileUrlScheme.ZYX,
            imageExtension = ".jpg",
            minZoom = 0,
            maxZoom = 20,
            attribution = "Esri, Maxar, Earthstar Geographics, and the GIS User Community",
        )

    val ALL: List<TileSource> = listOf(OSM_MAPNIK, USGS_TOPO, OPEN_TOPO, ESRI_WORLD_TOPO, USGS_SATELLITE, ESRI_IMAGERY)

    val DEFAULT: TileSource = OSM_MAPNIK

    fun byId(id: String?): TileSource = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
