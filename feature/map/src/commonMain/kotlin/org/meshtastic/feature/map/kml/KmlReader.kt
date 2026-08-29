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

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.readSimpleElement

/** A `<Style>` id and what this app can use of it. Null when the style has no id to reference it by. */
internal fun XmlReader.readStyle(): Pair<String, KmlStyle>? {
    val id = getAttributeValue(null, "id")
    val style = readStyleBody()
    return id?.let { it to style }
}

/**
 * The colours and width of a `<Style>`, read through to its end tag.
 *
 * Split out from [readStyle] because a Placemark may carry its own `<Style>` with no id at all, and that one still has
 * to be read — it is the only styling such a placemark has.
 */
internal fun XmlReader.readStyleBody(): KmlStyle {
    var style = KmlStyle()
    // Which sub-style the reader is inside. A Google Earth export routinely carries an IconStyle and a LabelStyle in
    // the same Style, each with its own <color>; taking any colour as the line colour painted routes in the icon's.
    var enclosing: String? = null
    var done = false

    while (!done) {
        when (next()) {
            EventType.START_ELEMENT ->
                if (localName in SUB_STYLES) {
                    enclosing = localName
                } else {
                    style = style.withStyleElement(localName, enclosing) { readSimpleElement().trim() }
                }

            EventType.END_ELEMENT ->
                when (localName) {
                    in SUB_STYLES -> enclosing = null
                    "Style" -> done = true
                    else -> Unit
                }

            EventType.END_DOCUMENT -> done = true

            else -> Unit
        }
    }
    return style
}

/** The `<Style>` children that own a colour or a width. Anything else in a Style is skipped. */
private val SUB_STYLES = setOf("LineStyle", "PolyStyle", "IconStyle", "LabelStyle", "BalloonStyle", "ListStyle")

/**
 * Folds one element of a `<Style>` sub-style into the style being built.
 *
 * Only LineStyle, PolyStyle and an IconStyle's image contribute: an IconStyle or LabelStyle *colour* still says nothing
 * about how a route or a zone is drawn. `<fill>0</fill>` is how KML says "no fill", so it means outline only rather
 * than a fill of nothing.
 *
 * [value] is called only for an element that contributes, so an element we ignore is left unconsumed — exactly as it
 * was under the pull parser this replaced.
 */
private fun KmlStyle.withStyleElement(tag: String, enclosing: String?, value: () -> String): KmlStyle = when {
    // `<Icon>` is not itself a sub-style, so it falls through without clearing `enclosing` and the `<href>` inside
    // it
    // still reads as belonging to the IconStyle. NetworkLink's `<Link><href>` cannot be confused with it for the
    // same
    // reason: it is never inside an IconStyle.
    tag == "href" && enclosing == "IconStyle" -> copy(iconHref = value().takeIf { it.isNotBlank() })

    tag == "color" && enclosing == "PolyStyle" -> copy(fillColor = value())

    tag == "color" && enclosing == "LineStyle" -> copy(lineColor = value())

    tag == "width" && enclosing == "LineStyle" -> copy(lineWidth = value().toFloatOrNull())

    tag == "fill" && enclosing == "PolyStyle" -> copy(filled = value() != "0")

    else -> this
}

/** A `<StyleMap>` id and the style id its `normal` pair points at. */
internal fun XmlReader.readStyleMap(): Pair<String, String>? {
    val id = getAttributeValue(null, "id")
    var key: String? = null
    var target: String? = null
    var done = id == null

    while (!done) {
        when (next()) {
            EventType.START_ELEMENT ->
                when (localName) {
                    "key" -> key = readSimpleElement().trim()
                    "styleUrl" -> if (key == "normal") target = readSimpleElement().trim()
                    else -> Unit
                }

            EventType.END_ELEMENT -> if (localName == "StyleMap") done = true

            EventType.END_DOCUMENT -> done = true

            else -> Unit
        }
    }
    return if (id != null && target != null) id to target else null
}

internal fun XmlReader.readPlacemark(): Placemark {
    val reader = PlacemarkReader()
    var done = false

    while (!done) {
        when (next()) {
            EventType.START_ELEMENT -> reader.onStartElement(this, localName)
            EventType.END_ELEMENT -> if (localName == "Placemark") done = true
            EventType.END_DOCUMENT -> done = true
            else -> Unit
        }
    }
    return reader.build()
}

