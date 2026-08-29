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
package org.meshtastic.feature.map.kml

/** A KML `<Style>`: the colours and width this app can carry through to simplestyle. */
internal data class KmlStyle(
    val lineColor: String? = null,
    val lineWidth: Float? = null,
    val fillColor: String? = null,
    val filled: Boolean = true,
    /** `<IconStyle><Icon><href>` — the image a point is drawn with, if the KML names one. */
    val iconHref: String? = null,
)

internal class Placemark(
    val name: String?,
    val description: String?,
    val styleUrl: String?,
    /** A `<Style>` written inside the Placemark. Takes precedence over [styleUrl], as KML specifies. */
    val inlineStyle: KmlStyle? = null,
) {
    val geometries = mutableListOf<KmlGeometry>()
}

/** A parsed geometry, held as its GeoJSON type and already-formatted coordinates. */
internal class KmlGeometry(val type: String, val coordinates: String, val isPolygonal: Boolean)

/**
 * A KML `<GroundOverlay>`: an image draped over a `<LatLonBox>`.
 *
 * [rotationDegrees] is the box's optional `<rotation>` — degrees counter-clockwise about the box centre, per the KML
 * reference. [href] is untouched: a KMZ-packed relative path, an absolute URL, or (as the Site Planner exports) a
 * sibling file name that nothing can fetch — what is reachable is the importer's judgement, not the parser's.
 */
data class KmlGroundOverlay(
    val name: String?,
    val href: String,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val rotationDegrees: Double = 0.0,
)

/** Everything a KML document holds that this app can draw: vector features as GeoJSON, plus the image overlays. */
data class KmlConversion(val geoJson: String?, val groundOverlays: List<KmlGroundOverlay>)
