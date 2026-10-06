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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SPRITE_ANCHOR_Y = 0.08f
private const val RUN_CADENCE_BASE = 14.0
private const val RUN_CADENCE_PER_SPEED = 8.0
private const val IMPACT_X_OF_SPRITE = 0.22f
private const val IMPACT_Y_OF_SPRITE = 0.4f
private const val BURST_RAYS = 8
private const val DIZZY_STARS = 3
private const val DIZZY_HEIGHT_OF_SPRITE = 0.74f
private const val DIZZY_WIDTH_OF_SPRITE = 0.24f
private const val DEGREES_PER_HALF_TURN = 180f

// Where the design vector stands in for the idle sprite: the share of the sprite its character fills, and its feet.
private const val DAZED_HEIGHT_OF_SPRITE = 0.774f
private const val DAZED_FEET_OF_SPRITE = 0.027f
private const val DAZED_X_OF_SPRITE = 0.06f

// Chirpy's design vector (img_chirpy, from meshtastic/design chirpy.svg) in its own viewport units. Its legs are one
// outline with the body, so it is clipped just under the body and the legs are drawn here, free to bend at the knee.
private const val VECTOR_WIDTH = 1871.7f
private const val VECTOR_HEIGHT = 2607.9f
private const val VECTOR_BODY_CENTER_X = 918f
private const val VECTOR_HIP_Y = 1843f
private const val VECTOR_FOOT_Y = 2582f
private const val VECTOR_LEG_WIDTH = 30f
private const val VECTOR_KNEE_SWING = 70f
private const val VECTOR_KNEE_DIP = 40f

/** (hip x, toe x) for his left and right legs, from the design's own legs and feet. */
private val vectorLegs = listOf(723f to 356f, 1082f to 1430f)

/**
 * Chirpy himself: the running, jumping and ducking sprites during a run, and after a hit the design's front-facing
 * vector, dazed and swaying on legs drawn to bend at the knee, with stars circling his head.
 */
