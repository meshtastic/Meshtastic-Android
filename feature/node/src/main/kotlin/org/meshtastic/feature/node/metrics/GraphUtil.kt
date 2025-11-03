/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.meshtastic.proto.TelemetryProtos.Telemetry

object GraphUtil {

    val RADIUS = Resources.getSystem().displayMetrics.density * 2

    /**
     * @param value Must be zero-scaled before passing.
     * @param divisor The range for the data set.
     */
    fun plotPoint(drawContext: DrawContext, color: Color, x: Float, value: Float, divisor: Float) {
        val height = drawContext.size.height
        val ratio = value / divisor
        val y = height - (ratio * height)
        drawContext.canvas.drawCircle(
            center = Offset(x, y),
            radius = RADIUS,
            paint = androidx.compose.ui.graphics.Paint().apply { this.color = color },
        )
    }

    /**
     * Creates a [Path] that could be used to draw a line from the `index` to the end of `telemetries` or the last point
     * before a time separation between [Telemetry]s.
     *
     * @param telemetries data used to create the [Path]
     * @param index current place in the [List]
     * @param path [Path] that will be used to draw
     * @param timeRange The time range for the data set
     * @param width of the [DrawContext]
     * @param timeThreshold to determine significant breaks in time between [Telemetry]s
     * @param calculateY (`index`) -> `y` coordinate
     * @return the current index after iterating
     */
    fun createPath(
        telemetries: List<Telemetry>,
        index: Int,
        path: Path,
        oldestTime: Int,
        timeRange: Int,
        width: Float,
        timeThreshold: Long,
        calculateY: (Int) -> Float,
    ): Int {
        var i = index
        var isNewLine = true
        with(path) {
            while (i < telemetries.size) {
                val telemetry = telemetries[i]
                val nextTelemetry = telemetries.getOrNull(i + 1) ?: telemetries.last()

                /* Check to see if we have a significant time break between telemetries. */
                if (nextTelemetry.time - telemetry.time > timeThreshold) {
                    i++
                    break
                }

                val x1Ratio = (telemetry.time - oldestTime).toFloat() / timeRange
                val x1 = x1Ratio * width
                val y1 = calculateY(i)

                val x2Ratio = (nextTelemetry.time - oldestTime).toFloat() / timeRange
                val x2 = x2Ratio * width
                val y2 = calculateY(i + 1)

                if (isNewLine || i == 0) {
                    isNewLine = false
                    moveTo(x1, y1)
                }

                quadraticTo(x1, y1, (x1 + x2) / 2f, (y1 + y2) / 2f)
                i++
            }
        }
        return i
    }

    fun DrawScope.drawPathWithGradient(path: Path, color: Color, height: Float, x1: Float, x2: Float) {
        drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        val fillPath =
            android.graphics.Path(path.asAndroidPath()).asComposePath().apply {
                lineTo(x1, height)
                lineTo(x2, height)
                close()
            }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.5f), Color.Transparent), endY = height),
        )
    }
}
