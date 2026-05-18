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
package org.meshtastic.feature.map.model

import org.jetbrains.compose.resources.StringResource
import org.maplibre.compose.style.BaseStyle
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_style_dark
import org.meshtastic.core.resources.map_style_light
import org.meshtastic.core.resources.map_style_osm
import org.meshtastic.core.resources.map_style_road_map
import org.meshtastic.core.resources.map_style_satellite
import org.meshtastic.core.resources.map_style_terrain

/**
 * Predefined map tile styles available in the app.
 *
 * Uses free tile sources that do not require API keys.
 */
enum class MapStyle(val label: StringResource, val styleUri: String) {
    /** OpenStreetMap default tiles via OpenFreeMap Liberty style. */
    OpenStreetMap(label = Res.string.map_style_osm, styleUri = "https://tiles.openfreemap.org/styles/liberty"),

    /** Clean, light cartographic style via OpenFreeMap Positron. */
    Light(label = Res.string.map_style_light, styleUri = "https://tiles.openfreemap.org/styles/positron"),

    /** Topographic style via OpenFreeMap Bright. */
    Terrain(label = Res.string.map_style_terrain, styleUri = "https://tiles.openfreemap.org/styles/bright"),

    /** US road-map style via Americana. */
    RoadMap(label = Res.string.map_style_road_map, styleUri = "https://americanamap.org/style.json"),

    /** Dark mode style via OpenFreeMap Fiord. */
    Dark(label = Res.string.map_style_dark, styleUri = "https://tiles.openfreemap.org/styles/fiord"),

    /** Satellite imagery via Esri World Imagery (free for non-commercial use). */
    Satellite(label = Res.string.map_style_satellite, styleUri = SATELLITE_STYLE_URI),
    ;

    fun toBaseStyle(): BaseStyle = when (this) {
        Satellite -> BaseStyle.Json(SATELLITE_STYLE_JSON)
        else -> BaseStyle.Uri(styleUri)
    }
}

/** Stable URI used as persistence key for satellite style selection. */
private const val SATELLITE_STYLE_URI = "satellite://esri-world-imagery"

/**
 * Inline MapLibre style JSON for raster satellite imagery.
 *
 * Uses Esri World Imagery tiles which are free for non-commercial and educational use.
 */
@Suppress("MaxLineLength")
private const val SATELLITE_STYLE_JSON: String =
    """{"version":8,"name":"Satellite","sources":{"esri-satellite":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"maxzoom":18,"attribution":"Esri, Maxar, Earthstar Geographics"}},"layers":[{"id":"satellite","type":"raster","source":"esri-satellite"}]}"""
