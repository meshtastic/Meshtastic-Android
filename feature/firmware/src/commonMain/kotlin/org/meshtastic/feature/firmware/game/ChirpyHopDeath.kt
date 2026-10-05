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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** How long the knockout plays before GAME OVER shows and a tap can restart. */
internal const val DEATH_ANIMATION_SECONDS = 0.9

private const val HOP_SECONDS = 0.45
private const val SQUASH_SECONDS = 0.15
private const val HOP_DP = 30.0

/** fall * (1 - fall) peaks at a quarter, so this scales the hop to reach [HOP_DP]. */
private const val ARC_PEAK_SCALE = 4.0
private const val KNOCKBACK_DP = 22.0
private const val HIT_TILT_DEGREES = -22.0
private const val KO_TILT_DEGREES = -8.0
private const val SQUASH_Y = 0.62
private const val SQUASH_X = 1.14
private const val FLASH_SECONDS = 0.3
private const val FLASH_PERIOD = 0.06
private const val FLASH_ALPHA = 0.35f
private const val SHAKE_SECONDS = 0.28
private const val SHAKE_DP = 5.0
private const val SHAKE_RATE = 70.0
private const val BURST_SECONDS = 0.4
private const val BURST_RAYS = 8
private const val DIZZY_FROM_SECONDS = 0.4
private const val DIZZY_TURNS_PER_SECOND = 0.9
private const val DIZZY_STARS = 3

/**
 * Where a knocked-out Chirpy is [elapsed] seconds after the hit, starting [startHeightDp] above the ground. He is
 * bounced up and back with a tilt, lands squashed flat by the time [HOP_SECONDS] is up whatever height he was hit at,
 * and then sits dazed with stars circling his head. He stays close to where he was hit, so he never leaves the screen.
 */
internal data class DeathPose(
    val knockbackDp: Float,
    val heightDp: Float,
    val rotationDegrees: Float,
    val scaleX: Float,
    val scaleY: Float,
    val alpha: Float,
    val shakeDp: Float,
    /** 0..1 through the impact burst, or null once it has faded. */
    val burst: Float?,
    /** Angle in radians of the circling stars, or null before he has landed. */
    val dizzyRadians: Float?,
)

internal fun deathPose(elapsed: Double, startHeightDp: Double): DeathPose {
    val t = elapsed.coerceAtLeast(0.0)
    val hop = min(t / HOP_SECONDS, 1.0)
    val easedHop = 1 - (1 - hop) * (1 - hop)
    val squash = ((t - HOP_SECONDS) / SQUASH_SECONDS).coerceIn(0.0, 1.0)
    val height = startHeightDp * (1 - hop) * (1 - hop) + HOP_DP * ARC_PEAK_SCALE * hop * (1 - hop)
    val tilt = HIT_TILT_DEGREES * sin(PI * hop) + KO_TILT_DEGREES * squash
    val flashing = t < FLASH_SECONDS && (t / FLASH_PERIOD).toInt() % 2 == 1
    val shake = if (t < SHAKE_SECONDS) sin(t * SHAKE_RATE) * SHAKE_DP * (1 - t / SHAKE_SECONDS) else 0.0
    return DeathPose(
        knockbackDp = (-KNOCKBACK_DP * easedHop).toFloat(),
        heightDp = height.toFloat(),
        rotationDegrees = tilt.toFloat(),
        scaleX = (1 + (SQUASH_X - 1) * squash).toFloat(),
        scaleY = (1 - (1 - SQUASH_Y) * squash).toFloat(),
        alpha = if (flashing) FLASH_ALPHA else 1f,
        shakeDp = shake.toFloat(),
        burst = (t / BURST_SECONDS).takeIf { it < 1.0 }?.toFloat(),
        dizzyRadians = if (t < DIZZY_FROM_SECONDS) null else (2 * PI * DIZZY_TURNS_PER_SECOND * t).toFloat(),
    )
}

/** Stars circling above a dazed Chirpy's head at [center], the ring [radiusX] wide, turned to [angle]. */
@Suppress("MagicNumber")
internal fun DrawScope.drawDizzyStars(center: Offset, radiusX: Float, angle: Float) {
    for (star in 0 until DIZZY_STARS) {
        val a = angle + 2 * PI.toFloat() * star / DIZZY_STARS
        // The far side of the ring is smaller, which reads as the stars going round behind his head.
        val depth = 0.7f + 0.3f * sin(a)
        val at = Offset(center.x + cos(a) * radiusX, center.y + sin(a) * radiusX * 0.3f)
        drawStar(at, 5 * density * depth)
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawStar(center: Offset, radius: Float) {
    val star =
        Path().apply {
            for (point in 0 until 10) {
                val r = if (point % 2 == 0) radius else radius * 0.45f
                val a = -PI / 2 + PI * point / 5
                val p = Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r)
                if (point == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
    drawPath(star, ChirpyInk)
}

/** Sparks flying out from where Chirpy was hit, [progress] 0..1 as they spread and fade. */
@Suppress("MagicNumber")
internal fun DrawScope.drawImpactBurst(center: Offset, progress: Float) {
    val fade = 1 - progress
    val inner = (8 + 40 * progress) * density
    val length = 16 * fade * density
    for (ray in 0 until BURST_RAYS) {
        val angle = 2 * PI * ray / BURST_RAYS + PI / BURST_RAYS
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(
            color = ChirpyInk.copy(alpha = fade),
            start = center + direction * inner,
            end = center + direction * (inner + length),
            strokeWidth = 3 * density,
            cap = StrokeCap.Round,
        )
    }
}
