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
package org.meshtastic.feature.firmware.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private const val ANTENNA_DP = 98f
private const val PAIRED_ANTENNA_DP = 84f
private const val PAIR_DISH_OFFSET_DP = -25f
private const val PAIR_ANTENNA_OFFSET_DP = 18f
private const val SIGNAL_PULSE_RATE = 4.0
private const val DISH_POST_DP = 34f
private const val DISH_TILT_DEGREES = -35f

// The UFO hovers relative to Chirpy's drawn size: clear of his antenna when he ducks, level with his head when he
// stands.
private const val UFO_HEIGHT_OF_SPRITE = 0.73f
private const val UFO_HOVER_RATE = 3.0
private const val UFO_HOVER_DP = 2f
private const val UFO_LIGHT_RATE = 6.0
private const val UFO_LIGHTS = 3

/**
 * The obstacles: an antenna mast, a satellite dish beside a shorter mast, and a UFO. Their solid parts are paths built
 * here once, in pixels with the obstacle's foot at the origin, and drawn at the obstacle's position each frame.
 */
@Suppress("MagicNumber")
internal class ChirpyHopProps(private val groundY: Float, spriteHeight: Float, density: Density) : Density by density {
    private val ufoHeight = spriteHeight * UFO_HEIGHT_OF_SPRITE
    private val antenna = antennaBody(ANTENNA_DP)
    private val pairedAntenna = antennaBody(PAIRED_ANTENNA_DP)
    private val dishBase =
        footPath(halfWidth = 12f, topHalfWidth = 5f, height = 9f).apply {
            addRoundRect(roundRect(centerY = DISH_POST_DP / 2, width = 6f, height = DISH_POST_DP, corner = 3f))
        }
    private val wire = Stroke(width = 2 * this.density, cap = StrokeCap.Round)
    private val ufoGlint = Stroke(width = 2.5f * this.density, cap = StrokeCap.Round)

    fun DrawScope.drawObstacle(kind: ChirpyObstacleKind, xFraction: Double, sceneSeconds: Double) {
        val x = (size.width * xFraction).toFloat()
        val pulse = ((sin(sceneSeconds * SIGNAL_PULSE_RATE) + 1) / 2).toFloat()
        when (kind) {
            ChirpyObstacleKind.Antenna -> drawAntenna(x, antenna, ANTENNA_DP, pulse)

            ChirpyObstacleKind.AntennaPair -> {
                drawDish(x + PAIR_DISH_OFFSET_DP.dp.toPx())
                drawAntenna(x + PAIR_ANTENNA_OFFSET_DP.dp.toPx(), pairedAntenna, PAIRED_ANTENNA_DP, 1 - pulse)
            }

            ChirpyObstacleKind.Ufo -> {
                val hover = sin(sceneSeconds * UFO_HOVER_RATE).toFloat() * UFO_HOVER_DP.dp.toPx()
                drawUfo(Offset(x, groundY - ufoHeight + hover), litLight = (sceneSeconds * UFO_LIGHT_RATE).toInt())
            }
        }
    }

    /** A mesh node's antenna: a guyed mast with yagi elements, sending out signal arcs that fade with [pulse]. */
    private fun DrawScope.drawAntenna(x: Float, body: Path, heightDp: Float, pulse: Float) {
        val guyTop = Offset(x, groundY - (heightDp * 0.55f).dp.toPx())
        drawLine(ChirpyInk, guyTop, Offset(x - 20.dp.toPx(), groundY), wire.width, StrokeCap.Round)
        drawLine(ChirpyInk, guyTop, Offset(x + 20.dp.toPx(), groundY), wire.width, StrokeCap.Round)
        translate(x, groundY) { drawPath(body, ChirpyInk) }

        val tip = Offset(x, groundY - (heightDp + 4).dp.toPx())
        drawCircle(ChirpyInk, radius = 4.dp.toPx(), center = tip)
        val signal = ChirpyInk.copy(alpha = 0.25f + 0.6f * pulse)
        for (radiusDp in 10..17 step 7) {
            val r = radiusDp.dp.toPx()
            val topLeft = Offset(tip.x - r, tip.y - r)
            val arc = Size(2 * r, 2 * r)
            drawArc(signal, -40f, 80f, useCenter = false, topLeft = topLeft, size = arc, style = wire)
            drawArc(signal, 140f, 80f, useCenter = false, topLeft = topLeft, size = arc, style = wire)
        }
    }

