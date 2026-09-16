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

import org.meshtastic.kp1812.Atmosphere
import org.meshtastic.kp1812.P1812
import org.meshtastic.kp1812.Polarization
import org.meshtastic.kp1812.TerrainPath
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Spike: compute RF coverage locally, replacing the headless-WebView hand-off to the hosted
 * Site Planner.
 *
 * Today `SitePlannerRunner` loads site.meshtastic.org in a hidden WebView, waits up to 45 s for a
 * JavaScript bridge to hand back GeoJSON, and needs the network. Desktop cannot even do that —
 * it opens a browser and asks the user to export and re-import a file by hand.
 *
 * This computes the same answer in-process from `org.meshtastic:kp1812` and an [ElevationSource],
 * so it works offline once terrain is cached and produces a result on every platform the app runs
 * on. The propagation model is ITU-R P.1812 rather than the planner's SPLAT!/ITM — a different
 * model, so predictions will not match the hosted planner pixel for pixel.
 */
class LocalCoverage(
    private val elevation: ElevationSource,
    private val atmosphere: Atmosphere = Atmosphere(),
) {

    /**
     * Sweep radials out from [site] and return received signal strength at every sample point.
     *
     * @param site the transmitter
     * @param radials how many bearings to sweep; the planner's own sweep uses one per perimeter pixel
     * @param samplesPerRadial profile points along each radial, including the transmitter
     */
    /**
     * Sweep radials out from [site] and return received signal strength at every sample point.
     *
     * Terrain profile resolution and receiver spacing are deliberately **decoupled**. P.1812
     * evaluates diffraction across the whole profile between transmitter and receiver, so the
     * profile has to be sampled near the terrain's own resolution — Mapterhorn at z11 is ~75 m/px.
     * Sampling it at the receiver spacing instead (900 m over a 30 km radius) makes the model see a
     * jagged, aliased profile and invent diffraction loss that is not there: visible as spurious
     * rings and spokes in the plot, and a badly depressed reachable fraction.
     *
     * The expensive part is the number of [P1812.predict] calls, not the profile length, so a dense
     * profile with sparse receivers is both more accurate and no more costly.
     *
     * @param site the transmitter
     * @param radials how many bearings to sweep
     * @param receiversPerRadial how many receiver positions to evaluate along each radial
     * @param profileStepKm spacing of the terrain profile itself
     */
    suspend fun sweep(
        site: Site,
        radials: Int = DEFAULT_RADIALS,
        receiversPerRadial: Int = DEFAULT_RECEIVERS,
        profileStepKm: Double = DEFAULT_PROFILE_STEP_KM,
    ): Coverage {
        require(radials >= MIN_RADIALS) { "radials must be >= $MIN_RADIALS, got $radials" }
        require(receiversPerRadial >= MIN_RECEIVERS) {
            "receiversPerRadial must be >= $MIN_RECEIVERS, got $receiversPerRadial"
        }
        require(profileStepKm > 0) { "profileStepKm must be > 0, got $profileStepKm" }

        // Profile points, at terrain resolution.
        val profilePoints = maxOf((site.radiusKm / profileStepKm).toInt() + 1, MIN_PROFILE_POINTS)
        val stepKm = site.radiusKm / (profilePoints - 1)
        // Which profile indices carry a receiver. Never the first two: P.1812 needs interior points.
        val firstReceiver = MIN_PROFILE_POINTS - 1
        val receiverStride = maxOf((profilePoints - firstReceiver) / receiversPerRadial, 1)

        val points = ArrayList<CoveragePoint>(radials * receiversPerRadial)

        for (i in 0 until radials) {
            val bearing = 360.0 * i / radials
            val lats = DoubleArray(profilePoints)
            val lons = DoubleArray(profilePoints)
            val heights = DoubleArray(profilePoints)
            for (s in 0 until profilePoints) {
                val (lat, lon) = destination(site.latitude, site.longitude, bearing, stepKm * s)
                lats[s] = lat
                lons[s] = lon
                heights[s] = elevation.elevationMeters(lat, lon)
            }

            var end = firstReceiver
            while (end < profilePoints) {
                val n = end + 1
                val d = DoubleArray(n) { stepKm * it }
                val h = DoubleArray(n) { heights[it] }
                val prediction = P1812.predict(
                    path = TerrainPath(d, h, DoubleArray(n) { site.clutterHeightM }, IntArray(n) { INLAND }),
                    frequencyGhz = site.frequencyMhz / MHZ_PER_GHZ,
                    txHeightM = site.txHeightM,
                    rxHeightM = site.rxHeightM,
                    timePercent = site.timePercent,
                    pathCentreLatitudeDeg = (site.latitude + lats[end]) / 2.0,
                    polarization = Polarization.VERTICAL,
                    atmosphere = atmosphere,
                )
                val rxDbm = P1812.receivedPower(
                    prediction,
                    txPowerDbm = site.txPowerDbm,
                    txGainDbi = site.txGainDbi,
                    rxGainDbi = site.rxGainDbi,
                ).value
                points.add(CoveragePoint(lats[end], lons[end], rxDbm))
                end += receiverStride
            }
        }
        return Coverage(site, points)
    }

    private companion object {
        const val DEFAULT_RADIALS = 180
        const val DEFAULT_RECEIVERS = 40
        /** ~100 m, close to Mapterhorn z11's ~75 m/px. */
        const val DEFAULT_PROFILE_STEP_KM = 0.1
        const val MIN_RADIALS = 4
        const val MIN_RECEIVERS = 2
        const val MIN_PROFILE_POINTS = 3
        const val MHZ_PER_GHZ = 1000.0
        const val INLAND = 4
    }
}

