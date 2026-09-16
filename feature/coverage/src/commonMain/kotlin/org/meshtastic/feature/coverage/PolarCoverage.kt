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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.cos
import kotlin.math.floor

/**
 * Received power on a polar lattice centred on the site: [radials] bearings by [rings] ranges.
 *
 * Coverage is computed in polar and only then rasterised, because a receiver's prediction needs the whole terrain
 * profile back to the transmitter. On a radial every receiver shares one profile — the far one is the near one plus a
 * few points — so a bearing's worth of predictions costs a single walk. Sampling the cartesian grid directly re-walks a
 * profile per cell and re-reads the same terrain thousands of times over.
 *
 * `dbm` is indexed `bearing * rings + ring`; ring `r` sits at `firstKm + r * ringStepKm`.
 */
class PolarCoverage(
    val site: Site,
    val radials: Int,
    val rings: Int,
    val firstKm: Double,
    val ringStepKm: Double,
    val dbm: DoubleArray,
) {
    init {
        require(dbm.size == radials * rings) { "dbm must be radials*rings, got ${dbm.size} for $radials x $rings" }
    }

    /** Received power at ring [r] of bearing index [b], which wraps. */
    fun at(b: Int, r: Int): Double {
        val bb = ((b % radials) + radials) % radials
        return dbm[bb * rings + r]
    }

    /** Distance of ring [r] from the site, km. */
    fun ringKm(r: Int): Double = firstKm + r * ringStepKm
}

/**
 * Sweep coverage in polar form, one shared terrain profile per bearing.
 *
 * [profileStepKm] is the terrain sampling interval and should track the elevation source's own resolution; [rings] is
 * how many of those steps carry a receiver. They are independent because the cost is the [P1812.predict] calls, not the
 * profile length — a dense profile with sparse receivers is both cheaper and more accurate than matching the two.
 */
suspend fun LocalCoverage.sweepPolar(
    site: Site,
    radials: Int = DEFAULT_RADIALS,
    rings: Int = DEFAULT_RINGS,
    profileStepKm: Double = DEFAULT_PROFILE_STEP_KM,
): PolarCoverage {
    require(radials >= MIN_RADIALS) { "radials must be >= $MIN_RADIALS, got $radials" }
    require(rings >= MIN_RINGS) { "rings must be >= $MIN_RINGS, got $rings" }
    require(profileStepKm > 0) { "profileStepKm must be > 0, got $profileStepKm" }

    val profilePoints = maxOf((site.radiusKm / profileStepKm).toInt() + 1, MIN_PROFILE_POINTS)
    val stepKm = site.radiusKm / (profilePoints - 1)
    // P.1812 needs interior profile points, so the innermost receiver is never the first or second.
    val firstIndex = MIN_PROFILE_POINTS - 1
    val lastIndex = profilePoints - 1
    val indexStride = (lastIndex - firstIndex).toDouble() / (rings - 1)

    val out = DoubleArray(radials * rings)
    // Radials are independent, and a sweep is pure arithmetic once the terrain is cached, so this
    // scales with cores. Each writes its own disjoint slice of `out`, so there is nothing to guard.
    // The dispatcher is explicit: a caller on runBlocking or the main thread would otherwise hand
    // every radial to that one confined thread and the sweep would stay serial.
    coroutineScope {
        (0 until radials)
            .map { b ->
                async(Dispatchers.Default) {
                    val bearing = FULL_CIRCLE * b / radials
                    // One walk per bearing: every ring on this radial is a prefix of this profile.
                    val lats = DoubleArray(profilePoints)
                    val heights = DoubleArray(profilePoints)
                    for (s in 0 until profilePoints) {
                        val (lat, lon) = destination(site.latitude, site.longitude, bearing, stepKm * s)
                        lats[s] = lat
                        heights[s] = elevationAt(lat, lon)
                    }
                    for (r in 0 until rings) {
                        val end = firstIndex + (r * indexStride).toInt()
                        out[b * rings + r] = predictAlong(site, heights, lats[end], stepKm, end)
                    }
                }
            }
            .awaitAll()
    }
    return PolarCoverage(
        site = site,
        radials = radials,
        rings = rings,
        firstKm = stepKm * firstIndex,
        ringStepKm = stepKm * indexStride,
        dbm = out,
    )
}

/**
 * Resample the polar lattice onto the regular grid the contour tracer needs.
 *
 * Bilinear in (bearing, range), so the grid is smooth rather than showing the lattice's own spokes. The grid can be far
 * finer than the lattice for free — it costs no predictions — which is what lets the bands follow terrain instead of
 * the sampling pattern.
 */
fun PolarCoverage.toGrid(resolution: Int = DEFAULT_GRID): CoverageGrid {
    require(resolution >= MIN_GRID) { "resolution must be >= $MIN_GRID, got $resolution" }

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
            if (km > site.radiusKm) continue

            // Inside the innermost ring and outside the outermost one both clamp: the lattice covers
            // the whole disc, and clamping is a nearer neighbour than any extrapolation would be.
            val rPos = ((km - firstKm) / ringStepKm).coerceIn(0.0, (rings - 1).toDouble())
            val r0 = floor(rPos).toInt().coerceAtMost(rings - 1)
            val r1 = (r0 + 1).coerceAtMost(rings - 1)
            val rf = rPos - r0

            // Bearings wrap, so no clamping here - PolarCoverage.at takes the index modulo.
            val bPos = bearingDeg(site.latitude, site.longitude, lat, lon) / FULL_CIRCLE * radials
            val b0 = floor(bPos).toInt()
            val bf = bPos - b0

            val lower = at(b0, r0) + (at(b0 + 1, r0) - at(b0, r0)) * bf
            val upper = at(b0, r1) + (at(b0 + 1, r1) - at(b0, r1)) * bf
            out[y * resolution + x] = lower + (upper - lower) * rf
        }
    }
    return CoverageGrid(site, resolution, resolution, north, south, east, west, out)
}

/**
 * Matched to a 256-cell grid over a 25 km radius: 720 bearings put ~220 m between radials at the rim and 128 rings put
 * ~195 m between ranges, both about one grid cell. Denser buys nothing the grid can show; sparser is what leaves the
 * sampling pattern visible as spokes.
 */
internal const val DEFAULT_RADIALS = 720
internal const val DEFAULT_RINGS = 128

/**
 * Terrain sampling along a radial, at the global archive's own z12 resolution (~37 m at mid latitudes). Cheap: the
 * profile is walked once per bearing, and the predictions that consume it dominate. Stepping at 100 m instead
 * measurably under-reports shadowing.
 */
internal const val DEFAULT_PROFILE_STEP_KM = 0.05
private const val MIN_RADIALS = 8
private const val MIN_RINGS = 4
private const val MIN_PROFILE_POINTS = 3
private const val MIN_GRID = 8
private const val FULL_CIRCLE = 360.0
private const val KM_PER_DEG_LAT = 111.32
private const val DEG_TO_RAD = 0.017453292519943295
