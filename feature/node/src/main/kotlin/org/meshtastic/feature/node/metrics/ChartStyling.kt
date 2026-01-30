/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

/**
 * Utility object for chart styling and component creation. Provides reusable styled lines, points, and axes for Vico
 * charts.
 */
object ChartStyling {
    // Point sizes
    const val SMALL_POINT_SIZE_DP = 6f
    const val MEDIUM_POINT_SIZE_DP = 8f
    const val LARGE_POINT_SIZE_DP = 10f

    // Line stroke widths
    const val THIN_LINE_WIDTH_DP = 1.5f
    const val MEDIUM_LINE_WIDTH_DP = 2f
    const val THICK_LINE_WIDTH_DP = 2.5f

    /**
     * Creates a solid line with optional point markers.
     *
     * @param lineColor The color of the line
     * @param pointSize Size of point markers (in dp). If null, no point markers are shown.
     * @param lineWidth Width of the line in dp
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createStyledLine(
        lineColor: Color,
        pointSize: Float? = MEDIUM_POINT_SIZE_DP,
        lineWidth: Float = MEDIUM_LINE_WIDTH_DP,
    ): LineCartesianLayer.Line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
        pointProvider =
        pointSize?.let {
            LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    rememberShapeComponent(fill = Fill(lineColor), shape = CircleShape),
                    size = it.dp,
                ),
            )
        },
        stroke = LineCartesianLayer.LineStroke.Continuous(lineWidth.dp),
    )

    /**
     * Creates a transparent line (no line, only points). Useful for distinguishing multiple metrics on the same chart.
     *
     * @param pointColor The color of the point markers
     * @param pointSize Size of point markers in dp
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createPointOnlyLine(pointColor: Color, pointSize: Float = MEDIUM_POINT_SIZE_DP): LineCartesianLayer.Line =
        LineCartesianLayer.rememberLine(
            // we still need to give the line a color, the Marker derives the label color from the line
            fill = LineCartesianLayer.LineFill.single(Fill(pointColor)),
            // magic sauce to make the line disappear
            stroke = LineCartesianLayer.LineStroke.Dashed(thickness = 0.dp, dashLength = 0.dp),
            pointProvider =
            LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    rememberShapeComponent(fill = Fill(pointColor), shape = CircleShape),
                    size = pointSize.dp,
                ),
            ),
        )

    /**
     * Creates a line with a gradient fill effect. The gradient goes from the line color to transparent.
     *
     * @param lineColor The primary color of the line
     * @param pointSize Size of point markers (in dp). If null, no point markers are shown.
     * @param lineWidth Width of the line in dp
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createGradientLine(
        lineColor: Color,
        pointSize: Float? = MEDIUM_POINT_SIZE_DP,
        lineWidth: Float = MEDIUM_LINE_WIDTH_DP,
    ): LineCartesianLayer.Line {
        val gradientBrush =
            Brush.verticalGradient(colors = listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0.1f)))
        return LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(gradientBrush)),
            pointProvider =
            pointSize?.let {
                LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(
                        rememberShapeComponent(fill = Fill(lineColor), shape = CircleShape),
                        size = it.dp,
                    ),
                )
            },
            stroke = LineCartesianLayer.LineStroke.Continuous(lineWidth.dp),
        )
    }

    /**
     * Creates a bold line suitable for highlighting primary metrics.
     *
     * @param lineColor The color of the line
     * @param pointSize Size of point markers (in dp). If null, no point markers are shown.
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createBoldLine(lineColor: Color, pointSize: Float? = LARGE_POINT_SIZE_DP): LineCartesianLayer.Line =
        createStyledLine(lineColor = lineColor, pointSize = pointSize, lineWidth = THICK_LINE_WIDTH_DP)

    /**
     * Creates a subtle line suitable for secondary metrics.
     *
     * @param lineColor The color of the line
     * @param pointSize Size of point markers (in dp). If null, no point markers are shown.
     * @return Configured [LineCartesianLayer.Line]
     */
    @Composable
    fun createSubtleLine(lineColor: Color, pointSize: Float? = SMALL_POINT_SIZE_DP): LineCartesianLayer.Line =
        createStyledLine(lineColor = lineColor, pointSize = pointSize, lineWidth = THIN_LINE_WIDTH_DP)

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
                        // Force alpha to 1f so text is readable even if the line is transparent/subtle
                        val color = point.color.copy(alpha = .8f)
                        val text = format(point.entry.y, color)
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text) }
                    }
                }
            }
        }
    }

    /**
     * Creates a standard [com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis.ItemPlacer] with optimized
     * spacing.
     */
    fun rememberItemPlacer(
        spacing: Int = 50,
    ): com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis.ItemPlacer =
        com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis.ItemPlacer.aligned(
            spacing = { spacing },
            addExtremeLabelPadding = true,
        )

    /**
     * Creates and remembers a [com.patrykandpatrick.vico.compose.common.component.TextComponent] styled for axis
     * labels.
     */
    @Composable
    fun rememberAxisLabel(color: Color = MaterialTheme.colorScheme.onSurfaceVariant): TextComponent =
        rememberTextComponent(style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium))
}
