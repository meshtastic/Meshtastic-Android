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

import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_overlay_hillshade
import org.meshtastic.core.resources.map_overlay_precipitation
import org.meshtastic.core.resources.map_overlay_weather_radar

/**
 * The tile sources the app offers, independent of which engine draws them.
 *
 * These are URL templates, zoom ranges and attribution strings — data, not rendering — but they lived in the MapLibre
 * module, which the Google flavour cannot depend on. So the Google map offered four Google map types and nothing else,
 * and a user who wanted USGS or Esri there had to type the tile URL in by hand as a custom source.
 */
object MapTileCatalogue {

    val OpenStreetMap =
        RasterBasemapSource(
            id = "osm",
            label = "OpenStreetMap",
            spec =
            RasterTileSpec(
                tiles = listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
                maxZoom = 19,
                attributionHtml = "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>",
            ),
        )

    val OpenTopo =
        RasterBasemapSource(
            id = "opentopo",
            label = "OpenTopoMap",
            spec =
            RasterTileSpec(
                tiles = listOf("https://tile.opentopomap.org/{z}/{x}/{y}.png"),
                maxZoom = 17,
                attributionHtml = "&copy; OpenTopoMap (CC-BY-SA)",
            ),
        )

    val UsgsTopo =
        RasterBasemapSource(
            id = "usgs-topo",
            label = "USGS Topo",
            spec =
            RasterTileSpec(
                tiles =
                listOf(
                    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}",
                ),
                maxZoom = 16,
                attributionHtml = "USGS The National Map",
            ),
        )

    val UsgsSatellite =
        RasterBasemapSource(
            id = "usgs-sat",
            label = "USGS Imagery",
            spec =
            RasterTileSpec(
                tiles =
                listOf(
                    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}",
                ),
                maxZoom = 16,
                attributionHtml = "USGS The National Map",
            ),
        )

    val EsriTopo =
        RasterBasemapSource(
            id = "esri-topo",
            label = "Esri Topo",
            spec =
            RasterTileSpec(
                tiles =
                listOf(
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map" +
                        "/MapServer/tile/{z}/{y}/{x}.jpg",
                ),
                maxZoom = 20,
                attributionHtml =
                "Esri, HERE, Garmin, FAO, NOAA, USGS, &copy; OpenStreetMap contributors, " +
                    "and the GIS User Community",
            ),
        )

    val EsriImagery =
        RasterBasemapSource(
            id = "esri-imagery",
            label = "Esri Imagery",
            spec =
            RasterTileSpec(
                tiles =
                listOf(
                    "https://clarity.maptiles.arcgis.com/arcgis/rest/services/World_Imagery" +
                        "/MapServer/tile/{z}/{y}/{x}.jpg",
                ),
                maxZoom = 20,
                attributionHtml = "Esri, Maxar, Earthstar Geographics, and the GIS User Community",
            ),
        )

    /** Every raster basemap, in menu order. */
    val basemaps: List<RasterBasemapSource> =
        listOf(OpenStreetMap, OpenTopo, UsgsTopo, UsgsSatellite, EsriTopo, EsriImagery)

    val Hillshade =
        RasterOverlaySource(
            id = "hillshade",
            label = Res.string.map_overlay_hillshade,
            spec =
            RasterTileSpec(
                tiles = listOf("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"),
                maxZoom = 15,
                attributionHtml = "&copy; AWS Open Data Terrain Tiles",
            ),
            // Not the default. MapLibre assumes Mapbox Terrain-RGB; every keyless public DEM is Terrarium, and the
            // mismatch is silent — shading looks plausible but is wrong.
            demEncoding = DemEncoding.TERRARIUM,
        )

    /**
     * NEXRAD base reflectivity, quality-controlled, from NCEP's own GeoServer.
     *
     * WMS rather than XYZ, so the tile URL carries a `{bbox-epsg-3857}` placeholder the renderer expands to the tile's
     * projected bounds.
     */
    val NoaaRadar =
        RasterOverlaySource(
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
    val overlays: List<RasterOverlaySource> = listOf(Hillshade, NoaaRadar)

    /**
     * OpenWeatherMap precipitation, which needs a key the user supplies.
     *
     * Carried over from the OSMdroid map, where it was wired up with an empty appid and so never rendered; it stays
     * behind a caller-supplied key rather than shipping another broken entry in the layer menu.
     */
    fun openWeatherPrecipitation(apiKey: String): RasterOverlaySource? = apiKey
        .takeIf { it.isNotBlank() }
        ?.let { key ->
            RasterOverlaySource(
                id = "owm-precipitation",
                label = Res.string.map_overlay_precipitation,
                spec =
                RasterTileSpec(
                    tiles =
                    listOf(
                        "https://tile.openweathermap.org/map/precipitation_new/{z}/{x}/{y}.png?appid=$key",
                    ),
                    maxZoom = 19,
                    attributionHtml = "&copy; OpenWeatherMap",
                ),
            )
        }
}