@Suppress("MagicNumber")
internal class ChirpyHopCharacter(
    private val groundY: Float,
    private val spriteWidth: Float,
    private val spriteHeight: Float,
    density: Density,
) : Density by density {
    private val dazedHeight = spriteHeight * DAZED_HEIGHT_OF_SPRITE
    private val vectorUnit = dazedHeight / VECTOR_HEIGHT
    private val dazedSize = Size(VECTOR_WIDTH * vectorUnit, VECTOR_HEIGHT * vectorUnit)
    private val legStroke =
        Stroke(width = VECTOR_LEG_WIDTH * vectorUnit, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** Rebuilt each frame the legs move, reusing the one path rather than allocating a new one. */
    private val leg = Path()

    /** A five-pointed star of unit radius around the origin. */
    private val star =
        Path().apply {
            for (point in 0 until 10) {
                val r = if (point % 2 == 0) 1f else 0.45f
                val a = -PI / 2 + PI * point / 5
                val x = cos(a).toFloat() * r
                val y = sin(a).toFloat() * r
                if (point == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

    fun DrawScope.drawChirpy(
        engine: ChirpyHopEngine,
        sceneSeconds: Double,
        sprites: ChirpyHopSprites,
        knockout: KnockoutPose?,
    ) {
        val runX = (size.width * ChirpyHopEngine.PLAYER_X).toFloat()
        val runY = groundY - (engine.playerY * size.height * JUMP_SCALE).toFloat()
        if (knockout == null) {
            drawRunning(engine, sceneSeconds, sprites, Offset(runX, runY))
            return
        }
        knockout.burst?.let {
            drawImpactBurst(
                Offset(runX + spriteWidth * IMPACT_X_OF_SPRITE, runY - spriteHeight * IMPACT_Y_OF_SPRITE),
                it,
            )
        }
        val feet = Offset(runX + knockout.knockbackDp.dp.toPx(), groundY - knockout.heightDp.dp.toPx())
        rotate(knockout.rotationDegrees, pivot = feet) {
            drawDazed(
                vector = sprites.dazed,
                feet = Offset(feet.x + spriteWidth * DAZED_X_OF_SPRITE, feet.y - spriteHeight * DAZED_FEET_OF_SPRITE),
                kneeBend = knockout.kneeBend,
                alpha = knockout.alpha,
            )
        }
        knockout.dizzyRadians?.let { angle ->
            // Follow his head as he sways by rotating the point above his feet.
            val rise = spriteHeight * DIZZY_HEIGHT_OF_SPRITE
            val tilt = knockout.rotationDegrees * PI.toFloat() / DEGREES_PER_HALF_TURN
            drawDizzyStars(Offset(feet.x + rise * sin(tilt), feet.y - rise * cos(tilt)), angle)
        }
    }

    private fun DrawScope.drawRunning(
        engine: ChirpyHopEngine,
        sceneSeconds: Double,
        sprites: ChirpyHopSprites,
        feet: Offset,
    ) {
        val image =
            when {
                engine.phase != ChirpyHopPhase.Running -> sprites.idle

                engine.isAirborne -> sprites.jump

                engine.isCrouching -> sprites.crouch

                else -> {
                    val cadence = RUN_CADENCE_BASE + ChirpyHopEngine.speed(engine.score) * RUN_CADENCE_PER_SPEED
                    sprites.run[(sceneSeconds * cadence).toInt() % sprites.run.size]
                }
            }
        drawImage(
            image = image,
            dstOffset =
            IntOffset(
                (feet.x - spriteWidth / 2).roundToInt(),
                (feet.y - spriteHeight * (1 - SPRITE_ANCHOR_Y)).roundToInt(),
            ),
            dstSize = IntSize(spriteWidth.roundToInt(), spriteHeight.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }

    /** The design's Chirpy standing at [feet], his knees knocking by [kneeBend] and his body dipping as they give. */
    private fun DrawScope.drawDazed(vector: Painter, feet: Offset, kneeBend: Float, alpha: Float) {
        val dip = VECTOR_KNEE_DIP * abs(kneeBend)
        translate(feet.x - VECTOR_BODY_CENTER_X * vectorUnit, feet.y - VECTOR_FOOT_Y * vectorUnit) {
            translate(top = dip * vectorUnit) {
                clipRect(bottom = VECTOR_HIP_Y * vectorUnit) {
                    with(vector) { draw(dazedSize, alpha = alpha) }
                }
            }
            val ink = ChirpyInk.copy(alpha = alpha)
            val hipY = (VECTOR_HIP_Y + dip) * vectorUnit
            val footY = VECTOR_FOOT_Y * vectorUnit
            vectorLegs.forEachIndexed { index, (hipX, toeX) ->
                val inward = if (index == 0) 1 else -1
                val kneeX = (hipX + inward * kneeBend * VECTOR_KNEE_SWING) * vectorUnit
                leg.reset()
                leg.moveTo(hipX * vectorUnit, hipY)
                leg.quadraticTo(kneeX, (hipY + footY) / 2, hipX * vectorUnit, footY)
                leg.lineTo(toeX * vectorUnit, footY)
                drawPath(leg, ink, style = legStroke)
            }
        }
    }

    /** Sparks flying out from where Chirpy was hit, [progress] 0..1 as they spread and fade. */
    private fun DrawScope.drawImpactBurst(center: Offset, progress: Float) {
        val fade = 1 - progress
        val inner = (8 + 40 * progress).dp.toPx()
        val length = (16 * fade).dp.toPx()
        for (ray in 0 until BURST_RAYS) {
            val angle = 2 * PI * ray / BURST_RAYS + PI / BURST_RAYS
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                color = ChirpyInk.copy(alpha = fade),
                start = center + direction * inner,
                end = center + direction * (inner + length),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    /** Stars circling above a dazed Chirpy's head at [center], turned to [angle]. */
    private fun DrawScope.drawDizzyStars(center: Offset, angle: Float) {
        val radiusX = spriteWidth * DIZZY_WIDTH_OF_SPRITE
        for (index in 0 until DIZZY_STARS) {
            val a = angle + 2 * PI.toFloat() * index / DIZZY_STARS
            // The far side of the ring is smaller, which reads as the stars going round behind his head.
            val depth = 0.7f + 0.3f * sin(a)
            translate(center.x + cos(a) * radiusX, center.y + sin(a) * radiusX * 0.3f) {
                scale(5.dp.toPx() * depth, pivot = Offset.Zero) { drawPath(star, ChirpyInk) }
            }
        }
    }
}
