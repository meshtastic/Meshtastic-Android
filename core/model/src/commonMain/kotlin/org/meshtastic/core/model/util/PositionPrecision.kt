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

/**
 * The bit depths firmware degrades a shared position to.
 *
 * Outside this range the position is not degraded at all — `0` for a node that has never reported one and `32` for full
 * precision — and there is no uncertainty circle to draw.
 */
val DEGRADED_PRECISION_BITS: IntRange = 10..19

/**
 * Radius, in metres, of the uncertainty circle a firmware `precision_bits` implies, or null when the position is not
 * degraded.
 *
 * The nullability is the point. This lived in three places — a lookup table on the Google map's cluster item, an
 * identical one in the MapLibre map, and a `23905787.925008 * 0.5^bits` formula in the precision preference — and only
 * the tables answered "not degraded" at all. The formula returns a number for every input, so the callers that used it
 * guarded with `> 0`, which is true for the whole range: an undegraded position asked the node-detail mini-map for a
 * circle roughly 23,905 km across, and asked the compass for one 5 mm across.
 *
 * The table and the formula agreed to within 1.1 cm at every depth, so this keeps the tabulated values the maps have
 * always drawn.
 */
fun precisionRadiusMetersOrNull(precisionBits: Int?): Double? = PRECISION_RADIUS_METERS[precisionBits]

/** Carried over verbatim from the OSMdroid marker, so the circles keep the size users calibrate their trust against. */
private val PRECISION_RADIUS_METERS =
    mapOf(
        10 to 23345.484932,
        11 to 11672.7369,
        12 to 5836.36288,
        13 to 2918.175876,
        14 to 1459.0823719999053,
        15 to 729.53562,
        16 to 364.7622,
        17 to 182.375556,
        18 to 91.182212,
        19 to 45.58554,
    )