/** Elevation above mean sea level, metres. Backed by Mapterhorn tiles in the app; trivially fakeable in tests. */
fun interface ElevationSource {
    suspend fun elevationMeters(latitude: Double, longitude: Double): Double
}

/** The transmitter and the radio configuration to model. */
data class Site(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val frequencyMhz: Double,
    val txPowerDbm: Double,
    val rxSensitivityDbm: Double,
    val txHeightM: Double = 2.0,
    val rxHeightM: Double = 1.0,
    val txGainDbi: Double = 2.0,
    val rxGainDbi: Double = 0.0,
    val radiusKm: Double = 15.0,
    val clutterHeightM: Double = 1.0,
    /** Percentage of time the prediction holds. P.1812 accepts 1..50. */
    val timePercent: Double = 50.0,
)

/** One sampled receiver position and the signal predicted there. */
data class CoveragePoint(val latitude: Double, val longitude: Double, val rxDbm: Double)

/** The result of a sweep. */
data class Coverage(val site: Site, val points: List<CoveragePoint>) {
    /** Points at or above the receiver's sensitivity — where a node would actually hear this site. */
    val reachable: List<CoveragePoint> get() = points.filter { it.rxDbm >= site.rxSensitivityDbm }

    /** Fraction of sampled points that are reachable. */
    val reachableFraction: Double get() = if (points.isEmpty()) 0.0 else reachable.size.toDouble() / points.size

    /** Greatest distance at which any sampled point is reachable, km. */
    val maxRangeKm: Double
        get() = reachable.maxOfOrNull { haversineKm(site.latitude, site.longitude, it.latitude, it.longitude) } ?: 0.0
}

private const val EARTH_RADIUS_KM = 6371.0

/** Great-circle destination from a start point along [bearingDeg] for [distanceKm]. */
fun destination(latDeg: Double, lonDeg: Double, bearingDeg: Double, distanceKm: Double): Pair<Double, Double> {
    val ang = distanceKm / EARTH_RADIUS_KM
    val lat1 = latDeg.toRadians()
    val lon1 = lonDeg.toRadians()
    val brg = bearingDeg.toRadians()
    val lat2 = asin(sin(lat1) * cos(ang) + cos(lat1) * sin(ang) * cos(brg))
    val lon2 = lon1 + atan2(sin(brg) * sin(ang) * cos(lat1), cos(ang) - sin(lat1) * sin(lat2))
    return lat2.toDegrees() to lon2.toDegrees()
}

/** Great-circle distance between two points, km. Public: a consumer plotting a [Coverage] needs it. */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat2 - lat1).toRadians()
    val dLon = (lon2 - lon1).toRadians()
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_KM * asin(max(-1.0, kotlin.math.min(1.0, kotlin.math.sqrt(a))))
}

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI
