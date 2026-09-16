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

import kotlin.math.cos

/**
 * Received signal strength on a regular lat/lon grid.
 *
 * A grid, not the polar sweep the first version produced, because coverage has to be drawn as
 * **filled iso-bands** — the hosted planner runs marching squares over exactly this shape. Polar
 * samples exported as GeoJSON `Point` features get clustered by the map and render as a swarm of
 * identical node markers, which is what they are.
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

/** Compute coverage onto a regular grid centred on [site]. */
suspend fun LocalCoverage.sweepGrid(
    site: Site,
    resolution: Int = DEFAULT_GRID,
    profileStepKm: Double = 0.1,
): CoverageGrid {
    require(resolution >= MIN_GRID) { "resolution must be >= $MIN_GRID, got $resolution" }

    // A degree of longitude shrinks with latitude; keep the box square on the ground.
    val latSpanDeg = site.radiusKm / KM_PER_DEG_LAT
    val lonSpanDeg = latSpanDeg / cos(site.latitude * DEG_TO_RAD)
    val north = site.latitude + latSpanDeg
    val south = site.latitude - latSpanDeg
    val east = site.longitude + lonSpanDeg
    val west = site.longitude - lonSpanDeg

    val out = DoubleArray(resolution * resolution) { Double.NaN }
    for (y in 0 until resolution) {
        val lat = north - (north - south) * y / (resolution - 1)
        for (x in 0 until resolution) {
            val lon = west + (east - west) * x / (resolution - 1)
            val km = haversineKm(site.latitude, site.longitude, lat, lon)
            // Outside the requested radius, and too close to profile at all, stay NaN.
            if (km > site.radiusKm || km < profileStepKm * MIN_PROFILE_STEPS) continue
            out[y * resolution + x] = predictAt(site, lat, lon, km, profileStepKm)
        }
    }
    return CoverageGrid(site, resolution, resolution, north, south, east, west, out)
}

internal const val DEFAULT_GRID = 96
private const val MIN_GRID = 8
private const val MIN_PROFILE_STEPS = 3
private const val KM_PER_DEG_LAT = 111.32
private const val DEG_TO_RAD = 0.017453292519943295
