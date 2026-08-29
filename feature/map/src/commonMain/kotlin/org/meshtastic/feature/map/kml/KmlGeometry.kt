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

import kotlin.math.abs
import kotlin.math.sign

internal fun pointGeometry(position: GeoPosition) = KmlGeometry("Point", position.json(), isPolygonal = false)

/**
 * A line, split into a MultiLineString if it crosses the antimeridian.
 *
 * RFC 7946 &sect;3.1.9 asks for the cut, and without it a track between, say, Fiji and Samoa draws as a line back
 * across the entire world rather than the short hop it is.
 */
internal fun lineGeometry(positions: List<GeoPosition>): KmlGeometry {
    val segments = positions.splitAtAntimeridian()
    return if (segments.size == 1) {
        KmlGeometry("LineString", segments.single().json(), isPolygonal = false)
    } else {
        KmlGeometry(
            type = "MultiLineString",
            coordinates = segments.joinToString(",", prefix = "[", postfix = "]") { it.json() },
            isPolygonal = false,
        )
    }
}

internal fun polygonGeometry(ring: List<GeoPosition>): KmlGeometry {
    // RFC 7946 §3.1.6: an exterior ring winds counterclockwise. KML says nothing about winding and exporters go both
    // ways, so the ring is turned rather than trusted.
    val wound = if (ring.isCounterClockwise()) ring else ring.reversed()
    // GeoJSON requires a closed ring; KML usually closes its own, but not always.
    val closed = if (wound.first() == wound.last()) wound else wound + wound.first()
    return KmlGeometry(type = "Polygon", coordinates = "[${closed.json()}]", isPolygonal = true)
}

private fun GeoPosition.json(): String = "[$longitude,$latitude]"

private fun List<GeoPosition>.json(): String = joinToString(",", prefix = "[", postfix = "]") { it.json() }

/**
 * The ring's winding, by the shoelace sum.
 *
 * A positive sum is a clockwise ring in a longitude/latitude frame, so counterclockwise — the one RFC 7946 wants — is
 * the negative case.
 */
private fun List<GeoPosition>.isCounterClockwise(): Boolean {
    var sum = 0.0
    for (index in indices) {
        val from = this[index]
        val to = this[(index + 1) % size]
        sum += (to.longitude - from.longitude) * (to.latitude + from.latitude)
    }
    return sum < 0
}

/**
 * The line broken wherever consecutive positions jump more than half the world in longitude.
 *
 * Such a jump is the short way across &plusmn;180&deg;, not a journey the long way round, so each crossing ends one
 * segment on the meridian and starts the next on the other side at the same latitude.
 *
 * Polygons are deliberately left whole: cutting a ring means splitting it into several rings, and no import this app
 * has seen needs it. A polygon spanning the antimeridian will still draw the long way round.
 */
private fun List<GeoPosition>.splitAtAntimeridian(): List<List<GeoPosition>> {
    val segments = mutableListOf<List<GeoPosition>>()
    var current = mutableListOf(first())

    for (next in drop(1)) {
        val previous = current.last()
        val delta = next.longitude - previous.longitude
        if (abs(delta) > HALF_TURN) {
            val exit = if (delta > 0) -HALF_TURN else HALF_TURN
            val latitude = crossingLatitude(previous, next, exit)
            current += GeoPosition(exit, latitude)
            segments += current
            current = mutableListOf(GeoPosition(-exit, latitude))
        }
        current += next
    }

    segments += current
    return segments
}

/** The latitude at which the segment meets the antimeridian, interpolated along the short way round. */
private fun crossingLatitude(from: GeoPosition, to: GeoPosition, exit: Double): Double {
    val delta = to.longitude - from.longitude
    val shortWay = delta - FULL_TURN * sign(delta)
    val fraction = ((exit - from.longitude) / shortWay).coerceIn(0.0, 1.0)
    return from.latitude + fraction * (to.latitude - from.latitude)
}

private const val HALF_TURN = 180.0
private const val FULL_TURN = 360.0

/**
 * KML coordinates are whitespace-separated `lon,lat[,alt]` tuples; GeoJSON wants `[lon, lat]` pairs.
 *
 * Altitude is dropped rather than carried: nothing on either map reads it, and a third ordinate would make every
 * downstream bounding-box calculation handle a case it never needs to.
 */
internal fun parseCoordinates(raw: String): List<GeoPosition>? {
    val positions =
        raw.trim().split(WHITESPACE).mapNotNull { tuple ->
            val parts = tuple.split(',')
            val longitude = parts.getOrNull(0)?.trim()?.toDoubleOrNull()?.takeIf { it.isValidOrdinate(MAX_LONGITUDE) }
            val latitude = parts.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { it.isValidOrdinate(MAX_LATITUDE) }
            if (longitude == null || latitude == null) null else GeoPosition(longitude, latitude)
        }
    return positions.ifEmpty { null }
}

/** GeoJSON's ordinate bounds, per RFC 7946. */
private const val MAX_LONGITUDE = 180.0
private const val MAX_LATITUDE = 90.0

/**
 * Whether an ordinate is one this converter will write, checked before the number reaches the JSON.
 *
 * `toDoubleOrNull` accepts "NaN" and "Infinity", and these coordinates are interpolated into the output as text — so a
 * single such ordinate emits a bare `NaN` token and makes the *whole* converted file unparseable, taking every other
 * placemark in it down too. That is the same shape of failure as writing `0,498` for a decimal under a comma-decimal
 * locale. Out-of-range values go on the same pass: a coordinate outside RFC 7946's bounds is corrupt input rather than
 * a place on Earth.
 */
private fun Double.isValidOrdinate(limit: Double): Boolean = isFinite() && this in -limit..limit

/** Whitespace between KML coordinate tuples. */
private val WHITESPACE = Regex("\\s+")

/** A GeoJSON ring needs three distinct positions before it can be closed into a polygon. */
internal const val MIN_RING_POSITIONS = 3
internal const val MIN_LINE_POSITIONS = 2
