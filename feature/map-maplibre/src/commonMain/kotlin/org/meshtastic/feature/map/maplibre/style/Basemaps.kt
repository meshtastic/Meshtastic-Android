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

import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.style.BaseStyle

/**
 * Tile-scheme description for one raster source.
 *
 * MapLibre substitutes `{z}`/`{x}`/`{y}` by placeholder name, not position, so an ArcGIS-style `.../tile/{z}/{y}/{x}`
 * template works unchanged — no TMS flag needed for those.
 */
data class RasterTileSpec(
    val tiles: List<String>,
    val tileSize: Int = DEFAULT_TILE_SIZE,
    val minZoom: Int = 0,
    val maxZoom: Int = DEFAULT_MAX_ZOOM,
    val attributionHtml: String? = null,
) {
    companion object {
        const val DEFAULT_TILE_SIZE = 256
        const val DEFAULT_MAX_ZOOM = 19
    }
}

/**
 * A selectable basemap. Vector entries hand MapLibre a style URL; raster entries are rendered as a background plus a
 * single raster layer over an empty style, so both kinds compose identically.
 */
sealed interface Basemap {
    val id: String

    /** Menu label. Every one is a proper noun, so these are deliberately not translated. */
    val label: String

    /** Vector style served as a MapLibre style document. */
    data class Vector(override val id: String, override val label: String, val styleUri: String) : Basemap

    /** Classic XYZ raster tiles — everything the OSMdroid map used to serve. */
    data class Raster(override val id: String, override val label: String, val spec: RasterTileSpec) : Basemap
}

/**
 * Basemaps offered by the MapLibre map, in menu order.
 *
 * The three vector styles come from OpenFreeMap, which requires no API key, no registration and imposes no request
 * limits. Only `liberty` is actively maintained upstream; `positron` and `dark` descend from the abandoned OpenMapTiles
 * style set, so if either rots the fix is to vendor its style JSON rather than chase the URL.
 *
 * The raster entries are a 1:1 carry-over of the OSMdroid tile sources so nothing a user had selected disappears in the
 * swap.
 */
object Basemaps {
    val Liberty =
        Basemap.Vector(id = "liberty", label = "Liberty", styleUri = "https://tiles.openfreemap.org/styles/liberty")

    val Positron =
        Basemap.Vector(id = "positron", label = "Positron", styleUri = "https://tiles.openfreemap.org/styles/positron")

    val Dark = Basemap.Vector(id = "dark", label = "Dark", styleUri = "https://tiles.openfreemap.org/styles/dark")

    val OpenStreetMap =
        Basemap.Raster(
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
        Basemap.Raster(
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
        Basemap.Raster(
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
        Basemap.Raster(
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
        Basemap.Raster(
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
        Basemap.Raster(
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

    /** Menu order: vector styles first, then the raster carry-overs. */
    val all: List<Basemap> =
        listOf(Liberty, Positron, Dark, OpenStreetMap, OpenTopo, UsgsTopo, UsgsSatellite, EsriTopo, EsriImagery)

    val default: Basemap = Liberty

    fun byId(id: String?): Basemap = all.firstOrNull { it.id == id } ?: default
}

/** Vector basemaps arrive as a style document; raster ones draw over a bare one that still declares its fonts. */
internal fun Basemap.toBaseStyle(): BaseStyle = when (this) {
    is Basemap.Vector -> BaseStyle.Uri(styleUri)
    is Basemap.Raster -> RasterBaseStyle
}

/**
 * Where a style gets its font glyphs. The vector styles above declare this same endpoint themselves; it is keyless and
 * unmetered, which is why the basemaps use OpenFreeMap in the first place.
 */
private const val GLYPHS_URL = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"

/**
 * The style a raster basemap draws its tiles over.
 *
 * Deliberately not `BaseStyle.Empty`, which is what this used to be. That document declares no `glyphs`, so a style
 * built on it can load no font at all, and a symbol layer carrying text has nothing to render with. The three vector
 * styles bring their own glyph endpoint, which is why the same layers drew there and nowhere else.
 *
 * The cost was not limited to the text. Confirmed by running the desktop app against the same camera on both: with
 * `BaseStyle.Empty` a raster basemap drew its tiles and **no mesh data at all** — not the node chips, and not even the
 * cluster bubbles, which are plain circles with no text in them. The first text layer to fail is `waypoint-label`,
 * composed ahead of the node layers, and everything added after it is lost with it.
 *
 * No sources or layers of its own — the basemap itself is added at runtime as a raster layer over the top of this.
 */
private val RasterBaseStyle: BaseStyle =
    BaseStyle.Json {
        put("version", STYLE_SPEC_VERSION)
        put("name", "Meshtastic raster basemap")
        put("glyphs", GLYPHS_URL)
        putJsonObject("sources") {}
        putJsonArray("layers") {}
    }

/** The MapLibre/Mapbox style specification version every style document here declares. */
private const val STYLE_SPEC_VERSION = 8

/**
 * The zoom levels this basemap actually has data for.
 *
 * The OSMdroid map took these from the tile source itself, so the map would not let you zoom past what the server could
 * serve. Several of these sources stop well short of the map's default ceiling — USGS and Esri at 16, OSM at 19 — and
 * zooming beyond that gets blank or stretched tiles.
 *
 * Vector styles carry their own zoom handling and overzoom cleanly, so they keep the map's default range.
 */
internal fun Basemap.zoomRange(): ClosedFloatingPointRange<Float> = when (this) {
    is Basemap.Vector -> DEFAULT_MIN_ZOOM..DEFAULT_MAX_ZOOM
    is Basemap.Raster -> spec.minZoom.toFloat()..spec.maxZoom.toFloat()
}

/** MaplibreMap's own default zoom range. */
internal const val DEFAULT_MIN_ZOOM = 0f
internal const val DEFAULT_MAX_ZOOM = 20f
