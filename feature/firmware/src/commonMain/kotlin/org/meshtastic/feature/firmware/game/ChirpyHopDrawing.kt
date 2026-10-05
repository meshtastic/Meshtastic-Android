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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.sin

// Scene geometry in dp, mirroring the iOS scene. Shapes are authored with y pointing up from their anchor, the way
// SpriteKit draws them, and flipped as they are placed.
private const val GROUND_FRACTION = 0.18f
private const val JUMP_SCALE = 0.72f
private const val SPRITE_WIDTH_DP = 128f
private const val SPRITE_HEIGHT_DP = 168f
private const val SPRITE_MAX_FIELD_FRACTION = 0.32f
private const val SPRITE_ANCHOR_Y = 0.08f
private const val RUN_CADENCE_BASE = 14.0
private const val RUN_CADENCE_PER_SPEED = 8.0
private const val GROUND_LINE_DP = 3f
private const val MARK_SPACING_DP = 68f
private const val MARK_WRAP_DP = 24f
private const val MIN_MARKS = 6

// The UFO hovers relative to Chirpy's drawn size: clear of his antenna when he ducks, level with his head when he
// stands.
private const val UFO_HEIGHT_OF_SPRITE = 0.73f
private const val UFO_HOVER_RATE = 3.0
private const val UFO_HOVER_DP = 2f
private const val UFO_LIGHT_RATE = 6.0
private const val SIGNAL_PULSE_RATE = 4.0
private const val DISH_TILT_DEGREES = -35f

private val GroundMarkColor = Color(0xB37A7A7A)

internal fun DrawScope.drawChirpyWorld(engine: ChirpyHopEngine, world: ChirpyWorld, sprites: ChirpySprites) {
    drawRect(ChirpyPaper)
    val groundY = size.height * (1 - GROUND_FRACTION)
    val shiftPx = (world.groundShift * size.width).toFloat()
    drawChirpyBackdrop(groundY, shiftPx, world.sceneSeconds)
    drawGround(groundY, shiftPx)
    if (engine.phase != ChirpyHopPhase.Ready) {
        world.departing?.let { drawObstacle(it.kind, it.x, world, groundY) }
    }
    drawObstacle(engine.obstacleKind, engine.obstacleX, world, groundY)
    drawChirpy(engine, world, sprites, groundY)
}

/** Wraps [x] into [start, start + span), so scenery that scrolls off the left re-enters on the right. */
internal fun wrap(x: Float, start: Float, span: Float): Float = ((x - start) % span + span) % span + start

