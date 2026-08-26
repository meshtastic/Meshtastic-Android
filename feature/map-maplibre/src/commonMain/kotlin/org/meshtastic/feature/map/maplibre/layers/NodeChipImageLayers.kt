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
package org.meshtastic.feature.map.maplibre.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.Source
import org.maplibre.compose.util.ClickResult
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.needsChipImage
import org.meshtastic.feature.map.maplibre.geojson.toCssHex

/**
 * Node chips drawn as images, for the nodes the text layer cannot draw.
 *
 * Short names on a Meshtastic mesh are often emoji, and the vector basemaps serve Noto Sans and nothing else, so the
 * text layer draws those as nothing at all — a node with an emoji short name appeared as a bare coloured dot with no
 * label. The same problem, and the same fix, as the waypoint icons.
 *
 * Drawing the chip rather than just the glyph is what makes these read as chips: the rounded rectangle, the node's own
 * colour and the name inside it, the way [org.meshtastic.core.ui.component.NodeChip] renders everywhere else in the
 * app. One layer per distinct chip, so the count is bounded by how many *different* emoji names are in range rather
 * than by the node count.
 */
@Composable
internal fun NodeChipImageLayers(nodes: List<Node>, source: Source, onNodeClick: (Int) -> Unit) {
    val chips =
        remember(nodes) {
            nodes
                .filter { it.user.short_name.needsChipImage() }
                .map { node ->
                    val (foreground, background) = node.colors
                    ChipKey(node.user.short_name, background, foreground)
                }
                .distinct()
                .take(MAX_CHIP_IMAGE_LAYERS)
        }

    chips.forEach { chip ->
        SymbolLayer(
            // The id carries the colour as well as the name: two nodes can share a short name and never share a
            // colour, and a duplicate layer id is rejected outright ("already owned by a different live layer
            // descriptor"), which took out every chip on the map rather than just the clashing one.
            id = "node-chip-image-${chip.id()}",
            source = source,
            filter =
            !feature.has("point_count") and
                (feature[NodeFeatureKeys.SHORT_NAME].asString() eq const(chip.name)) and
                (feature[NodeFeatureKeys.BACKGROUND].asString() eq const(chip.backgroundHex)),
            iconImage = image(rememberChipPainter(chip), DpSize(CHIP_WIDTH_DP.dp, CHIP_HEIGHT_DP.dp)),
            iconAllowOverlap = const(true),
            onClick = { features ->
                features.firstOrNull()?.properties?.get(NodeFeatureKeys.NODE_NUM)?.let { nodeNum ->
                    onNodeClick(nodeNum.jsonPrimitive.int)
                    ClickResult.Consume
                } ?: ClickResult.Pass
            },
        )
    }
}

/** One distinct chip appearance: the same name in the same colours only needs drawing once. */
private data class ChipKey(val name: String, val background: Int, val foreground: Int) {
    /** The colour as the feature carries it, so the filter can compare against the property directly. */
    val backgroundHex: String = background.toCssHex()
}

/** A layer id must be unique and safe, and a short name can be anything — so key on code points plus the colour. */
private fun ChipKey.id(): String =
    name.map { it.code.toString(HEX_RADIX) }.joinToString("-") + "-" + background.toString(HEX_RADIX)

/** Draws a chip the way NodeChip does: rounded rectangle, node colour, name centred inside. */
@Composable
private fun rememberChipPainter(chip: ChipKey): Painter {
    val measurer = rememberTextMeasurer()
    val layout =
        remember(chip, measurer) { measurer.measure(AnnotatedString(chip.name), TextStyle(fontSize = CHIP_TEXT_SP.sp)) }

    return remember(layout, chip) {
        object : Painter() {
            override val intrinsicSize = Size(CHIP_WIDTH_PX, CHIP_HEIGHT_PX)

            override fun DrawScope.onDraw() {
                drawRoundRect(
                    color = Color(chip.background),
                    cornerRadius = CornerRadius(size.height * CORNER_FRACTION),
                )
                drawText(
                    textLayoutResult = layout,
                    color = Color(chip.foreground),
                    topLeft =
                    Offset(x = (size.width - layout.size.width) / 2f, y = (size.height - layout.size.height) / 2f),
                )
            }
        }
    }
}

/** Matches NodeChip's own minimum size, so a map chip is the size of the chip everywhere else. */
private const val CHIP_WIDTH_DP = 64
private const val CHIP_HEIGHT_DP = 28
private const val CHIP_WIDTH_PX = 192f
private const val CHIP_HEIGHT_PX = 84f
private const val CHIP_TEXT_SP = 34
private const val CORNER_FRACTION = 0.28f
private const val HEX_RADIX = 16

/**
 * A ceiling on how many chip images the map will draw.
 *
 * Each distinct chip is its own layer, so an unusual mesh where every node has a different emoji name would otherwise
 * add a layer per node. Beyond this the remaining nodes keep the plain coloured marker rather than the map slowing to a
 * crawl.
 */
private const val MAX_CHIP_IMAGE_LAYERS = 24
