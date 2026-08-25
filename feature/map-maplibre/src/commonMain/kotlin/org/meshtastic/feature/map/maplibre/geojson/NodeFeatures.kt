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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.Node

/**
 * Radius, in metres, of the uncertainty circle firmware implies for a given `precision_bits`.
 *
 * Carried over verbatim from the OSMdroid marker so the circle keeps the exact size users already calibrate their trust
 * against. Anything outside 10..19 means "not degraded" and draws no circle.
 */
@Suppress("MagicNumber")
fun precisionMeters(precisionBits: Int): Double? = when (precisionBits) {
    10 -> 23345.484932
    11 -> 11672.7369
    12 -> 5836.36288
    13 -> 2918.175876
    14 -> 1459.0823719999053
    15 -> 729.53562
    16 -> 364.7622
    17 -> 182.375556
    18 -> 91.182212
    19 -> 45.58554
    else -> null
}

/** Formats an `@ColorInt` ARGB value as the `#rrggbb` string MapLibre expressions parse. */
@Suppress("MagicNumber")
internal fun Int.toCssHex(): String {
    val hex = (this and 0xFFFFFF).toString(16).padStart(6, '0')
    return "#$hex"
}

/**
 * Projects nodes into a GeoJSON [FeatureCollection] for the clustered node source.
 *
 * Nodes without a usable fix are dropped rather than emitted at (0, 0) — that is what produced the "flying through the
 * ocean" jump on the OSMdroid map.
 */
fun nodesToFeatureCollection(nodes: List<Node>, myNodeNum: Int? = null): FeatureCollection<Point, JsonObject?> =
    FeatureCollection(
        nodes.mapNotNull { node ->
            node.validPosition ?: return@mapNotNull null
            val (foreground, background) = node.colors
            Feature(
                geometry = Point(Position(longitude = node.longitude, latitude = node.latitude)),
                properties =
                buildJsonObject {
                    put(NodeFeatureKeys.NODE_NUM, node.num)
                    put(NodeFeatureKeys.SHORT_NAME, node.user.short_name)
                    put(NodeFeatureKeys.LONG_NAME, node.user.long_name)
                    put(NodeFeatureKeys.IS_FAVORITE, node.isFavorite)
                    put(NodeFeatureKeys.IS_ONLINE, node.isOnline)
                    put(NodeFeatureKeys.IS_SELF, myNodeNum != null && node.num == myNodeNum)
                    put(NodeFeatureKeys.FOREGROUND, foreground.toCssHex())
                    put(NodeFeatureKeys.BACKGROUND, background.toCssHex())
                    put(NodeFeatureKeys.LAST_HEARD, node.lastHeard)
                    put(NodeFeatureKeys.PRECISION_METERS, precisionMeters(node.position.precision_bits ?: 0) ?: 0.0)
                },
            )
        },
    )
