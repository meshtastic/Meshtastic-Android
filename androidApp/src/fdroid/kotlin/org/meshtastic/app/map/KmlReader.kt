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
package org.meshtastic.app.map

import org.xmlpull.v1.XmlPullParser

/** A KML `<Style>`: the colours and width this app can carry through to simplestyle. */
internal data class KmlStyle(
    val lineColor: String? = null,
    val lineWidth: Float? = null,
    val fillColor: String? = null,
    val filled: Boolean = true,
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

/** A `<Style>` id and what this app can use of it. Null when the style has no id to reference it by. */
internal fun XmlPullParser.readStyle(): Pair<String, KmlStyle>? {
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
internal fun XmlPullParser.readStyleBody(): KmlStyle {
    var style = KmlStyle()
    // Which sub-style the reader is inside. A Google Earth export routinely carries an IconStyle and a LabelStyle in
    // the same Style, each with its own <color>; taking any colour as the line colour painted routes in the icon's.
    var enclosing: String? = null
    var done = false

    while (!done && next() != XmlPullParser.END_DOCUMENT) {
        val tag = name
        when {
            eventType == XmlPullParser.START_TAG && tag in SUB_STYLES -> enclosing = tag

            eventType == XmlPullParser.START_TAG ->
                style = style.withStyleElement(tag = tag, enclosing = enclosing) { nextText().trim() }

            eventType == XmlPullParser.END_TAG && tag in SUB_STYLES -> enclosing = null

            eventType == XmlPullParser.END_TAG && tag == "Style" -> done = true

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
 * Only LineStyle and PolyStyle contribute: an IconStyle or LabelStyle colour says nothing about how a route or a zone
 * is drawn. `<fill>0</fill>` is how KML says "no fill", so it means outline only rather than a fill of nothing.
 */
private fun KmlStyle.withStyleElement(tag: String, enclosing: String?, value: () -> String): KmlStyle = when {
    tag == "color" && enclosing == "PolyStyle" -> copy(fillColor = value())
    tag == "color" && enclosing == "LineStyle" -> copy(lineColor = value())
    tag == "width" && enclosing == "LineStyle" -> copy(lineWidth = value().toFloatOrNull())
    tag == "fill" && enclosing == "PolyStyle" -> copy(filled = value() != "0")
    else -> this
}

/** A `<StyleMap>` id and the style id its `normal` pair points at. */
internal fun XmlPullParser.readStyleMap(): Pair<String, String>? {
    val id = getAttributeValue(null, "id")
    var key: String? = null
    var target: String? = null
    var done = id == null

    while (!done && next() != XmlPullParser.END_DOCUMENT) {
        when {
            eventType == XmlPullParser.START_TAG && name == "key" -> key = nextText().trim()

            eventType == XmlPullParser.START_TAG && name == "styleUrl" ->
                if (key == "normal") target = nextText().trim()

            eventType == XmlPullParser.END_TAG && name == "StyleMap" -> done = true

            else -> Unit
        }
    }
    return if (id != null && target != null) id to target else null
}

internal fun XmlPullParser.readPlacemark(): Placemark {
    val reader = PlacemarkReader()
    var done = false

    while (!done && next() != XmlPullParser.END_DOCUMENT) {
        when {
            eventType == XmlPullParser.START_TAG -> reader.onStartTag(this, name)
            eventType == XmlPullParser.END_TAG && name == "Placemark" -> done = true
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

    fun onStartTag(parser: XmlPullParser, tag: String) {
        when (tag) {
            "name" -> name = parser.nextText().trim()

            "description" -> description = parser.nextText().trim()

            "styleUrl" -> styleUrl = parser.nextText().trim()

            // An inline Style is consumed here or not at all: the top-level scan never sees it, because this reader
            // has already run to the Placemark's end tag by then.
            "Style" -> inlineStyle = parser.readStyleBody()

            in GEOMETRY_TAGS -> {
                pendingType = tag
                polygonTaken = false
            }

            "coordinates" -> takeCoordinates(parser.nextText())

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
private fun geometryFrom(type: String?, positions: List<String>?, polygonTaken: Boolean): KmlGeometry? = when {
    positions == null -> null

    type == "Point" -> positions.firstOrNull()?.let { pointGeometry(it) }

    type == "LineString" -> if (positions.size >= MIN_LINE_POSITIONS) lineGeometry(positions) else null

    type == "Polygon" ->
        if (!polygonTaken && positions.size >= MIN_RING_POSITIONS) polygonGeometry(positions) else null

    else -> null
}
