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
package org.meshtastic.core.ui.label

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.meshtastic.core.ble.niimbot.LabelBitmap
import kotlin.math.round

/** One pixel per printer dot, whatever the screen's density. */
private val DotDensity = Density(1f)

/** Half the cap height of a bold sans face, in ems: where the middle of a line of capitals sits above its baseline. */
private const val HALF_CAP_HEIGHT_EMS = 0.36f

/** Black kept on each side of the band's text. */
private const val BAND_SIDE_DOTS = 4

/** Anything darker than mid gray is ink: the printer can't print gray, and dithering it makes noise. */
private const val INK_BELOW = 128
private const val RED_WEIGHT = 299
private const val GREEN_WEIGHT = 587
private const val BLUE_WEIGHT = 114
private const val WEIGHT_TOTAL = 1000
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF

/** The M-PWRD mark's two Ms, traced from M-PWRD_BW_Border.svg in meshtastic/design, as labeltastic draws them. */
private const val MARK_LEFT = 0.27480f
private const val MARK_TOP = 0.20476f
private val MARK_SLASH = listOf(0.38985f to 1.11826f, 0.27480f to 1.03796f, 0.85635f to 0.20476f, 0.97139f to 0.28505f)
private val MARK_CHEVRON =
    listOf(
        1.49623f to 0.25395f,
        2.04204f to 1.03727f,
        1.91897f to 1.12302f,
        1.42250f to 0.41052f,
        0.92756f to 1.12419f,
        0.80430f to 1.03871f,
        1.34843f to 0.25412f,
    )

/** A label ready to show and to print. */
class RenderedLabel(val preview: ImageBitmap, val bitmap: LabelBitmap)

/**
 * Draws a [LabelLayout] at one pixel per printer dot and thresholds it to one bit. Text is bold in [textFont]; emoji
 * come from [emojiFont], a one-color face, because color emoji threshold to blobs.
 */
