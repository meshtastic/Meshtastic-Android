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
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.BooleanValue
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.VectorSource
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys

/**
 * The count on a chip that stands for several nodes at one spot.
 *
 * A map layer cannot host a composable, so this rasterizes Material 3's large badge itself: the label style
 * [androidx.compose.material3.Badge] uses at its 16dp height, pill shape and 4dp padding, in the secondary color the
 * Google map's badge is given. It sits where `BadgedBox` puts one, overlapping the chip's top trailing corner, so both
 * maps show the same badge.
 */
@Suppress("SpreadOperator") // switch() only takes its cases as varargs
@Composable
internal fun StackBadgeLayer(id: String, source: VectorSource, labels: List<String>, filter: Expression<BooleanValue>) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelSmall
    // Secondary rather than the default error color, as on the Google map: a count of nodes is not an alert.
    val container = MaterialTheme.colorScheme.secondary
    val content = contentColorFor(container)
    val images =
        remember(labels, measurer, density, style, container, content) {
            labels.associateWith { label ->
                val layout = measurer.measure(AnnotatedString(label), style.copy(color = content), density = density)
                val width = with(density) { maxOf(BADGE_HEIGHT, layout.size.width.toDp() + BADGE_PADDING * 2) }
                BadgePainter(layout, container) to DpSize(width, BADGE_HEIGHT)
            }
        }
    if (images.isEmpty()) return

    SymbolLayer(
        id = id,
        source = source,
        filter = filter,
        iconImage =
        switch(
            input = feature[NodeFeatureKeys.STACK_COUNT].asString(),
            *images.map { (label, image) -> case(label, image(image.first, image.second)) }.toTypedArray(),
            fallback = image(images.values.first().first, images.values.first().second),
        ),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconTranslate = const(BADGE_OFFSET),
    )
}

/** A pill in the badge's container color with its label centered in it. */
private class BadgePainter(private val layout: TextLayoutResult, private val container: Color) : Painter() {
    // Never read: the layer always names an explicit size, which is what lets the pill grow with its label.
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRoundRect(color = container, cornerRadius = CornerRadius(size.height / 2f))
        drawText(
            textLayoutResult = layout,
            topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f),
        )
    }
}

// Material 3's large badge.
private val BADGE_HEIGHT = 16.dp
private val BADGE_PADDING = 4.dp

// A chip is 64x28dp and centered on its point; this puts the badge's center just inside its top trailing corner.
private val BADGE_OFFSET = DpOffset(28.dp, (-14).dp)
