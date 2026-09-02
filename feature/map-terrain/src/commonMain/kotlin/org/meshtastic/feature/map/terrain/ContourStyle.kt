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
package org.meshtastic.feature.map.terrain

/**
 * A contour line's style, expressed as simplestyle-spec property values (`stroke`, `stroke-width`, `stroke-opacity`)
 * rather than a platform color type — both renderers already consume simplestyle properties for imported layers
 * (MapLibre's `CustomLayers.kt` style expressions; Google's `applySimpleStyleSpec()`), so a contour feature carrying
 * these properties styles itself identically on both flavors with no per-flavor color logic to keep in sync.
 *
 * Colors here are a placeholder, not a design-reviewed choice — a topo-map-conventional brown, distinguishable from
 * this app's existing traceroute/geofence palette (both of which already use blue/orange). Needs a pass against the
 * Meshtastic design standards before shipping (constitution principle V), same as any other new on-map visual element.
 */
data class ContourStyle(val strokeHexColor: String, val strokeWidth: Float, val strokeOpacity: Float)

object ContourStyling {

    fun styleFor(line: ContourLine, zoom: Int, metric: Boolean): ContourStyle {
        val isIndex = ContourIntervals.isIndexLevel(line.elevationMeters, zoom, metric)
        return if (isIndex) INDEX_STYLE else MINOR_STYLE
    }

    private val INDEX_STYLE = ContourStyle(strokeHexColor = TOPO_BROWN, strokeWidth = 1.5f, strokeOpacity = 0.55f)
    private val MINOR_STYLE = ContourStyle(strokeHexColor = TOPO_BROWN, strokeWidth = 0.75f, strokeOpacity = 0.3f)

    private const val TOPO_BROWN = "#8B5A2B"
}
