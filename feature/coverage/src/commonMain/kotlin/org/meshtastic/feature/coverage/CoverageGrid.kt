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
package org.meshtastic.feature.coverage

/**
 * Received signal strength on a regular lat/lon grid.
 *
 * A grid, not the polar sweep the first version produced, because coverage has to be drawn as **filled iso-bands** —
 * the hosted planner runs marching squares over exactly this shape. Polar samples exported as GeoJSON `Point` features
 * get clustered by the map and render as a swarm of identical node markers, which is what they are.
 *
 * `NaN` marks a cell that was not computed.
 */
class CoverageGrid(
    val site: Site,
    val width: Int,
    val height: Int,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val dbm: DoubleArray,
) {
    init {
        require(dbm.size == width * height) { "dbm must be width*height, got ${dbm.size} for $width x $height" }
    }

    fun at(x: Int, y: Int): Double = dbm[y * width + x]

    /** Longitude of grid column [x]. */
    fun lonAt(x: Double): Double = west + (east - west) * x / (width - 1)

    /** Latitude of grid row [y]; row 0 is north. */
    fun latAt(y: Double): Double = north - (north - south) * y / (height - 1)

    /** Fraction of computed cells at or above the receiver's sensitivity. */
    val reachableFraction: Double
        get() {
            var computed = 0
            var reachable = 0
            for (v in dbm) {
                if (v.isNaN()) continue
                computed++
                if (v >= site.rxSensitivityDbm) reachable++
            }
            return if (computed == 0) 0.0 else reachable.toDouble() / computed
        }

    /** Greatest distance from the site at which any cell is reachable, km. */
    val maxRangeKm: Double
        get() {
            var best = 0.0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val v = at(x, y)
                    if (v.isNaN() || v < site.rxSensitivityDbm) continue
                    val km = haversineKm(site.latitude, site.longitude, latAt(y.toDouble()), lonAt(x.toDouble()))
                    if (km > best) best = km
                }
            }
            return best
        }
}

/**
 * Compute coverage onto a regular grid centred on [site].
 *
 * Swept in polar and resampled: see [sweepPolar]. The grid resolution is therefore free of the prediction budget, so it
 * can be fine enough for the contour tracer without costing anything.
 */
suspend fun LocalCoverage.sweepGrid(
    site: Site,
    resolution: Int = DEFAULT_GRID,
    profileStepKm: Double = DEFAULT_PROFILE_STEP_KM,
): CoverageGrid = sweepPolar(site, profileStepKm = profileStepKm).toGrid(resolution)

/** 256 cells across the coverage box is ~195 m per cell at a 25 km radius. */
internal const val DEFAULT_GRID = 256