class LabelRasterizer(
    private val textMeasurer: TextMeasurer,
    private val emojiFont: FontFamily,
    private val textFont: FontFamily = FontFamily.Default,
) : LabelTextMeasurer {

    override fun width(text: String, size: Int, emoji: Boolean): Float =
        measure(text, size, emoji, Color.Black).let { it.getLineRight(0) - it.getLineLeft(0) }

    /** Null when the layout is taller than the head. */
    fun render(layout: LabelLayout): RenderedLabel? {
        val ink = inkOf(draw(layout))
        val bitmap =
            LabelBitmap.fromReadingOrientation(layout.width, layout.height, LABEL_HEAD_DOTS) { x, y ->
                ink[y * layout.width + x]
            }
        return bitmap?.let { RenderedLabel(preview(layout.width, layout.height, ink), it) }
    }

    private fun measure(text: String, size: Int, emoji: Boolean, color: Color): TextLayoutResult = textMeasurer.measure(
        text = text,
        style =
        TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = if (emoji) emojiFont else textFont,
            fontWeight = if (emoji) FontWeight.Normal else FontWeight.Bold,
        ),
        softWrap = false,
        maxLines = 1,
        density = DotDensity,
    )

    private fun draw(layout: LabelLayout): ImageBitmap {
        val image = ImageBitmap(layout.width, layout.height)
        onto(image) {
            drawRect(Color.White)
            layout.ops.filterIsInstance<LabelOp.Qr>().forEach { drawQr(it) }
            // Whatever a font does, text stays inside the margins of 12 mm stock.
            clipRect(
                left = LABEL_END_DOTS.toFloat(),
                top = LABEL_EDGE_DOTS.toFloat(),
                right = (layout.width - LABEL_END_DOTS).toFloat(),
                bottom = (layout.height - LABEL_EDGE_DOTS).toFloat(),
            ) {
                for (op in layout.ops) {
                    when (op) {
                        is LabelOp.Qr -> Unit
                        is LabelOp.Band -> drawBand(op)
                        is LabelOp.Text -> drawRuns(op)
                        is LabelOp.Mark -> drawMark(op)
                    }
                }
            }
        }
        return image
    }

    private fun onto(image: ImageBitmap, block: DrawScope.() -> Unit) {
        CanvasDrawScope()
            .draw(
                density = DotDensity,
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(image),
                size = Size(image.width.toFloat(), image.height.toFloat()),
                block = block,
            )
    }

    private fun DrawScope.drawQr(op: LabelOp.Qr) {
        val dots = op.dotsPerModule.toFloat()
        for (row in 0 until op.modules.size) {
            for (column in 0 until op.modules.size) {
                if (op.modules.isDark(row, column)) {
                    drawRect(Color.Black, Offset(op.x + column * dots, op.y + row * dots), Size(dots, dots))
                }
            }
        }
    }

    /** Runs drawn one after another, each with its vertical middle on the line, as PIL's "lm" anchor does. */
    private fun DrawScope.drawRuns(op: LabelOp.Text) {
        var x = op.x.toFloat()
        for (run in labelRuns(op.text)) {
            val measured = measure(run.text, op.size, run.emoji, Color.Black)
            drawText(measured, topLeft = Offset(x, op.yCenter - measured.size.height / 2f))
            x += measured.getLineRight(0) - measured.getLineLeft(0)
        }
    }

    private fun DrawScope.drawBand(op: LabelOp.Band) {
        drawRect(Color.Black, Offset(op.x.toFloat(), op.y.toFloat()), Size(op.width.toFloat(), op.height.toFloat()))
        val measured = measure(op.text, op.size, emoji = false, color = Color.White)
        val textWidth = measured.getLineRight(0) - measured.getLineLeft(0)
        val squeeze = minOf(1f, (op.width - 2 * BAND_SIDE_DOTS) / maxOf(1f, textWidth))
        val left = op.x + (op.width - textWidth * squeeze) / 2f
        val top = op.y + op.height / 2f - (measured.firstBaseline - HALF_CAP_HEIGHT_EMS * op.size)
        withTransform({
            translate(left, top)
            scale(squeeze, 1f, pivot = Offset.Zero)
        }) {
            drawText(measured, topLeft = Offset.Zero)
        }
    }

    private fun DrawScope.drawMark(op: LabelOp.Mark) {
        val scale = op.height / MARK_UNITS_TALL
        val left = op.right - round(MARK_UNITS_WIDE * scale)
        val top = (op.bottom - op.height).toFloat()
        for (polygon in listOf(MARK_SLASH, MARK_CHEVRON)) {
            val path = Path()
            polygon.forEachIndexed { index, (px, py) ->
                val x = left + (px - MARK_LEFT) * scale
                val y = top + (py - MARK_TOP) * scale
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color.Black)
        }
    }

    private fun inkOf(image: ImageBitmap): BooleanArray {
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return BooleanArray(pixels.size) { index ->
            val pixel = pixels[index]
            val luminance =
                (pixel shr RED_SHIFT and CHANNEL_MASK) * RED_WEIGHT +
                    (pixel shr GREEN_SHIFT and CHANNEL_MASK) * GREEN_WEIGHT +
                    (pixel and CHANNEL_MASK) * BLUE_WEIGHT
            luminance < INK_BELOW * WEIGHT_TOTAL
        }
    }

    /** What the printer gets, dot for dot, rather than the smoothed drawing it was thresholded from. */
    private fun preview(width: Int, height: Int, ink: BooleanArray): ImageBitmap {
        val image = ImageBitmap(width, height)
        onto(image) {
            drawRect(Color.White)
            for (y in 0 until height) {
                var x = 0
                while (x < width) {
                    val start = x
                    while (x < width && ink[y * width + x]) x++
                    if (x > start) {
                        drawRect(Color.Black, Offset(start.toFloat(), y.toFloat()), Size((x - start).toFloat(), 1f))
                    }
                    x++
                }
            }
        }
        return image
    }
}
