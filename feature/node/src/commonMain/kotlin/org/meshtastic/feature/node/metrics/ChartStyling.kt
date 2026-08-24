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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

/**
 * Utility object for chart styling and component creation. Provides reusable styled lines, points, and axes for Vico
 * charts.
 *
 * **Design principles** (per [design#53](https://github.com/meshtastic/design/issues/53)):
 * - Default to thin lines **without** point markers to avoid clutter on dense timeseries.
 * - Show a single dot only at the marker/cursor position (handled by [rememberMarker]).
 * - Use `Interpolator.cubic()` for smooth monotone curves that won't overshoot between sparse points.
 * - Reserve bold lines for the single most-important series; use subtle/gradient fills for secondary data.
 */
@Suppress("TooManyFunctions")
object ChartStyling {
    // Line stroke widths
    const val THIN_LINE_WIDTH_DP = 1.5f
    const val MEDIUM_LINE_WIDTH_DP = 2f
    const val THICK_LINE_WIDTH_DP = 2.5f

    /**
     * Creates a clean timeseries line — thin, smooth, with **no** point markers. This is the default style recommended
     * by Oscar's UX guidance: "thin lines, and maybe a dot where the cursor is."
     *
     * @param lineColor The color of the line
     * @param lineWidth Width of the line in dp
     * @param interpolator The line interpolation strategy. Defaults to monotone
     *   [cubic][LineCartesianLayer.Interpolator.cubic] which won't overshoot between sparse data points (unlike
     *   catmull-rom). Use [Sharp][LineCartesianLayer.Interpolator.Sharp] for discrete/integer metrics like hop counts.
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createStyledLine(
        lineColor: Color,
        lineWidth: Float = MEDIUM_LINE_WIDTH_DP,
        interpolator: LineCartesianLayer.Interpolator = LineCartesianLayer.Interpolator.cubic(),
    ): LineCartesianLayer.Line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
        stroke = LineCartesianLayer.LineStroke.Continuous(lineWidth.dp),
        interpolator = interpolator,
    )

    /**
     * Creates a line with a gradient area fill effect. Ideal for emphasising a single series or showing magnitude. The
     * gradient goes from the line color at ~30% opacity to near-transparent.
     *
     * @param lineColor The primary color of the line
     * @param lineWidth Width of the line in dp
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createGradientLine(
        lineColor: Color,
        lineWidth: Float = MEDIUM_LINE_WIDTH_DP,
        interpolator: LineCartesianLayer.Interpolator = LineCartesianLayer.Interpolator.cubic(),
    ): LineCartesianLayer.Line {
        val gradientBrush =
            Brush.verticalGradient(colors = listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0.05f)))
        return LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(gradientBrush)),
            stroke = LineCartesianLayer.LineStroke.Continuous(lineWidth.dp),
            interpolator = interpolator,
        )
    }

    /**
     * Creates a bold line suitable for highlighting the primary metric in a multi-series chart.
     *
     * @param lineColor The color of the line
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createBoldLine(
        lineColor: Color,
        interpolator: LineCartesianLayer.Interpolator = LineCartesianLayer.Interpolator.cubic(),
    ): LineCartesianLayer.Line =
        createStyledLine(lineColor = lineColor, lineWidth = THICK_LINE_WIDTH_DP, interpolator = interpolator)

    /**
     * Creates a subtle line suitable for secondary metrics that should not dominate the chart.
     *
     * @param lineColor The color of the line
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createSubtleLine(lineColor: Color): LineCartesianLayer.Line =
        createStyledLine(lineColor = lineColor, lineWidth = THIN_LINE_WIDTH_DP)

    /**
     * Creates a dashed secondary line. Useful for distinguishing two metrics that share the same axis without relying
     * on colour alone.
     *
     * @param lineColor The color of the dashed line
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createDashedLine(
        lineColor: Color,
        interpolator: LineCartesianLayer.Interpolator = LineCartesianLayer.Interpolator.cubic(),
    ): LineCartesianLayer.Line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
        stroke =
        LineCartesianLayer.LineStroke.Dashed(
            thickness = THIN_LINE_WIDTH_DP.dp,
            dashLength = 6.dp,
            gapLength = 3.dp,
        ),
        interpolator = interpolator,
    )

    /**
     * Gets Material 3 theme-aware colors with opacity. Useful for creating color variants while respecting the current
     * theme.
     *
     * @param baseColor The base color
     * @param alpha The alpha/opacity value (0f-1f)
     * @return Color with adjusted alpha
     */
    fun createThemedColor(baseColor: Color, alpha: Float = 1f): Color = baseColor.copy(alpha = alpha)

