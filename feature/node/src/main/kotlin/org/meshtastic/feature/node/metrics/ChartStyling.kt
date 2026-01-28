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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent

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
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
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
            fill = LineCartesianLayer.LineFill.single(Fill(gradientBrush)),
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
}
