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

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Turns a [CoverageGrid] into filled iso-bands — "signal ≥ X dBm" polygons — as GeoJSON.
 *
 * This is the shape a map can actually draw coverage with, and the shape the hosted Site Planner exports. Emitting the
 * sample points instead produces a swarm of markers that the map clusters, which is what the first version did.
 *
 * Marching squares, tracing each band's boundary cell by cell. Deliberately simple: cells at or above the threshold
 * contribute their own square to the band, so the result is a union of cell rectangles rather than a smoothed isoline.
 * At ~100 m cells that reads as a solid coverage area, and it avoids the ambiguous-saddle handling a true isoline
 * tracer needs.
 */
internal fun CoverageGrid.bands(ranges: List<ClosedFloatingPointRange<Double>>): List<CoverageBand> = ranges
    .map { range ->
        val rings = mutableListOf<List<Pair<Double, Double>>>()
        // Greedily merge horizontal runs of in-band cells into rectangles: far fewer polygons than
        // one per cell, and the map renders a handful of rings rather than thousands.
        val taken = BooleanArray(width * height)
        for (y in 0 until height - 1) {
            var x = 0
            while (x < width - 1) {
                if (taken[y * width + x] || !inBand(x, y, range)) {
                    x++
                    continue
                }
                var runEnd = x
                while (runEnd + 1 < width - 1 && !taken[y * width + runEnd + 1] && inBand(runEnd + 1, y, range)) {
                    runEnd++
                }
                // Extend downwards while the whole run stays in band.
                var runBottom = y
                while (
                    runBottom + 1 < height - 1 &&
                    (x..runEnd).all { inBand(it, runBottom + 1, range) } &&
                    (x..runEnd).none { taken[(runBottom + 1) * width + it] }
                ) {
                    runBottom++
                }
                for (yy in y..runBottom) {
                    for (xx in x..runEnd) taken[yy * width + xx] = true
                }
                rings.add(
                    listOf(
                        lonAt(x.toDouble()) to latAt(y.toDouble()),
                        lonAt(runEnd + 1.0) to latAt(y.toDouble()),
                        lonAt(runEnd + 1.0) to latAt(runBottom + 1.0),
                        lonAt(x.toDouble()) to latAt(runBottom + 1.0),
                        lonAt(x.toDouble()) to latAt(y.toDouble()),
                    ),
                )
                x = runEnd + 1
            }
        }
        CoverageBand(range.start, rings)
    }
    .filter { it.rings.isNotEmpty() }

/**
 * Bands do not overlap — each cell belongs to exactly one.
 *
 * They used to nest ("everything at or above this"), which only worked because the fill was a fixed three-colour ramp
 * at low opacity. With a palette, six nested fills paint over each other and the chosen colours never appear; and a
 * single overlay transparency is only meaningful when each pixel is painted once.
 */
private fun CoverageGrid.inBand(x: Int, y: Int, range: ClosedFloatingPointRange<Double>): Boolean {
    val v = at(x, y)
    return !v.isNaN() && v >= range.start && v < range.endInclusive
}

/** One iso-band, labelled by the weakest signal it contains. */
internal class CoverageBand(val thresholdDbm: Double, val rings: List<List<Pair<Double, Double>>>)

/**
 * The coverage as GeoJSON polygons with simplestyle-spec fills — what the map draws as a layer.
 *
 * Bands run from the receiver's sensitivity upward, so the outermost polygon is "a node here can hear this site at all"
 * and the inner ones are progressively stronger signal.
 */
fun CoverageGrid.toGeoJson(style: CoverageStyle = CoverageStyle(), bandCount: Int = DEFAULT_BANDS): String {
    val step = (style.maxDbm - style.minDbm) / bandCount
    // The strongest band runs to infinity: a cell above the picker's ceiling is still coverage, and
    // dropping it would punch a hole through the middle of the plot.
    val ranges =
        (0 until bandCount).map { index ->
            val lower = style.minDbm + step * index
            val upper = if (index == bandCount - 1) Double.POSITIVE_INFINITY else lower + step
            lower..upper
        }

    val features =
        bands(ranges)
            .map { band ->
                val t = ((band.thresholdDbm - style.minDbm) / (style.maxDbm - style.minDbm)).coerceIn(0.0, 1.0)
                val color = style.palette.colorAt(t)
                // GeoJSON MultiPolygon nests coordinates[polygon][ring][position]. Emitting the rings
                // one level flatter makes a Polygon-with-holes wearing a MultiPolygon label, which MapLibre
                // silently drops - the layer is added and nothing draws.
                val polygons =
                    band.rings.joinToString(",") { ring ->
                        "[[" + ring.joinToString(",") { (lon, lat) -> "[$lon,$lat]" } + "]]"
                    }
                """    {"type":"Feature","geometry":{"type":"MultiPolygon","coordinates":[$polygons]},""" +
                    """"properties":{"title":"\u2265 ${band.thresholdDbm.toFixed1()} dBm",""" +
                    """"dbm":${band.thresholdDbm.toFixed1()},"fill":"$color",""" +
                    """"fill-opacity":${style.opacity.toFixed1()},""" +
                    """"stroke":"$color","stroke-opacity":0.0,"stroke-width":0}}"""
            }
            .joinToString(",\n")

    if (features.isEmpty()) return EMPTY_FEATURE_COLLECTION

    return """{
  "type": "FeatureCollection",
  "properties": {"generator": "meshtastic-kp1812", "name": "${site.name}", "model": "ITU-R P.1812",
    "palette": "${style.palette.key}", "min_dbm": ${style.minDbm.toFixed1()}, "max_dbm": ${style.maxDbm.toFixed1()}},
  "features": [
$features
  ]
}
"""
}

private const val DEFAULT_BANDS = 6
private const val EMPTY_FEATURE_COLLECTION =
    """{"type":"FeatureCollection","properties":{"generator":"meshtastic-kp1812"},"features":[]}"""

/**
 * One decimal place, without `String.format` — which is JVM-only and does not exist on Kotlin/Native or wasm. Adding
 * the native targets is what surfaced that.
 */
internal fun Double.toFixed1(): String {
    val scaled = (this * TENTHS).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    return "$sign${magnitude / TENTHS}.${magnitude % TENTHS}"
}

private const val TENTHS = 10
