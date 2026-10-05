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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.ceil
import kotlin.math.sin

// Background layers, far to near. Each scrolls at its own fraction of the ground's speed and stays lighter than the
// obstacles so nothing behind the run reads as something to jump.
private const val MOUNTAIN_PARALLAX = 0.06f
private const val MOUNTAIN_TILE_WIDTHS = 1.7f
private const val HILL_PARALLAX = 0.25f
private const val HILL_TILE_WIDTHS = 1.25f
private const val HILL_VALLEY_FRACTION = 0.018f
private const val CLOUD_WRAP_DP = 60f
private const val NODE_MAST_DP = 16f
private const val NODE_BLINK_RATE = 2.5
private const val PACKET_RATE = 0.45

// The paraglider flies its own course across a stretch of sky wider than the screen, so it is only sometimes in view.
private const val GLIDER_PARALLAX = 0.08f
private const val GLIDER_DRIFT_WIDTHS_PER_SECOND = 0.025
private const val GLIDER_SKY_WIDTHS = 2.6f
private const val GLIDER_HEIGHT_FRACTION = 0.64f
private const val GLIDER_WRAP_DP = 80f
private const val GLIDER_BOB_DP = 6f
private const val GLIDER_BOB_RATE = 0.8
private const val GLIDER_SWAY_RATE = 0.6
private const val GLIDER_SWAY_DEGREES = 4f

/** Banked slightly into its turn, nose down toward the direction of flight. */
private const val GLIDER_BANK_DEGREES = -6f

private val MountainFill = Color(0xFFEEEEEE)
private val MountainLine = Color(0x40A0A0A0)
private val HillFill = Color(0xFFE8E8E8)
private val HillLine = Color(0x66A0A0A0)
private val NodeColor = Color(0x99909090)
private val CloudColor = Color(0xA6A8A8A8)
private val GliderColor = Color(0xB3808080)

private class Cloud(val xFraction: Float, val heightFraction: Float, val scale: Float, val parallax: Float)

private val clouds =
    listOf(
        Cloud(0.12f, 0.8f, 0.5f, 0.01f),
        Cloud(0.3f, 0.72f, 0.82f, 0.03f),
        Cloud(0.78f, 0.6f, 0.62f, 0.045f),
    )

/** Peaks of the far range as (fraction of the tile's width, fraction of the field's height above the ground). */
private val mountainPeaks =
    listOf(
        0f to 0f,
        0.07f to 0.16f,
        0.13f to 0.09f,
        0.22f to 0.27f,
        0.31f to 0.12f,
        0.4f to 0.21f,
        0.48f to 0.07f,
        0.58f to 0.23f,
        0.67f to 0.13f,
        0.77f to 0.3f,
        0.87f to 0.11f,
        0.94f to 0.17f,
        1f to 0f,
    )

/** Hill crests as (fraction of the tile's width, crest height as a fraction of the field's height). */
private val hillCrests = listOf(0.1f to 0.055f, 0.32f to 0.075f, 0.55f to 0.05f, 0.78f to 0.07f)

internal fun DrawScope.drawChirpyBackdrop(groundY: Float, shiftPx: Float, sceneSeconds: Double) {
    drawClouds(shiftPx)
    drawMountains(groundY, shiftPx * MOUNTAIN_PARALLAX)
    drawParaglider(shiftPx, sceneSeconds)
    drawHills(groundY, shiftPx * HILL_PARALLAX, sceneSeconds)
}

/** Calls [draw] with the left edge of every repeat of a [tileWidth]-wide layer that is on screen. */
private inline fun DrawScope.forEachTile(tileWidth: Float, shift: Float, draw: (left: Float) -> Unit) {
    val first = wrap(-shift, -tileWidth, tileWidth)
    val count = ceil((size.width - first) / tileWidth).toInt()
    for (index in 0..count) draw(first + index * tileWidth)
}

