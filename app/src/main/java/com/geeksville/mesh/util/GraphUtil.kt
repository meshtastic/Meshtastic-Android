package com.geeksville.mesh.util

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawContext
import com.geeksville.mesh.TelemetryProtos.Telemetry
import java.util.concurrent.TimeUnit

private const val TIME_SEPARATION_THRESHOLD = 2L

object GraphUtil {

    /**
     * @param value Must be zero-scaled before passing.
     * @param divisor The range for the data set.
     */
    fun plotPoint(
        drawContext: DrawContext,
        color: Color,
        radius: Float,
        x: Float,
        value: Float,
        divisor: Float,
    ) {
        val height = drawContext.size.height
        val ratio = value / divisor
        val y = height - (ratio * height)
        drawContext.canvas.drawCircle(
            center = Offset(x, y),
            radius = radius,
            paint = androidx.compose.ui.graphics.Paint().apply { this.color = color }
        )
    }

    /**
     * Creates a [Path] that could be used to draw a line from the `index` to the end of `telemetries`
     * or the last point before a time separation between [Telemetry]s.
     *
     * @param telemetries data used to create the [Path]
     * @param index current place in the [List]
     * @param path [Path] that will be used to draw
     * @param timeRange The time range for the data set
     * @param width of the [DrawContext]
     * @param calculateY (`index`) -> `y` coordinate
     */
    fun createPath(
        telemetries: List<Telemetry>,
        index: Int,
        path: Path,
        oldestTime: Int,
        timeRange: Int,
        width: Float,
        calculateY: (Int) -> Float
    ): Int {
        var i = index
        var isNewLine = true
        with (path) {
            while (i < telemetries.size) {
                val telemetry = telemetries[i]
                val nextTelemetry = telemetries.getOrNull(i + 1) ?: telemetries.last()

                /* Check to see if we have a significant time break between telemetries. */
                if (nextTelemetry.time - telemetry.time > TimeUnit.HOURS.toSeconds(TIME_SEPARATION_THRESHOLD)) {
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
}