@Suppress("MagicNumber")
private fun DrawScope.drawGround(groundY: Float, shiftPx: Float) {
    val line = GROUND_LINE_DP * density
    drawRect(ChirpyInk, topLeft = Offset(0f, groundY - line / 2), size = Size(size.width, line))

    val spacing = MARK_SPACING_DP * density
    val count = maxOf((size.width / spacing).toInt() + 2, MIN_MARKS)
    val wrapStart = -MARK_WRAP_DP * density
    for (index in 0 until count) {
        val width = (8 + (index * 7) % 19) * density
        val y = groundY + (10 + (index * 11) % 18) * density
        val x = wrap(index * spacing - shiftPx, wrapStart, count * spacing)
        drawRect(GroundMarkColor, topLeft = Offset(x - width / 2, y - density), size = Size(width, 2 * density))
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawObstacle(kind: ChirpyObstacleKind, xFraction: Double, world: ChirpyWorld, groundY: Float) {
    val x = (size.width * xFraction).toFloat()
    val pulse = ((sin(world.sceneSeconds * SIGNAL_PULSE_RATE) + 1) / 2).toFloat()
    when (kind) {
        ChirpyObstacleKind.Antenna -> drawAntenna(Offset(x, groundY), heightDp = 98f, pulse = pulse)

        ChirpyObstacleKind.AntennaPair -> {
            drawDish(Offset(x - 25 * density, groundY))
            drawAntenna(Offset(x + 18 * density, groundY), heightDp = 84f, pulse = 1 - pulse)
        }

        ChirpyObstacleKind.Ufo -> {
            val hover = sin(world.sceneSeconds * UFO_HOVER_RATE).toFloat() * UFO_HOVER_DP * density
            val center = Offset(x, groundY - chirpySpriteHeight * UFO_HEIGHT_OF_SPRITE + hover)
            drawUfo(center, litLight = (world.sceneSeconds * UFO_LIGHT_RATE).toInt())
        }
    }
}

/** Fills a rounded rect given in dp, centred on ([cx], [cy]) with y measured up from [base]. */
private fun DrawScope.inkRect(base: Offset, cx: Float, cy: Float, width: Float, height: Float, corner: Float) {
    drawRoundRect(
        color = ChirpyInk,
        topLeft = Offset(base.x + (cx - width / 2) * density, base.y - (cy + height / 2) * density),
        size = Size(width * density, height * density),
        cornerRadius = CornerRadius(corner * density),
    )
}

/** A mesh node's antenna: a guyed mast with yagi elements, sending out signal arcs that fade with [pulse]. */
@Suppress("MagicNumber")
private fun DrawScope.drawAntenna(base: Offset, heightDp: Float, pulse: Float) {
    val wire = Stroke(width = 2 * density, cap = StrokeCap.Round)
    val anchor = Offset(base.x, base.y - heightDp * 0.55f * density)
    for (side in listOf(-1, 1)) {
        drawLine(ChirpyInk, anchor, Offset(base.x + side * 20 * density, base.y), wire.width, StrokeCap.Round)
    }
    drawPath(upPath(base, -14f to 0f, 14f to 0f, 6f to 10f, -6f to 10f), ChirpyInk)
    inkRect(base, 0f, heightDp / 2, 6f, heightDp, 3f)
    listOf(30f, 24f, 18f).forEachIndexed { index, width ->
        inkRect(base, 0f, heightDp - 14 - index * 12, width, 4f, 2f)
    }

    val tip = Offset(base.x, base.y - (heightDp + 4) * density)
    drawCircle(ChirpyInk, radius = 4 * density, center = tip)
    val signal = ChirpyInk.copy(alpha = 0.25f + 0.6f * pulse)
    for (radius in listOf(10f, 17f)) {
        val r = radius * density
        val topLeft = Offset(tip.x - r, tip.y - r)
        val arc = Size(2 * r, 2 * r)
        drawArc(signal, -40f, 80f, useCenter = false, topLeft = topLeft, size = arc, style = wire)
        drawArc(signal, 140f, 80f, useCenter = false, topLeft = topLeft, size = arc, style = wire)
    }
}

/** A polygon from dp points with y up, relative to [origin]. */
private fun DrawScope.upPath(origin: Offset, vararg points: Pair<Float, Float>): Path = Path().apply {
    points.forEachIndexed { index, (px, py) ->
        val x = origin.x + px * density
        val y = origin.y - py * density
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A satellite dish on a short post, tilted to look up and back along the run, with its feed held over the bowl. */
@Suppress("MagicNumber")
private fun DrawScope.drawDish(base: Offset) {
    val postHeight = 34f
    drawPath(upPath(base, -12f to 0f, 12f to 0f, 5f to 9f, -5f to 9f), ChirpyInk)
    inkRect(base, 0f, postHeight / 2, 6f, postHeight, 3f)
    val pivot = Offset(base.x, base.y - postHeight * density)
    rotate(DISH_TILT_DEGREES, pivot = pivot) {
        translate(pivot.x, pivot.y) {
            scale(density, pivot = Offset.Zero) {
                val bowl = Size(44f, 26f)
                drawArc(ChirpyInk, 0f, 180f, useCenter = true, topLeft = Offset(-22f, -19f), size = bowl)
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
@Suppress("MagicNumber")
private fun DrawScope.drawUfo(center: Offset, litLight: Int) {
    val domeRadius = 15 * density
    drawArc(
        color = ChirpyInk,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - domeRadius, center.y - 4 * density - domeRadius),
        size = Size(2 * domeRadius, 2 * domeRadius),
    )
    val glint = 9 * density
    drawArc(
        color = ChirpyPaper,
        startAngle = 200f,
        sweepAngle = 50f,
        useCenter = false,
        topLeft = Offset(center.x - glint, center.y - 4 * density - glint),
        size = Size(2 * glint, 2 * glint),
        style = Stroke(width = 2.5f * density, cap = StrokeCap.Round),
    )
    drawOval(
        ChirpyInk,
        topLeft = Offset(center.x - 15 * density, center.y + 2 * density),
        size = Size(30 * density, 10 * density),
    )
    drawOval(
        ChirpyInk,
        topLeft = Offset(center.x - 38 * density, center.y - 8 * density),
        size = Size(76 * density, 18 * density),
    )
    listOf(-22f, 0f, 22f).forEachIndexed { index, offset ->
        val lit = index == litLight % 3
        drawCircle(
            color = ChirpyPaper,
            radius = (if (lit) 3.5f else 2f) * density,
            center = Offset(center.x + offset * density, center.y + density),
        )
    }
}

private val DrawScope.chirpySpriteHeight: Float
    get() = minOf(SPRITE_HEIGHT_DP * density, size.height * SPRITE_MAX_FIELD_FRACTION)

private fun DrawScope.drawChirpy(engine: ChirpyHopEngine, world: ChirpyWorld, sprites: ChirpySprites, groundY: Float) {
    val spriteHeight = chirpySpriteHeight
    val spriteWidth = spriteHeight * SPRITE_WIDTH_DP / SPRITE_HEIGHT_DP
    val anchorX = (size.width * ChirpyHopEngine.PLAYER_X).toFloat()
    val anchorY = groundY - (engine.playerY * size.height * JUMP_SCALE).toFloat()

    val image: ImageBitmap =
        when {
            engine.phase != ChirpyHopPhase.Running -> sprites.idle

            engine.isAirborne -> sprites.jump

            engine.isCrouching -> sprites.crouch

            else -> {
                val cadence = RUN_CADENCE_BASE + ChirpyHopEngine.speed(engine.score) * RUN_CADENCE_PER_SPEED
                sprites.run[(world.sceneSeconds * cadence).toInt() % sprites.run.size]
            }
        }
    drawImage(
        image = image,
        dstOffset =
        IntOffset(
            (anchorX - spriteWidth / 2).roundToInt(),
            (anchorY - spriteHeight * (1 - SPRITE_ANCHOR_Y)).roundToInt(),
        ),
        dstSize = IntSize(spriteWidth.roundToInt(), spriteHeight.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}