private fun DrawScope.drawClouds(shiftPx: Float) {
    val margin = CLOUD_WRAP_DP * density
    clouds.forEach { cloud ->
        val x = wrap(size.width * cloud.xFraction - shiftPx * cloud.parallax, -margin, size.width + 2 * margin)
        val y = size.height * (1 - cloud.heightFraction)
        translate(x, y) { scale(cloud.scale * density, pivot = Offset.Zero) { drawCloudOutline() } }
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawCloudOutline() {
    val path =
        Path().apply {
            moveTo(-38f, 0f)
            cubicTo(-38f, -17f, -23f, -19f, -14f, -2f)
            cubicTo(-8f, -25f, 7f, -25f, 10f, -8f)
            cubicTo(23f, -17f, 38f, -12f, 38f, 0f)
            close()
        }
    drawPath(path, CloudColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
}

private fun DrawScope.drawParaglider(shiftPx: Float, sceneSeconds: Double) {
    val margin = GLIDER_WRAP_DP * density
    val drift = (sceneSeconds * GLIDER_DRIFT_WIDTHS_PER_SECOND).toFloat() * size.width
    val x = wrap(size.width - shiftPx * GLIDER_PARALLAX - drift, -margin, size.width * GLIDER_SKY_WIDTHS + 2 * margin)
    if (x > size.width + margin) return
    val bob = sin(sceneSeconds * GLIDER_BOB_RATE).toFloat() * GLIDER_BOB_DP * density
    val y = size.height * (1 - GLIDER_HEIGHT_FRACTION) + bob
    val sway = GLIDER_BANK_DEGREES + sin(sceneSeconds * GLIDER_SWAY_RATE).toFloat() * GLIDER_SWAY_DEGREES
    translate(x, y) {
        rotate(sway, pivot = Offset.Zero) { scale(density, pivot = Offset.Zero) { drawParagliderShape() } }
    }
}

/**
 * A paraglider in dp around its canopy's centre, flying left: a long, thin, cambered wing with its cell ribs, the lines
 * fanning down to a pilot sitting in the harness with legs out in front.
 */
@Suppress("MagicNumber")
private fun DrawScope.drawParagliderShape() {
    val wing =
        Path().apply {
            moveTo(-40f, 6f)
            cubicTo(-30f, -12f, 30f, -12f, 40f, 6f)
            cubicTo(30f, -4f, -30f, -4f, -40f, 6f)
            close()
        }
    drawPath(wing, GliderColor)
    val rib = Stroke(width = 1f)
    for (x in listOf(-26f, -13f, 0f, 13f, 26f)) {
        drawLine(ChirpyPaper, Offset(x, -7.5f), Offset(x, -3.5f), strokeWidth = rib.width)
    }
    val harness = Offset(2f, 38f)
    listOf(-38f to 5f, -24f to -2f, -9f to -4f, 9f to -4f, 24f to -2f, 38f to 5f).forEach { (x, y) ->
        drawLine(GliderColor, Offset(x, y), harness, strokeWidth = 0.6f)
    }
    drawCircle(GliderColor, radius = 3f, center = Offset(0f, 36f))
    drawRoundRect(GliderColor, topLeft = Offset(-1f, 38.5f), size = Size(7f, 6f), cornerRadius = CornerRadius(2.5f))
    drawLine(GliderColor, Offset(1f, 43f), Offset(-7f, 45f), strokeWidth = 2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawMountains(groundY: Float, shift: Float) {
    val tileWidth = size.width * MOUNTAIN_TILE_WIDTHS
    forEachTile(tileWidth, shift) { left ->
        val range =
            Path().apply {
                mountainPeaks.forEachIndexed { index, (x, height) ->
                    val px = left + x * tileWidth
                    val py = groundY - height * size.height
                    if (index == 0) moveTo(px, py) else lineTo(px, py)
                }
            }
        drawPath(range, MountainFill)
        drawPath(range, MountainLine, style = Stroke(width = 2 * density, cap = StrokeCap.Round))
    }
}

/** Rolling hills with a small mesh node on each crest, linked to its neighbours with packets hopping between them. */
@Suppress("MagicNumber")
private fun DrawScope.drawHills(groundY: Float, shift: Float, sceneSeconds: Double) {
    val tileWidth = size.width * HILL_TILE_WIDTHS
    val valley = groundY - HILL_VALLEY_FRACTION * size.height
    forEachTile(tileWidth, shift) { left ->
        val crests = hillCrests.map { (x, height) -> Offset(left + x * tileWidth, groundY - height * size.height) }
        // Only the ridgeline is outlined: stroking the filled shape would draw a seam where one tile meets the next.
        val ridge =
            Path().apply {
                moveTo(left, valley)
                crests.forEachIndexed { index, crest ->
                    val to = if (index == crests.lastIndex) left + tileWidth else (crest.x + crests[index + 1].x) / 2
                    // A quadratic whose control point sits twice as far above the valley peaks exactly at the crest.
                    quadraticTo(crest.x, 2 * crest.y - valley, to, valley)
                }
            }
        val hills =
            Path().apply {
                addPath(ridge)
                lineTo(left + tileWidth, groundY)
                lineTo(left, groundY)
                close()
            }
        drawPath(hills, HillFill)
        drawPath(ridge, HillLine, style = Stroke(width = 2 * density, cap = StrokeCap.Round))

        val tips =
            crests.map { Offset(it.x, it.y - NODE_MAST_DP * density) } +
                Offset(crests.first().x + tileWidth, crests.first().y - NODE_MAST_DP * density)
        val link = Stroke(width = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
        tips.zipWithNext().forEachIndexed { index, (a, b) ->
            drawLine(NodeColor, a, b, link.width, pathEffect = link.pathEffect)
            val travel = ((sceneSeconds * PACKET_RATE + index * 0.37) % 1.0).toFloat()
            drawCircle(NodeColor, radius = 2.5f * density, center = a + (b - a) * travel)
        }
        crests.forEachIndexed { index, crest -> drawMeshNode(crest, sceneSeconds + index * 0.6) }
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawMeshNode(base: Offset, seconds: Double) {
    val tip = Offset(base.x, base.y - NODE_MAST_DP * density)
    drawLine(NodeColor, base, tip, 2 * density, StrokeCap.Round)
    drawLine(NodeColor, tip + Offset(-5 * density, 5 * density), tip + Offset(5 * density, 5 * density), 2 * density)
    val blink = ((sin(seconds * NODE_BLINK_RATE) + 1) / 2).toFloat()
    drawCircle(NodeColor.copy(alpha = NodeColor.alpha * (0.4f + 0.6f * blink)), radius = 3 * density, center = tip)
}
