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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
private const val NODE_BLINK_STAGGER = 0.6
private const val PACKET_RATE = 0.45
private const val PACKET_STAGGER = 0.37

// The hang glider flies its own course across a stretch of sky wider than the screen, so it is only sometimes in view.
// It is drawn small and pale, behind the clouds, so it reads as far off.
private const val GLIDER_PARALLAX = 0.04f
private const val GLIDER_DRIFT_WIDTHS_PER_SECOND = 0.015
private const val GLIDER_SKY_WIDTHS = 2.2f
private const val GLIDER_HEIGHT_FRACTION = 0.72f
private const val GLIDER_SCALE = 0.55f
private const val GLIDER_WRAP_DP = 80f
private const val GLIDER_BOB_DP = 3f
private const val GLIDER_BOB_RATE = 0.8
private const val GLIDER_SWAY_RATE = 0.6
private const val GLIDER_SWAY_DEGREES = 4f
private const val GLIDER_BANK_DEGREES = -4f
private const val GLIDER_SAIL_ALPHA = 0.55f

private val MountainFill = Color(0xFFEEEEEE)
private val MountainLine = Color(0x40A0A0A0)
private val HillFill = Color(0xFFE8E8E8)
private val HillLine = Color(0x66A0A0A0)
private val NodeColor = Color(0x99909090)
private val CloudColor = Color(0xA6A8A8A8)
private val GliderColor = Color(0x66909090)

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

/**
 * The sky and the scenery behind the run: a hang glider and clouds, a far mountain range, and rolling hills with mesh
 * nodes passing packets. Each repeating layer is one tile's path, built here once and drawn at each tile's offset.
 */
@Suppress("MagicNumber")
internal class ChirpyHopBackdrop(private val size: Size, groundY: Float, density: Density) : Density by density {
    private val mountainTileWidth = size.width * MOUNTAIN_TILE_WIDTHS
    private val hillTileWidth = size.width * HILL_TILE_WIDTHS
    private val nodeMast = NODE_MAST_DP * this.density

    private val mountainRange =
        Path().apply {
            mountainPeaks.forEachIndexed { index, (x, height) ->
                val px = x * mountainTileWidth
                val py = groundY - height * size.height
                if (index == 0) moveTo(px, py) else lineTo(px, py)
            }
        }

    /** Crest positions within a hill tile, the nodes' bases. */
    private val crests = hillCrests.map { (x, height) -> Offset(x * hillTileWidth, groundY - height * size.height) }

    // Only the ridgeline is outlined: stroking the filled shape would draw a seam where one tile meets the next.
    private val hillRidge =
        Path().apply {
            val valley = groundY - HILL_VALLEY_FRACTION * size.height
            moveTo(0f, valley)
            crests.forEachIndexed { index, crest ->
                val to = if (index == crests.lastIndex) hillTileWidth else (crest.x + crests[index + 1].x) / 2
                // A quadratic whose control point sits twice as far above the valley peaks exactly at the crest.
                quadraticTo(crest.x, 2 * crest.y - valley, to, valley)
            }
        }
    private val hillFill =
        Path().apply {
            addPath(hillRidge)
            lineTo(hillTileWidth, groundY)
            lineTo(0f, groundY)
            close()
        }

    /** The mast tips the mesh links run between, ending with the next tile's first node so links cross the seam. */
    private val nodeTips =
        crests.map { Offset(it.x, it.y - nodeMast) } +
            Offset(crests.first().x + hillTileWidth, crests.first().y - nodeMast)

