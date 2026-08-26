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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.nil
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.Source
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.FeaturesClickHandler
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.feature.map.maplibre.geojson.MapChipGlyph
import org.meshtastic.feature.map.maplibre.geojson.MapChipKey
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.featureValue
import org.meshtastic.feature.map.maplibre.geojson.toNodeChip

/**
 * Node markers drawn as chips — the rounded, node-coloured badge with the short name in it that
 * [org.meshtastic.core.ui.component.NodeChip] renders everywhere else in the app, and that the Google and OSMdroid maps
 * both put on the map.
 *
 * MapLibre cannot lay out a chip: `iconTextFit` sizes a sprite around a text layer, but the sprite it stretches can
 * only be tinted per feature if it is a signed distance field — and maplibre-compose's `drawAsSdf` uploads the
 * *unconverted* bitmap while telling MapLibre to read it as an SDF, so that route renders garbage (`ImageManager`
 * computes `toSdf()`, stores it in a map nothing reads, and passes the raw bitmap to `addImage`). Present on upstream
 * main as of 2026-08-26 and not yet reported; do not reach for `drawAsSdf` here until it is.
 *
 * So the whole chip is rasterized instead, exactly as the Google flavor rasterizes a `NodeChip` per marker. One image
 * per *distinct* chip, and a single layer that picks between them with a `match` on the chip key the feature carries —
 * rather than a layer per chip, which is what made a shared short name a fatal duplicate layer id.
 *
 * @param chipFilter narrows which features get a chip. The main map passes the unclustered-only test; the smaller maps
 *   have no clusters and pass nothing.
 */
@Composable
internal fun NodeChipLayer(
    id: String,
    source: Source,
    nodes: List<Node>,
    onNodeClick: ((Int) -> Unit)? = null,
    chipFilter: Expression<BooleanValue> = nil(),
) = MapChipLayer(
    id = id,
    source = source,
    chips = nodes.map { it.toNodeChip() },
    filter = chipFilter,
    onClick =
    onNodeClick?.let { click ->
        { features ->
            features.firstOrNull()?.properties?.get(NodeFeatureKeys.NODE_NUM)?.let { nodeNum ->
                click(nodeNum.jsonPrimitive.int)
                ClickResult.Consume
            } ?: ClickResult.Pass
        }
    },
)

/**
 * The chip layer itself: one rasterized image per distinct chip, picked per feature by the [MapChipKey.featureValue]
 * the feature carries under [NodeFeatureKeys.CHIP].
 *
 * Shared by the node maps and the discovery map, which draws differently coloured chips for the same reason.
 */
@Composable
internal fun MapChipLayer(
    id: String,
    source: Source,
    chips: List<MapChipKey>,
    filter: Expression<BooleanValue> = nil(),
    onClick: FeaturesClickHandler? = null,
) {
    val distinct = remember(chips) { chips.distinct().take(MAX_CHIP_IMAGES) }
    val images = rememberChipImages(distinct)
    val blank = rememberBlankPainter()

    SymbolLayer(
        id = id,
        source = source,
        filter = filter,
        iconImage =
        switch(
            input = feature[NodeFeatureKeys.CHIP].asString(),
            *images.map { (key, chip) -> case(key, image(chip.painter, chip.size)) }.toTypedArray(),
            // Nothing at all for a node past the image ceiling, rather than someone else's chip.
            fallback = image(blank, DpSize(1.dp, 1.dp)),
        ),
        iconAllowOverlap = const(true),
        onClick = onClick,
    )
}

/** A rasterized chip and the size MapLibre should draw it at. */
private class ChipImage(val painter: Painter, val size: DpSize)

/**
 * Rasterizes every distinct chip once.
 *
 * One `remember` over the whole set rather than a `remember` per chip: [Painter] identity is part of the key
 * maplibre-compose reference-counts style images by, so a painter rebuilt on recomposition would drop and re-upload
 * every chip on the map each time the node list emits.
 */
@Composable
private fun rememberChipImages(chips: List<MapChipKey>): Map<String, ChipImage> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.labelLarge

    // Vector painters have to be built in composition, so they are resolved here and handed to the rasterizer rather
    // than looked up inside it.
    val glyphs =
        mapOf(
            MapChipGlyph.SENSOR to rememberVectorPainter(MeshtasticIcons.Temperature),
            MapChipGlyph.SOCIAL to rememberVectorPainter(MeshtasticIcons.Person),
        )

    return remember(chips, measurer, density, textStyle, glyphs) {
        chips.associate { chip -> chip.featureValue() to chip.rasterize(measurer, textStyle, density, glyphs) }
    }
}

