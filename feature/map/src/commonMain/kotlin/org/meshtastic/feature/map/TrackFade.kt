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
package org.meshtastic.feature.map

/** The alpha a track's oldest end fades to: faint, never invisible, so its first point can still be seen and tapped. */
const val OLDEST_TRACK_ALPHA = 0.25f

/** Steps the fade is bucketed into where each step costs a prebuilt marker icon. */
const val TRACK_FADE_LEVELS = 8

/**
 * Where each point of an oldest-first track sits between its oldest and newest fix in time, from 0 to 1, so a burst of
 * fixes after a quiet hour reads as recent and the quiet hour as old. Measured across the track's own span, so a track
 * that is old throughout still fades from end to end. When any time is unknown (null or not positive) or every time is
 * the same, the track fades by its order instead.
 */
fun trackRecency(times: List<Long?>): List<Float> {
    val known = times.mapNotNull { it?.takeIf { t -> t > 0 } }
    val oldest = known.minOrNull()
    val newest = known.maxOrNull()
    return when {
        times.size < 2 -> List(times.size) { 1f }

        known.size < times.size || oldest == null || newest == null || newest == oldest ->
            List(times.size) { it.toFloat() / times.lastIndex }

        else -> times.map { (((it ?: oldest) - oldest).toFloat() / (newest - oldest)).coerceIn(0f, 1f) }
    }
}

/** The alpha at [recency] along the fade. */
fun trackFadeAlpha(recency: Float): Float = OLDEST_TRACK_ALPHA + (1f - OLDEST_TRACK_ALPHA) * recency.coerceIn(0f, 1f)

/** The [TRACK_FADE_LEVELS] bucket [recency] falls in, the newest point always in the top one. */
fun trackFadeLevel(recency: Float): Int =
    (recency.coerceIn(0f, 1f) * (TRACK_FADE_LEVELS - 1)).toInt().coerceAtMost(TRACK_FADE_LEVELS - 1)

/** The alpha every point in fade bucket [level] draws at. */
fun trackFadeLevelAlpha(level: Int): Float = trackFadeAlpha(level.toFloat() / (TRACK_FADE_LEVELS - 1))
