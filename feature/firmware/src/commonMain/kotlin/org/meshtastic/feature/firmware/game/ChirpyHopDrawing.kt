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
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
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
private const val CLOUD_WRAP_DP = 60f
private const val BIRD_HEIGHT_FRACTION = 0.145f
private const val WING_FLAP_RATE = 12.0
private const val WING_SWING_RAD = 0.32f
private const val WING_MIN_SCALE = 0.82f

private val GroundMarkColor = Color(0xB37A7A7A)
private val CloudColor = Color(0xA6A8A8A8)

private class Cloud(val xFraction: Float, val heightFraction: Float, val scale: Float, val parallax: Float)

private val clouds = listOf(Cloud(0.3f, 0.72f, 0.82f, 0.015f), Cloud(0.78f, 0.58f, 0.62f, 0.02f))

internal fun DrawScope.drawChirpyWorld(engine: ChirpyHopEngine, world: ChirpyWorld, sprites: ChirpySprites) {
    drawRect(ChirpyPaper)
    val groundY = size.height * (1 - GROUND_FRACTION)
    val shiftPx = (world.groundShift * size.width).toFloat()
    drawClouds(shiftPx)
    drawGround(groundY, shiftPx)
    drawObstacle(engine, world, groundY)
    drawChirpy(engine, world, sprites, groundY)
}

/** Wraps [x] into [start, start + span), so scenery that scrolls off the left re-enters on the right. */
private fun wrap(x: Float, start: Float, span: Float): Float = ((x - start) % span + span) % span + start

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
private fun DrawScope.drawObstacle(engine: ChirpyHopEngine, world: ChirpyWorld, groundY: Float) {
    val x = (size.width * engine.obstacleX).toFloat()
    when (engine.obstacleKind) {
        ChirpyObstacleKind.TallCactus -> drawSaguaro(x, groundY, heightDp = 98f)

        ChirpyObstacleKind.CactusCluster -> {
            drawSaguaro(x - 25 * density, groundY, heightDp = 68f)
            drawSaguaro(x + 18 * density, groundY, heightDp = 84f)
        }

        ChirpyObstacleKind.FlyingBird -> {
            val flap = sin(world.sceneSeconds * WING_FLAP_RATE).toFloat()
            drawBird(Offset(x, groundY - size.height * BIRD_HEIGHT_FRACTION), flap)
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

@Suppress("MagicNumber")
private fun DrawScope.drawSaguaro(x: Float, groundY: Float, heightDp: Float) {
    val base = Offset(x, groundY)
    inkRect(base, 0f, heightDp / 2, 22f, heightDp, 8f)
    for ((side, baseFraction, armFraction) in listOf(Triple(-1, 0.38f, 0.34f), Triple(1, 0.57f, 0.27f))) {
        val armBase = heightDp * baseFraction
        val armHeight = heightDp * armFraction
        inkRect(base, side * 15f, armBase, 34f, 14f, 7f)
        inkRect(base, side * 28f, armBase + armHeight / 2 - 3, 16f, armHeight, 7f)
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

@Suppress("MagicNumber")
private fun DrawScope.drawBird(center: Offset, flap: Float) {
    drawOval(
        ChirpyInk,
        topLeft = Offset(center.x - 31 * density, center.y - 16 * density),
        size = Size(62 * density, 32 * density),
    )
    drawCircle(ChirpyInk, radius = 14 * density, center = Offset(center.x - 29 * density, center.y - 7 * density))
    drawPath(upPath(center, -40f to 13f, -64f to 5f, -40f to 1f), ChirpyInk)
    drawPath(upPath(center, 27f to 8f, 51f to 21f, 42f to 3f, 54f to -10f, 26f to -5f), ChirpyInk)

    val wingOrigin = Offset(center.x + 2 * density, center.y - 5 * density)
    rotateRad(-flap * WING_SWING_RAD, pivot = wingOrigin) {
        scale(scaleX = 1f, scaleY = WING_MIN_SCALE + abs(flap) * (1 - WING_MIN_SCALE), pivot = wingOrigin) {
            val wing =
                Path().apply {
                    moveTo(wingOrigin.x - 10 * density, wingOrigin.y - 5 * density)
                    cubicTo(
                        wingOrigin.x - 3 * density,
                        wingOrigin.y - 35 * density,
                        wingOrigin.x + 13 * density,
                        wingOrigin.y - 42 * density,
                        wingOrigin.x + 18 * density,
                        wingOrigin.y - 8 * density,
                    )
                    lineTo(wingOrigin.x + 9 * density, wingOrigin.y + 4 * density)
                    close()
                }
            drawPath(wing, ChirpyInk)
        }
    }
    drawCircle(ChirpyPaper, radius = 3 * density, center = Offset(center.x - 33 * density, center.y - 11 * density))
}

private fun DrawScope.drawChirpy(engine: ChirpyHopEngine, world: ChirpyWorld, sprites: ChirpySprites, groundY: Float) {
    val spriteHeight = minOf(SPRITE_HEIGHT_DP * density, size.height * SPRITE_MAX_FIELD_FRACTION)
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
