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

import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * Runs a real coverage prediction end to end and writes a PNG plus the GeoJSON the app imports.
 *
 * This exists so the spike can be *seen* rather than only asserted: real Mapterhorn terrain, the
 * ITU-R P.1812 model from `org.meshtastic:kp1812`, no network call to site.meshtastic.org, no
 * WebView, no browser. The same `LocalCoverage` the desktop app would call.
 *
 * `./gradlew :feature:coverage:coverageDemo -PuseMavenLocal`
 */
object CoverageDemo {

    @JvmStatic
    fun main(args: Array<String>) {
        val lat = args.getOrNull(0)?.toDoubleOrNull() ?: DEFAULT_LAT
        val lon = args.getOrNull(1)?.toDoubleOrNull() ?: DEFAULT_LON
        val outDir = File(args.getOrNull(2) ?: "build/coverage-demo").apply { mkdirs() }

        val site = Site(
            name = "Demo",
            latitude = lat,
            longitude = lon,
            frequencyMhz = 906.875, // Meshtastic US LongFast
            txPowerDbm = 30.0,
            rxSensitivityDbm = -130.0, // LONG_FAST
            txHeightM = 10.0,
            rxHeightM = 1.5,
            radiusKm = 25.0,
        )

        println("site ${site.latitude}, ${site.longitude}  ${site.frequencyMhz} MHz  ${site.txPowerDbm} dBm")
        MapterhornElevation().use { elevation ->
            lateinit var coverage: Coverage
            val ms = measureTimeMillis {
                coverage = runBlocking {
                    LocalCoverage(elevation).sweep(site, radials = RADIALS, receiversPerRadial = RECEIVERS)
                }
            }
            val reach = coverage.reachable
            println("computed ${coverage.points.size} points in ${ms}ms from ${elevation.tilesFetched} terrain tiles")
            println("reachable: ${reach.size} (${(coverage.reachableFraction * 100).roundToInt()}%)")
            println("max range: ${"%.1f".format(coverage.maxRangeKm)} km")
            println("rx dBm range: ${"%.1f".format(coverage.points.minOf { it.rxDbm })} .. " +
                "${"%.1f".format(coverage.points.maxOf { it.rxDbm })}")

            File(outDir, "coverage.geojson").writeText(coverage.toGeoJson())
            renderPng(coverage, File(outDir, "coverage.png"))
            println("wrote ${outDir.absolutePath}/coverage.{png,geojson}")
        }
    }

    /** Plot the sweep as a top-down image, coloured by signal strength. */
    @Suppress("MagicNumber")
    private fun renderPng(coverage: Coverage, dest: File) {
        val size = 700
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0x14, 0x15, 0x1c)
        g.fillRect(0, 0, size, size)

        val site = coverage.site
        val scale = (size / 2.0) / site.radiusKm
        val sensitivity = site.rxSensitivityDbm
        val strongest = coverage.points.maxOf { it.rxDbm }

        for (p in coverage.points) {
            val km = haversineKm(site.latitude, site.longitude, p.latitude, p.longitude)
            val bearing = kotlin.math.atan2(
                (p.longitude - site.longitude) * kotlin.math.cos(site.latitude * Math.PI / 180),
                p.latitude - site.latitude,
            )
            val px = (size / 2 + kotlin.math.sin(bearing) * km * scale).toInt()
            val py = (size / 2 - kotlin.math.cos(bearing) * km * scale).toInt()
            if (px !in 0 until size || py !in 0 until size) continue
            g.color = colorFor(p.rxDbm, sensitivity, strongest)
            g.fillOval(px - 3, py - 3, 6, 6)
        }
        // transmitter
        g.color = java.awt.Color(0x67, 0xEA, 0x94)
        g.fillOval(size / 2 - 5, size / 2 - 5, 10, 10)
        g.dispose()
        ImageIO.write(img, "png", dest)
    }

    /** Below sensitivity is left dark; above it ramps red → yellow → green. */
    @Suppress("MagicNumber")
    private fun colorFor(dbm: Double, sensitivity: Double, strongest: Double): java.awt.Color {
        if (dbm < sensitivity) return java.awt.Color(0x22, 0x23, 0x2c)
        val t = ((dbm - sensitivity) / (strongest - sensitivity)).coerceIn(0.0, 1.0)
        val r = if (t < 0.5) 255 else (255 * (1 - (t - 0.5) * 2)).toInt().coerceIn(0, 255)
        val gc = if (t < 0.5) (255 * t * 2).toInt().coerceIn(0, 255) else 255
        return java.awt.Color(r, gc, 60)
    }

    private const val DEFAULT_LAT = 47.6062 // Seattle — real relief nearby
    private const val DEFAULT_LON = -122.3321
    private const val RADIALS = 120
    private const val RECEIVERS = 40
}