    /** A satellite dish on a short post, tilted to look up and back along the run, with its feed held over the bowl. */
    private fun DrawScope.drawDish(x: Float) {
        translate(x, groundY) { drawPath(dishBase, ChirpyInk) }
        val pivot = Offset(x, groundY - DISH_POST_DP.dp.toPx())
        rotate(DISH_TILT_DEGREES, pivot = pivot) {
            translate(pivot.x, pivot.y) {
                scale(density, pivot = Offset.Zero) {
                    drawArc(ChirpyInk, 0f, 180f, useCenter = true, topLeft = Offset(-22f, -19f), size = Size(44f, 26f))
                    drawLine(ChirpyInk, Offset(0f, -2f), Offset(0f, 6f), strokeWidth = 5f)
                    val feed = Offset(0f, -24f)
                    drawLine(ChirpyInk, Offset(-20f, -6f), feed, strokeWidth = 1.5f)
                    drawLine(ChirpyInk, Offset(20f, -6f), feed, strokeWidth = 1.5f)
                    drawLine(ChirpyInk, Offset(0f, -6f), feed, strokeWidth = 2f)
                    drawRoundRect(ChirpyInk, Offset(-3.5f, -30f), Size(7f, 7f), CornerRadius(1.5f))
                }
            }
        }
    }

    /** A flying saucer: glass dome over a disc whose rim lights chase round, [litLight] picking the bright one. */
    private fun DrawScope.drawUfo(center: Offset, litLight: Int) {
        val domeRadius = 15.dp.toPx()
        val domeCenterY = center.y - 4.dp.toPx()
        drawArc(
            color = ChirpyInk,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(center.x - domeRadius, domeCenterY - domeRadius),
            size = Size(2 * domeRadius, 2 * domeRadius),
        )
        val glint = 9.dp.toPx()
        drawArc(
            color = ChirpyPaper,
            startAngle = 200f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = Offset(center.x - glint, domeCenterY - glint),
            size = Size(2 * glint, 2 * glint),
            style = ufoGlint,
        )
        drawOval(ChirpyInk, Offset(center.x - 15.dp.toPx(), center.y + 2.dp.toPx()), Size(30.dp.toPx(), 10.dp.toPx()))
        drawOval(ChirpyInk, Offset(center.x - 38.dp.toPx(), center.y - 8.dp.toPx()), Size(76.dp.toPx(), 18.dp.toPx()))
        for (light in 0 until UFO_LIGHTS) {
            val lit = light == litLight % UFO_LIGHTS
            drawCircle(
                color = ChirpyPaper,
                radius = (if (lit) 3.5f else 2f).dp.toPx(),
                center = Offset(center.x + ((light - 1) * 22).dp.toPx(), center.y + 1.dp.toPx()),
            )
        }
    }

    /** A mast [heightDp] tall on a splayed foot, with three yagi elements near the top. */
    private fun antennaBody(heightDp: Float): Path = footPath(halfWidth = 14f, topHalfWidth = 6f, height = 10f).apply {
        addRoundRect(roundRect(centerY = heightDp / 2, width = 6f, height = heightDp, corner = 3f))
        for ((index, width) in listOf(30f, 24f, 18f).withIndex()) {
            addRoundRect(roundRect(centerY = heightDp - 14 - index * 12, width = width, height = 4f, corner = 2f))
        }
    }

    /** A trapezoid foot on the ground, in dp with y up from the base. */
    private fun footPath(halfWidth: Float, topHalfWidth: Float, height: Float): Path = Path().apply {
        moveTo(-halfWidth.dp.toPx(), 0f)
        lineTo(halfWidth.dp.toPx(), 0f)
        lineTo(topHalfWidth.dp.toPx(), -height.dp.toPx())
        lineTo(-topHalfWidth.dp.toPx(), -height.dp.toPx())
        close()
    }

    /** A rounded rect centred on the mast, [centerY] dp above the base. */
    private fun roundRect(centerY: Float, width: Float, height: Float, corner: Float): RoundRect = RoundRect(
        left = (-width / 2).dp.toPx(),
        top = -(centerY + height / 2).dp.toPx(),
        right = (width / 2).dp.toPx(),
        bottom = -(centerY - height / 2).dp.toPx(),
        cornerRadius = CornerRadius(corner.dp.toPx()),
    )
}
