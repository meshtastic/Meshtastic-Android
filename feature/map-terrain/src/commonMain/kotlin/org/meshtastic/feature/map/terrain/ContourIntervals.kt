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
package org.meshtastic.feature.map.terrain

/**
 * A zoom band's minor/index spacing, always in meters — [ContourIntervals.forZoom]'s `metric` flag only picks which
 * round-number family (meters vs. feet-converted-to-meters) generated the values.
 */
data class ContourIntervalBand(val minorMeters: Float, val indexMeters: Float)

/**
 * The zoom-banded contour interval table, ported verbatim from the sibling iOS app's `ContourGenerator.swift:38-58`
 * (`ContourIntervals.intervals(forZoom:metric:)`). `indexMeters` is always `5 × minorMeters` in every band; every 5th
 * minor line is an index line.
 */
object ContourIntervals {

    // literal here is one of the table's own values, not a computed quantity; naming each one (Z10_MINOR,
    // Z10_INDEX, ...) would just relabel the table without adding information, the same tradeoff as the ported
    // protobuf field numbers in VectorTile.kt.
    @Suppress("detekt:MagicNumber") // Ported zoom-band lookup table (ContourGenerator.swift:38-58) — every
    fun forZoom(zoom: Int, metric: Boolean): ContourIntervalBand = when {
        zoom <= 10 -> if (metric) ContourIntervalBand(500f, 2_500f) else feetBand(minorFeet = 2_000f)
        zoom <= 12 -> if (metric) ContourIntervalBand(100f, 500f) else feetBand(minorFeet = 500f)
        zoom <= 14 -> if (metric) ContourIntervalBand(50f, 250f) else feetBand(minorFeet = 200f)
        else -> if (metric) ContourIntervalBand(20f, 100f) else feetBand(minorFeet = 100f)
    }

    private fun feetBand(minorFeet: Float): ContourIntervalBand {
        val minorMeters = minorFeet * FOOT_METERS
        return ContourIntervalBand(minorMeters, minorMeters * INDEX_MULTIPLIER)
    }

    /** Every minor-interval level up to [maxElevationMeters], for [ContourGenerator.generate]'s `levels` param. */
    fun levelsForZoom(zoom: Int, metric: Boolean, maxElevationMeters: Float): List<Float> {
        val minor = forZoom(zoom, metric).minorMeters
        if (minor <= 0f || maxElevationMeters <= 0f) return emptyList()
        val levelCount = (maxElevationMeters / minor).toInt()
        return (1..levelCount).map { it * minor }
    }

    /** Whether [elevationMeters] (assumed to be one of [levelsForZoom]'s own outputs) is an index line, not minor. */
    fun isIndexLevel(elevationMeters: Float, zoom: Int, metric: Boolean): Boolean {
        val band = forZoom(zoom, metric)
        if (band.indexMeters <= 0f) return false
        val steps = elevationMeters / band.indexMeters
        return kotlin.math.abs(steps - kotlin.math.round(steps)) < INDEX_LEVEL_TOLERANCE
    }

    private const val FOOT_METERS = 0.3048f
    private const val INDEX_MULTIPLIER = 5f
    private const val INDEX_LEVEL_TOLERANCE = 1e-3f
}
