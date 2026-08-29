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
import org.meshtastic.core.model.util.precisionRadiusMetersOrNull

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
                    // Omitted rather than written as 0.0 when the node reports no precision: 0 is a real
                    // radius, and a reader cannot tell the difference. GeoCircle drops such nodes instead.
                    precisionRadiusMetersOrNull(node.position.precision_bits)?.let {
                        put(NodeFeatureKeys.PRECISION_METERS, it)
                    }
                    put(NodeFeatureKeys.CHIP, node.toNodeChip().featureValue())
                },
            )
        },
    )

/**
 * One distinct chip appearance. Two markers that look identical only need drawing once.
 *
 * @param outlined draws a white border, as the discovery map's chips have. Node chips have none, matching
 *   [org.meshtastic.core.ui.component.NodeChip].
 * @param glyph drawn instead of [label], for the discovery map's sensor and social markers. The Google discovery map
 *   substitutes an icon for the name the same way.
 */
internal data class MapChipKey(
    val label: String,
    val background: Int,
    val foreground: Int,
    val struckThrough: Boolean = false,
    val outlined: Boolean = false,
    val glyph: MapChipGlyph? = null,
)

/** The icons a chip can carry in place of its text. */
internal enum class MapChipGlyph {
    /** A node whose traffic is mostly environment telemetry. */
    SENSOR,

    /** A node whose traffic is mostly messages. */
    SOCIAL,
}

/**
 * The value a feature carries so one layer can pick that marker's chip image.
 *
 * Every field that changes the pixels is in the key: a short name is not unique, and neither is a colour.
 */
internal fun MapChipKey.featureValue(): String =
    "$label ${background.toString(HEX_RADIX)} ${foreground.toString(HEX_RADIX)} $struckThrough $outlined $glyph"

internal fun Node.toNodeChip(): MapChipKey {
    val (foreground, background) = colors
    return MapChipKey(
        // Matches NodeChip, which shows "???" rather than an empty badge for a node that has not sent a name yet.
        label = user.short_name.ifEmpty { UNNAMED_LABEL },
        background = background,
        foreground = foreground,
        struckThrough = isIgnored,
    )
}

private const val UNNAMED_LABEL = "???"
private const val HEX_RADIX = 16
