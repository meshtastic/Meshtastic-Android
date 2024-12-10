package com.geeksville.mesh.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawContext

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

    // TODO implement drawing line
}