    private val outline = Stroke(width = 2 * this.density, cap = StrokeCap.Round)
    private val meshLink = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))

    /** The cloud and hang glider are drawn in dp around their own origin, scaled into place. */
    private val cloudOutline =
        Path().apply {
            moveTo(-38f, 0f)
            cubicTo(-38f, -17f, -23f, -19f, -14f, -2f)
            cubicTo(-8f, -25f, 7f, -25f, 10f, -8f)
            cubicTo(23f, -17f, 38f, -12f, 38f, 0f)
            close()
        }
    private val cloudStroke = Stroke(width = 3f, cap = StrokeCap.Round)
    private val gliderSail =
        Path().apply {
            moveTo(-36f, 0f)
            lineTo(24f, -12f)
            lineTo(20f, 0f)
            lineTo(30f, 10f)
            close()
        }
    private val gliderSailEdge = Stroke(width = 1.2f, join = StrokeJoin.Round)

    fun DrawScope.drawBackdrop(shiftPx: Float, sceneSeconds: Double) {
        drawHangGlider(shiftPx, sceneSeconds)
        drawClouds(shiftPx)
        forEachTile(mountainTileWidth, shiftPx * MOUNTAIN_PARALLAX) { left ->
            translate(left = left) {
                drawPath(mountainRange, MountainFill)
                drawPath(mountainRange, MountainLine, style = outline)
            }
        }
        forEachTile(hillTileWidth, shiftPx * HILL_PARALLAX) { left ->
            translate(left = left) { drawHills(sceneSeconds) }
        }
    }

    /** Calls [draw] with the left edge of every repeat of a [tileWidth]-wide layer that is on screen. */
    private inline fun forEachTile(tileWidth: Float, shift: Float, draw: (left: Float) -> Unit) {
        val first = wrap(-shift, -tileWidth, tileWidth)
        val count = ceil((size.width - first) / tileWidth).toInt()
        for (index in 0..count) draw(first + index * tileWidth)
    }

    private fun DrawScope.drawClouds(shiftPx: Float) {
        val margin = CLOUD_WRAP_DP.dp.toPx()
        clouds.forEach { cloud ->
            val x = wrap(size.width * cloud.xFraction - shiftPx * cloud.parallax, -margin, size.width + 2 * margin)
            val y = size.height * (1 - cloud.heightFraction)
            translate(x, y) {
                scale(cloud.scale * density, pivot = Offset.Zero) {
                    drawPath(cloudOutline, CloudColor, style = cloudStroke)
                }
            }
        }
    }

    /**
     * Rolling hills with a small mesh node on each crest, linked to its neighbours with packets hopping between them.
     */
    private fun DrawScope.drawHills(sceneSeconds: Double) {
        drawPath(hillFill, HillFill)
        drawPath(hillRidge, HillLine, style = outline)
        for (index in 0 until nodeTips.lastIndex) {
            val from = nodeTips[index]
            val to = nodeTips[index + 1]
            drawLine(NodeColor, from, to, strokeWidth = 1.5f.dp.toPx(), pathEffect = meshLink)
            val travel = ((sceneSeconds * PACKET_RATE + index * PACKET_STAGGER) % 1.0).toFloat()
            drawCircle(NodeColor, radius = 2.5f.dp.toPx(), center = from + (to - from) * travel)
        }
        crests.forEachIndexed { index, crest -> drawMeshNode(crest, sceneSeconds + index * NODE_BLINK_STAGGER) }
    }

    private fun DrawScope.drawMeshNode(base: Offset, seconds: Double) {
        val tip = Offset(base.x, base.y - nodeMast)
        val arm = 5.dp.toPx()
        drawLine(NodeColor, base, tip, 2.dp.toPx(), StrokeCap.Round)
        drawLine(NodeColor, Offset(tip.x - arm, tip.y + arm), Offset(tip.x + arm, tip.y + arm), 2.dp.toPx())
        val blink = ((sin(seconds * NODE_BLINK_RATE) + 1) / 2).toFloat()
        drawCircle(NodeColor.copy(alpha = NodeColor.alpha * (0.4f + 0.6f * blink)), radius = 3.dp.toPx(), center = tip)
    }

    private fun DrawScope.drawHangGlider(shiftPx: Float, sceneSeconds: Double) {
        val margin = GLIDER_WRAP_DP.dp.toPx()
        val drift = (sceneSeconds * GLIDER_DRIFT_WIDTHS_PER_SECOND).toFloat() * size.width
        val span = size.width * GLIDER_SKY_WIDTHS + 2 * margin
        val x = wrap(size.width - shiftPx * GLIDER_PARALLAX - drift, -margin, span)
        if (x > size.width + margin) return
        val bob = sin(sceneSeconds * GLIDER_BOB_RATE).toFloat() * GLIDER_BOB_DP.dp.toPx()
        val y = size.height * (1 - GLIDER_HEIGHT_FRACTION) + bob
        val sway = GLIDER_BANK_DEGREES + sin(sceneSeconds * GLIDER_SWAY_RATE).toFloat() * GLIDER_SWAY_DEGREES
        translate(x, y) {
            rotate(sway, pivot = Offset.Zero) {
                scale(GLIDER_SCALE * density, pivot = Offset.Zero) { drawHangGliderShape() }
            }
        }
    }

    /**
     * A hang glider seen three-quarters on in dp around its keel, flying left: a delta sail with a notched trailing
     * edge, the keel and kingpost, and the pilot lying prone in the triangular control frame below.
     */
    private fun DrawScope.drawHangGliderShape() {
        val nose = Offset(-36f, 0f)
        val tail = Offset(20f, 0f)
        drawPath(gliderSail, GliderColor.copy(alpha = GliderColor.alpha * GLIDER_SAIL_ALPHA))
        drawPath(gliderSail, GliderColor, style = gliderSailEdge)
        drawLine(GliderColor, nose, tail, strokeWidth = 1.2f)

        val kingpostTop = Offset(-4f, -11f)
        drawLine(GliderColor, Offset(-4f, 0f), kingpostTop, strokeWidth = 1.2f)
        drawLine(GliderColor, kingpostTop, nose, strokeWidth = 0.5f)
        drawLine(GliderColor, kingpostTop, tail, strokeWidth = 0.5f)

        val keel = Offset(-4f, 0f)
        val frontOfBar = Offset(-14f, 24f)
        val backOfBar = Offset(4f, 24f)
        drawLine(GliderColor, keel, frontOfBar, strokeWidth = 1.2f)
        drawLine(GliderColor, keel, backOfBar, strokeWidth = 1.2f)
        drawLine(GliderColor, frontOfBar, backOfBar, strokeWidth = 1.5f)

        drawLine(GliderColor, Offset(0f, 0f), Offset(2f, 14f), strokeWidth = 0.8f)
        drawRoundRect(GliderColor, Offset(-8f, 13f), Size(26f, 5.5f), CornerRadius(2.75f))
        drawCircle(GliderColor, radius = 3.5f, center = Offset(-11f, 14.5f))
        drawLine(GliderColor, Offset(-6f, 16f), Offset(-12f, 24f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    }
}