private fun MapChipKey.rasterize(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    density: Density,
    glyphs: Map<MapChipGlyph, Painter>,
): ChipImage {
    val style =
        textStyle.copy(textDecoration = TextDecoration.LineThrough.takeIf { struckThrough }, color = Color(foreground))
    // A chip carrying a glyph shows the icon instead of the name, so it measures no text.
    val layout = if (glyph == null) measurer.measure(AnnotatedString(label), style, density = density) else null

    // NodeChip is a Card with 8dp of horizontal padding around text that is at least 64dp wide and 28dp tall. Sizing
    // the sprite the same way is what keeps a map chip the size of the chip in the node list. An icon chip needs no
    // room for text, so it is a square badge instead.
    val width =
        with(density) {
            layout?.let { maxOf(MIN_WIDTH_DP.dp, it.size.width.toDp() + HORIZONTAL_PADDING_DP.dp * 2) } ?: HEIGHT_DP.dp
        }
    val cornerRadiusPx = with(density) { CORNER_RADIUS_DP.dp.toPx() }
    val borderPx = if (outlined) with(density) { BORDER_DP.dp.toPx() } else 0f

    return ChipImage(
        painter =
        ChipPainter(
            chip = this,
            layout = layout,
            cornerRadiusPx = cornerRadiusPx,
            borderPx = borderPx,
            glyphPainter = glyph?.let(glyphs::get),
            glyphSizePx = with(density) { GLYPH_DP.dp.toPx() },
        ),
        size = DpSize(width, HEIGHT_DP.dp),
    )
}

/** Draws the chip: the marker's colour behind, and its name or its icon centred in the foreground colour. */
private class ChipPainter(
    private val chip: MapChipKey,
    private val layout: TextLayoutResult?,
    private val cornerRadiusPx: Float,
    private val borderPx: Float,
    private val glyphPainter: Painter?,
    private val glyphSizePx: Float,
) : Painter() {
    // Never read: the caller always names an explicit size, which is what makes the chip's width follow its text.
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRoundRect(color = Color(chip.background), cornerRadius = CornerRadius(cornerRadiusPx))
        if (borderPx > 0f) {
            // Inset by half the stroke: a centred stroke on the sprite's own edge would be clipped to half width.
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(borderPx / 2f, borderPx / 2f),
                size = Size(size.width - borderPx, size.height - borderPx),
                cornerRadius = CornerRadius(cornerRadiusPx),
                style = Stroke(width = borderPx),
            )
        }
        if (glyphPainter != null) {
            val glyph = Size(glyphSizePx, glyphSizePx)
            translate(left = (size.width - glyphSizePx) / 2f, top = (size.height - glyphSizePx) / 2f) {
                with(glyphPainter) { draw(glyph, colorFilter = ColorFilter.tint(Color(chip.foreground))) }
            }
        } else if (layout != null) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = (size.width - layout.size.width) / 2f,
                    y = (size.height - layout.size.height) / 2f,
                ),
            )
        }
    }
}

/** The image a node past [MAX_CHIP_IMAGES] resolves to: one transparent pixel, drawn once. */
@Composable
private fun rememberBlankPainter(): Painter = remember {
    object : Painter() {
        override val intrinsicSize: Size = Size.Unspecified

        override fun DrawScope.onDraw() = Unit
    }
}

/** Material 3's `shapes.small`, which is the shape NodeChip asks its Card for. */
private const val CORNER_RADIUS_DP = 8
private const val MIN_WIDTH_DP = 64
private const val HEIGHT_DP = 28
private const val HORIZONTAL_PADDING_DP = 8

/** Border width for an outlined chip, matching the discovery map's own 1dp. */
private const val BORDER_DP = 1

/** Icon size inside a glyph chip, matching the discovery marker's own 16dp. */
private const val GLYPH_DP = 16

/**
 * A ceiling on how many chip images one layer will hold.
 *
 * Each distinct chip is a bitmap uploaded to the style, so this bounds both the memory and the size of the `match`
 * expression. Far above any real mesh in view; it exists so a pathological node list degrades instead of stalling.
 */
private const val MAX_CHIP_IMAGES = 250
