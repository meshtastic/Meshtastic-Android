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

/**
 * Whether a recorded moment falls inside this filter's window.
 *
 * For a position *track*, where the cutoff is the point's own timestamp rather than when the node was last heard from:
 * a track is a history, so filtering it by the node's liveness would show all of it or none of it. Both map engines
 * filter tracks this way and each wrote the comparison out itself.
 */
fun LastHeardFilter.includes(recordedAtSeconds: Int, nowSeconds: Long): Boolean =
    this == LastHeardFilter.Any || recordedAtSeconds > nowSeconds - seconds

/**
 * How recently a node must have been heard for the map to pulse it.
 *
 * Shared so the two engines pulse for the same reason at the same moment; the Google map draws an animated chip and the
 * MapLibre map a circle layer, but "just heard" is one definition.
 */
const val RECENTLY_HEARD_SECONDS: Long = 5

/** Whether [lastHeard] is recent enough to pulse. */
fun heardJustNow(lastHeard: Int, nowSeconds: Long): Boolean = (nowSeconds - lastHeard) <= RECENTLY_HEARD_SECONDS
