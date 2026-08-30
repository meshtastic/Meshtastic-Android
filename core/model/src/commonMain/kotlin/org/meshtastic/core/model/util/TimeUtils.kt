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
package org.meshtastic.core.model.util

import org.meshtastic.core.common.util.nowInstant
import kotlin.time.Duration.Companion.hours

private val ONLINE_WINDOW_HOURS = 2.hours

/**
 * How recently a node must have been heard to count as online, in seconds.
 *
 * Exposed as well as applied by [onlineTimeThreshold] because not every caller has the wall clock to hand: the map's
 * filter rules are pure functions given a `now`, so they compare against this window rather than re-reading the clock.
 */
val ONLINE_WINDOW_SECONDS: Long = ONLINE_WINDOW_HOURS.inWholeSeconds

fun onlineTimeThreshold(): Int = (nowInstant - ONLINE_WINDOW_HOURS).epochSeconds.toInt()