/**
 * Accumulates one Placemark as its elements arrive.
 *
 * A class rather than a pile of locals in the loop because a Polygon's outer ring and its holes both arrive as
 * `<coordinates>` and the reader has to remember which geometry it is inside and whether it already took a ring.
 */
private class PlacemarkReader {
    private var name: String? = null
    private var description: String? = null
    private var styleUrl: String? = null
    private var inlineStyle: KmlStyle? = null
    private val geometries = mutableListOf<KmlGeometry>()
    private var pendingType: String? = null
    private var polygonTaken = false

    fun onStartElement(reader: XmlReader, tag: String) {
        when (tag) {
            "name" -> name = reader.readSimpleElement().trim()

            "description" -> description = reader.readSimpleElement().trim()

            "styleUrl" -> styleUrl = reader.readSimpleElement().trim()

            // An inline Style is consumed here or not at all: the top-level scan never sees it, because this reader
            // has already run to the Placemark's end tag by then.
            "Style" -> inlineStyle = reader.readStyleBody()

            in GEOMETRY_TAGS -> {
                pendingType = tag
                polygonTaken = false
            }

            "coordinates" -> takeCoordinates(reader.readSimpleElement())

            else -> Unit
        }
    }

    private fun takeCoordinates(raw: String) {
        geometryFrom(pendingType, parseCoordinates(raw), polygonTaken)?.let { parsed ->
            geometries += parsed
            if (parsed.isPolygonal) polygonTaken = true
        }
    }

    fun build(): Placemark =
        Placemark(name = name, description = description, styleUrl = styleUrl, inlineStyle = inlineStyle).also {
            it.geometries += geometries
        }
}

/** The geometry elements this reader understands. Everything else in a Placemark is skipped. */
private val GEOMETRY_TAGS = setOf("Point", "LineString", "Polygon")

/**
 * One geometry from a `<coordinates>` run, or null when there is nothing usable in it.
 *
 * [polygonTaken] guards a polygon's holes: an inner ring arrives as another `<coordinates>` and only the outer one is
 * drawn, so a ring already taken is not overwritten.
 */
private fun geometryFrom(type: String?, positions: List<GeoPosition>?, polygonTaken: Boolean): KmlGeometry? = when {
    positions == null -> null

    type == "Point" -> positions.firstOrNull()?.let { pointGeometry(it) }

    type == "LineString" -> if (positions.size >= MIN_LINE_POSITIONS) lineGeometry(positions) else null

    type == "Polygon" ->
        if (!polygonTaken && positions.size >= MIN_RING_POSITIONS) polygonGeometry(positions) else null

    else -> null
}

/**
 * A `<GroundOverlay>`, read through to its end tag. Null when the overlay is missing its image or any box edge — there
 * is nothing drawable without either.
 */
@Suppress("CyclomaticComplexMethod") // one branch per KML tag; splitting them would hide the element shape
internal fun XmlReader.readGroundOverlay(): KmlGroundOverlay? {
    var name: String? = null
    var href: String? = null
    var north: Double? = null
    var south: Double? = null
    var east: Double? = null
    var west: Double? = null
    var rotation = 0.0
    var depth = 1

    while (depth > 0) {
        when (next()) {
            EventType.START_ELEMENT ->
                when (localName) {
                    "name" -> name = readSimpleElement().trim()
                    "href" -> href = readSimpleElement().trim()
                    "north" -> north = readSimpleElement().trim().toDoubleOrNull()
                    "south" -> south = readSimpleElement().trim().toDoubleOrNull()
                    "east" -> east = readSimpleElement().trim().toDoubleOrNull()
                    "west" -> west = readSimpleElement().trim().toDoubleOrNull()
                    "rotation" -> rotation = readSimpleElement().trim().toDoubleOrNull() ?: 0.0
                    else -> depth++
                }

            EventType.END_ELEMENT -> depth--

            EventType.END_DOCUMENT -> depth = 0

            else -> Unit
        }
    }

    val image = href?.takeIf { it.isNotBlank() }
    val edges = listOfNotNull(north, south, east, west)
    return if (image == null || edges.size < BOX_EDGES) {
        null
    } else {
        KmlGroundOverlay(
            name = name,
            href = image,
            north = edges[0],
            south = edges[1],
            east = edges[2],
            west = edges[3],
            rotationDegrees = rotation,
        )
    }
}

/** A LatLonBox is only a box with all four of north, south, east and west. */
private const val BOX_EDGES = 4
