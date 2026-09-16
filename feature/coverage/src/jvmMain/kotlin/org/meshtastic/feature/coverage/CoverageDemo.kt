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
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * Runs a real coverage prediction end to end and writes a PNG plus the GeoJSON the app imports.
 *
 * This exists so the spike can be *seen* rather than only asserted: real Mapterhorn terrain, the ITU-R P.1812 model
 * from `org.meshtastic:kp1812`, no network call to site.meshtastic.org, no WebView, no browser. The same
 * `LocalCoverage` the desktop app would call.
 *
 * `./gradlew :feature:coverage:coverageDemo -PuseMavenLocal`
 */
object CoverageDemo {

    @Suppress("MagicNumber")
    @JvmStatic
    fun main(args: Array<String>) {
        val lat = args.getOrNull(0)?.toDoubleOrNull() ?: DEFAULT_LAT
        val lon = args.getOrNull(1)?.toDoubleOrNull() ?: DEFAULT_LON
        val outDir = File(args.getOrNull(2) ?: "build/coverage-demo").apply { mkdirs() }
        val radials = args.getOrNull(3)?.toIntOrNull() ?: DEFAULT_RADIALS
        val rings = args.getOrNull(4)?.toIntOrNull() ?: DEFAULT_RINGS
        val profileStepKm = args.getOrNull(5)?.toDoubleOrNull() ?: DEFAULT_PROFILE_STEP_KM
        val readers = args.getOrNull(6)?.toIntOrNull() ?: MapterhornElevation.DEFAULT_PREFETCH_READERS

        val site =
            Site(
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
        MapterhornElevation(site.coverageBounds()).use { elevation ->
            println("terrain: ${if (elevation.isRegional) "regional" else "global"} archive at z${elevation.zoomLevel}")
            var warmed = 0
            val prefetchMs = measureTimeMillis { warmed = runBlocking { elevation.prefetch(concurrency = readers) } }
            println("prefetched $warmed terrain tiles in ${prefetchMs}ms with $readers readers")
            lateinit var coverage: CoverageGrid
            val ms = measureTimeMillis {
                coverage = runBlocking {
                    LocalCoverage(elevation)
                        .sweepPolar(site, radials = radials, rings = rings, profileStepKm = profileStepKm)
                        .toGrid(GRID)
                }
            }
            val computed = coverage.dbm.count { !it.isNaN() }
            println("computed $computed grid cells in ${ms}ms from ${elevation.tilesResident} terrain tiles")
            println(
                "predictions: ${radials * rings} ($radials radials x $rings rings, " +
                    "profile step ${profileStepKm * 1000} m), cores ${Runtime.getRuntime().availableProcessors()}",
            )
            println("reachable: ${(coverage.reachableFraction * 100).roundToInt()}%")
            println("max range: ${"%.1f".format(coverage.maxRangeKm)} km")
            val finite = coverage.dbm.filter { !it.isNaN() }
            println("rx dBm range: ${"%.1f".format(finite.min())} .. ${"%.1f".format(finite.max())}")

            reportWarmSweeps(elevation, site, radials, rings, profileStepKm)

            File(outDir, "coverage.geojson").writeText(coverage.toGeoJson())
            renderPng(coverage, File(outDir, "coverage.png"))
            println("wrote ${outDir.absolutePath}/coverage.{png,geojson}")
        }
    }

    /**
     * What a repeat estimate costs once terrain is decoded — the number that decides whether the planner can update
     * interactively.
     *
     * The second run builds a *fresh* source, because that is the app's real shape: the planner's composable is
     * disposed when its sheet closes, so nothing survives in the instance itself.
     */
    private fun reportWarmSweeps(
        elevation: MapterhornElevation,
        site: Site,
        radials: Int,
        rings: Int,
        profileStepKm: Double,
    ) {
        val again = measureTimeMillis {
            runBlocking {
                LocalCoverage(elevation)
                    .sweepPolar(site, radials = radials, rings = rings, profileStepKm = profileStepKm)
                    .toGrid(GRID)
            }
        }
        println("re-swept with terrain already decoded in ${again}ms")

        val fresh = measureTimeMillis {
            runBlocking {
                MapterhornElevation().use { second ->
                    second.prefetch(site)
                    LocalCoverage(second)
                        .sweepPolar(site, radials = radials, rings = rings, profileStepKm = profileStepKm)
                        .toGrid(GRID)
                }
            }
        }
        println("re-swept through a brand new elevation source in ${fresh}ms")
    }

    /** Plot the grid as a top-down image, coloured by signal strength. */
    @Suppress("MagicNumber")
    private fun renderPng(coverage: CoverageGrid, dest: File) {
        val size = 700
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0x14, 0x15, 0x1c)
        g.fillRect(0, 0, size, size)

        val sensitivity = coverage.site.rxSensitivityDbm
        val strongest = coverage.dbm.filter { !it.isNaN() }.maxOrNull() ?: sensitivity
        val cw = size.toDouble() / coverage.width
        val ch = size.toDouble() / coverage.height
        for (y in 0 until coverage.height) {
            for (x in 0 until coverage.width) {
                val v = coverage.at(x, y)
                if (v.isNaN() || v < sensitivity) continue
                g.color = colorFor(v, sensitivity, strongest)
                g.fillRect((x * cw).toInt(), (y * ch).toInt(), (cw + 1).toInt(), (ch + 1).toInt())
            }
        }
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
    private const val GRID = 256
}
