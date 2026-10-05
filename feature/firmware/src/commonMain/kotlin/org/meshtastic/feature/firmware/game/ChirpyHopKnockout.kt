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

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** How long the knockout plays before GAME OVER shows and a tap can restart. */
internal const val KNOCKOUT_SECONDS = 0.9

private const val HOP_SECONDS = 0.45
private const val HOP_DP = 30.0

/** hop * (1 - hop) peaks at a quarter, so this scales the hop to reach [HOP_DP]. */
private const val ARC_PEAK_SCALE = 4.0
private const val KNOCKBACK_DP = 22.0
private const val HIT_TILT_DEGREES = -22.0
private const val WOBBLE_RAMP_SECONDS = 0.3
private const val SWAY_DEGREES = 7.0
private const val SWAY_HZ = 0.9
private const val KNEE_HZ = 2.6
private const val FLASH_SECONDS = 0.3
private const val FLASH_PERIOD = 0.06
private const val FLASH_ALPHA = 0.35f
private const val SHAKE_SECONDS = 0.28
private const val SHAKE_DP = 5.0
private const val SHAKE_RATE = 70.0
private const val BURST_SECONDS = 0.4
private const val DIZZY_FROM_SECONDS = 0.4
private const val DIZZY_TURNS_PER_SECOND = 0.9

/**
 * Where a knocked-out Chirpy is, relative to where he was hit. Distances are in dp so the pose is independent of the
 * field it is drawn in.
 */
internal data class KnockoutPose(
    val knockbackDp: Float,
    val heightDp: Float,
    val rotationDegrees: Float,
    /** -1..1: positive knocks his knees together, negative bows them apart. */
    val kneeBend: Float,
    val alpha: Float,
    val shakeDp: Float,
    /** 0..1 through the impact burst, or null once it has faded. */
    val burst: Float?,
    /** Angle in radians of the stars circling his head, or null before he has landed. */
    val dizzyRadians: Float?,
)

/**
 * The pose [elapsed] seconds after the hit that ended a run, with Chirpy hit [startHeightDp] above the ground. He is
 * bounced up and back with a tilt, is on his feet again by [HOP_SECONDS] whatever height he was hit at, and from then
 * on sways on wobbly knees with stars circling his head until the run restarts. He stays close to where he was hit, so
 * he never leaves the screen.
 */
internal fun knockoutPose(elapsed: Double, startHeightDp: Double): KnockoutPose {
    val t = elapsed.coerceAtLeast(0.0)
    val hop = min(t / HOP_SECONDS, 1.0)
    val easedHop = 1 - (1 - hop) * (1 - hop)
    val dazed = (t - HOP_SECONDS).coerceAtLeast(0.0)
    val wobble = min(dazed / WOBBLE_RAMP_SECONDS, 1.0)
    val height = startHeightDp * (1 - hop) * (1 - hop) + HOP_DP * ARC_PEAK_SCALE * hop * (1 - hop)
    val sway = SWAY_DEGREES * sin(2 * PI * SWAY_HZ * dazed) * wobble
    val flashing = t < FLASH_SECONDS && (t / FLASH_PERIOD).toInt() % 2 == 1
    val shake = if (t < SHAKE_SECONDS) sin(t * SHAKE_RATE) * SHAKE_DP * (1 - t / SHAKE_SECONDS) else 0.0
    return KnockoutPose(
        knockbackDp = (-KNOCKBACK_DP * easedHop).toFloat(),
        heightDp = height.toFloat(),
        rotationDegrees = (HIT_TILT_DEGREES * sin(PI * hop) + sway).toFloat(),
        kneeBend = (sin(2 * PI * KNEE_HZ * dazed) * wobble).toFloat(),
        alpha = if (flashing) FLASH_ALPHA else 1f,
        shakeDp = shake.toFloat(),
        burst = (t / BURST_SECONDS).takeIf { it < 1.0 }?.toFloat(),
        dizzyRadians = if (t < DIZZY_FROM_SECONDS) null else (2 * PI * DIZZY_TURNS_PER_SECOND * t).toFloat(),
    )
}
