/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.node.metrics.terminal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Minimum brightness during a flicker dip (fraction of full alpha). */
@Suppress("MagicNumber")
private const val FLICKER_MIN = 0.88f

/** Maximum brightness during a flicker peak. */
private const val FLICKER_MAX = 1.00f

/** Duration of a single flicker transition in milliseconds. */
@Suppress("MagicNumber")
private const val FLICKER_TRANSITION_MS = 80

/** Delay between random flicker events in milliseconds. Keeps the effect subtle. */
@Suppress("MagicNumber")
private const val FLICKER_INTERVAL_MS = 1_500L

/**
 * Produces a continuously animated phosphor brightness value suitable for driving CRT flicker.
 *
 * The returned [State] holds a float in [[FLICKER_MIN]..[FLICKER_MAX]] that dips and recovers at random intervals,
 * simulating the subtle intensity variation of an analogue CRT.
 *
 * Usage:
 * ```
 * val flickerAlpha by rememberFlickerAlpha()
 * TerminalCanvas(lines, preset, flickerAlpha, showCursor)
 * ScanlinesOverlay(preset, flickerAlpha)
 * ```
 *
 * @return A [State]<[Float]> updated by the running animation.
 */
@Composable
fun rememberFlickerAlpha(): State<Float> {
    val animatable = remember { Animatable(FLICKER_MAX) }

    LaunchedEffect(Unit) {
        while (true) {
            // Wait a randomised interval before the next dip
            delay(FLICKER_INTERVAL_MS + Random.nextLong(0L, FLICKER_INTERVAL_MS))
            val target = FLICKER_MIN + Random.nextFloat() * (FLICKER_MAX - FLICKER_MIN)
            animatable.animateTo(target, animationSpec = tween(durationMillis = FLICKER_TRANSITION_MS))
            animatable.animateTo(FLICKER_MAX, animationSpec = tween(durationMillis = FLICKER_TRANSITION_MS * 2))
        }
    }

    return animatable.asState()
}