    /**
     * Creates a [HorizontalLine] decoration for a reference threshold (e.g. battery low, pressure normal).
     *
     * @param y The y-value to draw the line at
     * @param color The color of the threshold line
     * @param label Optional label text for the line
     */
    @Composable
    fun rememberThresholdLine(y: Double, color: Color, label: String? = null): Decoration {
        val line = rememberLineComponent(fill = Fill(color.copy(alpha = 0.4f)), thickness = 1.dp)
        val labelComponent =
            if (label != null) {
                rememberTextComponent(
                    style =
                    TextStyle(color = color.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    padding = Insets(horizontal = 4.dp, vertical = 1.dp),
                )
            } else {
                null
            }
        return remember(y, color, label) {
            HorizontalLine(
                y = { y },
                line = line,
                labelComponent = labelComponent,
                label = { label ?: "" },
                horizontalLabelPosition = Position.Horizontal.End,
                verticalLabelPosition = Position.Vertical.Top,
            )
        }
    }

    /**
     * Creates and remembers a default [CartesianMarker] styled for the Meshtastic theme.
     *
     * @param valueFormatter The formatter for the marker label content.
     * @param showIndicator Whether to show the point indicator on the line/column.
     * @return A configured [CartesianMarker]
     */
    @Composable
    fun rememberMarker(
        valueFormatter: DefaultCartesianMarker.ValueFormatter =
            DefaultCartesianMarker.ValueFormatter.default(colorCode = true),
        showIndicator: Boolean = true,
    ): CartesianMarker {
        val labelBackground =
            rememberShapeComponent(
                fill = Fill(MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MarkerCornerBasedShape(MaterialTheme.shapes.extraSmall),
                strokeFill = Fill(MaterialTheme.colorScheme.outlineVariant),
                strokeThickness = 1.dp,
            )
        val label =
            rememberTextComponent(
                style =
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                ),
                background = labelBackground,
                padding = Insets(horizontal = 8.dp, vertical = 4.dp),
                margins = Insets(bottom = 4.dp),
            )
        val guideline =
            rememberLineComponent(
                fill = Fill(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                thickness = 1.dp,
            )

        val indicator =
            if (showIndicator) {
                // Force alpha to 1f so the indicator is visible even for "invisible" lines
                { color: Color -> ShapeComponent(fill = Fill(color.copy(alpha = 1f)), shape = CircleShape) }
            } else {
                null
            }

        return rememberDefaultCartesianMarker(
            label = label,
            valueFormatter = valueFormatter,
            guideline = guideline,
            indicator = indicator,
        )
    }

    /**
     * Creates a [DefaultCartesianMarker.ValueFormatter] that colors the text to match the series color.
     *
     * @param format A lambda that provides the string content for a given Y value and its series color.
     */
    fun createColoredMarkerValueFormatter(
        format: (value: Double, color: Color) -> String,
    ): DefaultCartesianMarker.ValueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
        buildAnnotatedString {
            targets.forEachIndexed { index, target ->
                if (index > 0) append(", ")
                if (target is LineCartesianLayerMarkerTarget) {
                    target.points.forEachIndexed { pointIndex, point ->
                        if (pointIndex > 0) append(", ")
                        // Pass the opaque color to the format lambda so callers can match without alpha gymnastics.
                        // Apply 0.8 alpha only on the rendered text for readability.
                        val opaqueColor = point.color.copy(alpha = 1f)
                        val text = format(point.entry.y, opaqueColor)
                        withStyle(SpanStyle(color = opaqueColor.copy(alpha = .8f), fontWeight = FontWeight.Bold)) {
                            append(text)
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates and remembers a [com.patrykandpatrick.vico.compose.common.component.TextComponent] styled for axis
     * labels.
     */
    @Composable
    fun rememberAxisLabel(color: Color = MaterialTheme.colorScheme.onSurfaceVariant): TextComponent =
        rememberTextComponent(style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium))
}

/**
 * Creates a [LineCartesianLayer] only when [hasData] is true, returning null otherwise.
 *
 * Extracts the repeated `if (data.isNotEmpty()) rememberLineCartesianLayer(...) else null` pattern used in every metric
 * chart composable.
 */
@Composable
fun rememberConditionalLayer(
    hasData: Boolean,
    lineProvider: LineCartesianLayer.LineProvider,
    verticalAxisPosition: Axis.Position.Vertical,
    rangeProvider: CartesianLayerRangeProvider? = null,
): LineCartesianLayer? = if (hasData) {
    rememberLineCartesianLayer(
        lineProvider = lineProvider,
        verticalAxisPosition = verticalAxisPosition,
        rangeProvider = rangeProvider ?: CartesianLayerRangeProvider.auto(),
    )
} else {
    null
}
