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
package org.meshtastic.feature.map.maplibre.geojson

/**
 * simplestyle-spec property keys carried on a contour line feature — read by
 * [ContourLayer][org.meshtastic.feature.map.maplibre.layers.ContourLayer]'s style expressions.
 *
 * Its own file rather than living alongside [contourLinesToFeatures] in `ContourFeatures.kt`: detekt's
 * `MatchingDeclarationName` requires a file with a single top-level class/object to be named after it, and
 * `ContourFeatures.kt`'s own name describes the functions that file actually holds, not this small keys holder.
 */
internal object ContourFeatureKeys {
    const val STROKE = "stroke"
    const val STROKE_WIDTH = "stroke-width"
    const val STROKE_OPACITY = "stroke-opacity"
}
