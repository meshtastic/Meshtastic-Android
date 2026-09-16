/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.meshtastic.feature.coverage

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spike verification: the local model behaves the way a coverage prediction must, without
 * needing a network, a WebView or a terrain download.
 *
 * These are behavioural assertions, not conformance ones — `kp1812`'s own suite already checks
 * the model against the ITU reference. What matters here is that this module drives it correctly.
 */
class LocalCoverageTest {

    private val flat = ElevationSource { _, _ -> 0.0 }

    private fun site(radiusKm: Double = 10.0, txPowerDbm: Double = 30.0) = Site(
        name = "Test",
        latitude = 47.6,
        longitude = -122.2,
        frequencyMhz = 915.0,
        txPowerDbm = txPowerDbm,
        rxSensitivityDbm = -130.0,
        txHeightM = 10.0,
        radiusKm = radiusKm,
    )

    @Test
    fun signalFallsOffWithDistance() = runTest {
        val coverage = LocalCoverage(flat).sweep(site(), radials = 4, samplesPerRadial = 20)
        // Take one radial's worth of points and check monotone decay over flat ground.
        val radial = coverage.points.take(18)
        val distances = radial.map { haversineKm(47.6, -122.2, it.latitude, it.longitude) }
        assertTrue(distances.zipWithNext().all { (a, b) -> b > a }, "samples should step outward")
        val strengths = radial.map { it.rxDbm }
        assertTrue(
            strengths.first() > strengths.last(),
            "signal should be weaker far away: ${strengths.first()} -> ${strengths.last()}",
        )
    }

    @Test
    fun aHillBlocksSignalBehindIt() = runTest {
        // A ridge 5 km north of the site, 400 m tall. Everything beyond it should be worse than
        // the same distance to the south, where the ground is flat.
        val ridged = ElevationSource { lat, _ ->
            val northKm = (lat - 47.6) * 111.0
            if (northKm in 4.5..5.5) 400.0 else 0.0
        }
        val coverage = LocalCoverage(ridged).sweep(site(), radials = 4, samplesPerRadial = 24)
        val far = 8.0
        fun strengthTowards(bearingIndex: Int): Double {
            val perRadial = coverage.points.size / 4
            return coverage.points.drop(bearingIndex * perRadial).take(perRadial)
                .minByOrNull { abs(haversineKm(47.6, -122.2, it.latitude, it.longitude) - far) }!!
                .rxDbm
        }
        val north = strengthTowards(0) // bearing 0 = north, over the ridge
        val south = strengthTowards(2) // bearing 180 = south, flat
        assertTrue(north < south, "signal past a 400 m ridge ($north dBm) should be weaker than flat ($south dBm)")
    }

    @Test
    fun higherTransmitPowerReachesFurther() = runTest {
        val low = LocalCoverage(flat).sweep(site(txPowerDbm = 17.0), radials = 4, samplesPerRadial = 20)
        val high = LocalCoverage(flat).sweep(site(txPowerDbm = 30.0), radials = 4, samplesPerRadial = 20)
        assertTrue(
            high.maxRangeKm >= low.maxRangeKm,
            "30 dBm (${high.maxRangeKm} km) should reach at least as far as 17 dBm (${low.maxRangeKm} km)",
        )
        assertTrue(high.reachableFraction >= low.reachableFraction)
    }

    @Test
    fun reachableRespectsReceiverSensitivity() = runTest {
        val coverage = LocalCoverage(flat).sweep(site(), radials = 4, samplesPerRadial = 12)
        assertTrue(coverage.points.isNotEmpty())
        assertTrue(coverage.reachable.all { it.rxDbm >= coverage.site.rxSensitivityDbm })
        assertTrue(coverage.points.none { it.rxDbm >= coverage.site.rxSensitivityDbm && it !in coverage.reachable })
    }

    @Test
    fun sweepCoversEveryBearing() = runTest {
        val coverage = LocalCoverage(flat).sweep(site(), radials = 8, samplesPerRadial = 6)
        // 8 radials x (6 samples - 2 skipped leading points) = 32
        assertEquals(8 * 4, coverage.points.size)
    }

    @Test
    fun destinationAndHaversineAgree() {
        val (lat, lon) = destination(47.6, -122.2, bearingDeg = 45.0, distanceKm = 25.0)
        val back = haversineKm(47.6, -122.2, lat, lon)
        assertTrue(abs(back - 25.0) < 0.01, "round trip was $back km, expected 25")
    }
}

/**
 * The one-decimal helper that replaced `String.format`, which does not exist off the JVM.
 *
 * Exact halves are deliberately not asserted: `roundToLong` breaks ties toward positive infinity,
 * and whether a decimal like -76.15 even *is* a tie depends on its binary representation
 * (-76.15 * 10 is -761.4999999999999, so it rounds to -76.1). Immaterial for displaying dBm, and
 * a test that pinned it would be asserting floating-point trivia rather than behaviour.
 */
class ToFixed1Test {
    @Test
    fun formatsToOneDecimalPlace() {
        assertEquals("-76.1", (-76.14).toFixed1())
        assertEquals("-76.2", (-76.16).toFixed1())
        assertEquals("0.0", 0.0.toFixed1())
        assertEquals("130.0", 130.0.toFixed1())
        assertEquals("-130.0", (-129.999).toFixed1())
        assertEquals("7.5", 7.45001.toFixed1())
    }

    @Test
    fun roundTripsThroughGeoJson() {
        // The value that actually matters: what lands in the exported feature properties.
        assertEquals("-76.1", (-76.14).toFixed1())
        assertEquals("-145.4", (-145.44).toFixed1())
        assertEquals("-61.7", (-61.72).toFixed1())
    }
}
