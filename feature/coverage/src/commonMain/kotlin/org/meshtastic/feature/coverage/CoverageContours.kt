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
 * Turns a [CoverageGrid] into filled iso-bands — "signal ≥ X dBm" polygons — as GeoJSON.
 *
 * This is the shape a map can actually draw coverage with, and the shape the hosted Site Planner
 * exports. Emitting the sample points instead produces a swarm of markers that the map clusters,
 * which is what the first version did.
 *
 * Marching squares, tracing each band's boundary cell by cell. Deliberately simple: cells at or
 * above the threshold contribute their own square to the band, so the result is a union of cell
 * rectangles rather than a smoothed isoline. At ~100 m cells that reads as a solid coverage area,
 * and it avoids the ambiguous-saddle handling a true isoline tracer needs.
 */
internal fun CoverageGrid.bands(thresholdsDbm: List<Double>): List<CoverageBand> =
    thresholdsDbm.sorted().map { threshold ->
        val rings = mutableListOf<List<Pair<Double, Double>>>()
        // Greedily merge horizontal runs of in-band cells into rectangles: far fewer polygons than
        // one per cell, and the map renders a handful of rings rather than thousands.
        val taken = BooleanArray(width * height)
        for (y in 0 until height - 1) {
            var x = 0
            while (x < width - 1) {
                if (taken[y * width + x] || !inBand(x, y, threshold)) {
                    x++
                    continue
                }
                var runEnd = x
                while (runEnd + 1 < width - 1 && !taken[y * width + runEnd + 1] && inBand(runEnd + 1, y, threshold)) {
                    runEnd++
                }
                // Extend downwards while the whole run stays in band.
                var runBottom = y
                while (runBottom + 1 < height - 1 && (x..runEnd).all { inBand(it, runBottom + 1, threshold) } &&
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
        CoverageBand(threshold, rings)
    }.filter { it.rings.isNotEmpty() }

private fun CoverageGrid.inBand(x: Int, y: Int, threshold: Double): Boolean {
    val v = at(x, y)
    return !v.isNaN() && v >= threshold
}

/** One iso-band: every cell at or above [thresholdDbm]. */
internal class CoverageBand(val thresholdDbm: Double, val rings: List<List<Pair<Double, Double>>>)

/**
 * The coverage as GeoJSON polygons with simplestyle-spec fills — what the map draws as a layer.
 *
 * Bands run from the receiver's sensitivity upward, so the outermost polygon is "a node here can
 * hear this site at all" and the inner ones are progressively stronger signal.
 */
fun CoverageGrid.toGeoJson(bandCount: Int = DEFAULT_BANDS): String {
    val floor = site.rxSensitivityDbm
    val ceiling = dbm.filter { !it.isNaN() }.maxOrNull() ?: floor
    if (ceiling <= floor) return EMPTY_FEATURE_COLLECTION

    val step = (ceiling - floor) / bandCount
    val thresholds = (0 until bandCount).map { floor + step * it }

    val features = bands(thresholds).mapIndexed { index, band ->
        val t = index.toDouble() / (bandCount - 1).coerceAtLeast(1)
        val color = bandColor(t)
        val rings = band.rings.joinToString(",") { ring ->
            "[" + ring.joinToString(",") { (lon, lat) -> "[$lon,$lat]" } + "]"
        }
        """    {"type":"Feature","geometry":{"type":"MultiPolygon","coordinates":[$rings]},""" +
            """"properties":{"title":"≥ ${band.thresholdDbm.toFixed1()} dBm",""" +
            """"dbm":${band.thresholdDbm.toFixed1()},"fill":"$color","fill-opacity":${bandOpacity(t)},""" +
            """"stroke":"$color","stroke-opacity":0.0,"stroke-width":0}}"""
    }.joinToString(",\n")

    return """{
  "type": "FeatureCollection",
  "properties": {"generator": "meshtastic-kp1812", "name": "${site.name}", "model": "ITU-R P.1812"},
  "features": [
$features
  ]
}
"""
}

/** Weakest band red, through amber, to Meshtastic green at the strongest. */
private fun bandColor(t: Double): String = when {
    t < ONE_THIRD -> "#ef476f"
    t < TWO_THIRDS -> "#ffd166"
    else -> "#67ea94"
}

/** Weaker bands are larger and sit underneath, so they stay faint. */
private fun bandOpacity(t: Double): String = (BASE_OPACITY + t * OPACITY_RANGE).toFixed1()

private const val DEFAULT_BANDS = 6
private const val ONE_THIRD = 0.34
private const val TWO_THIRDS = 0.67
private const val BASE_OPACITY = 0.15
private const val OPACITY_RANGE = 0.45
private const val EMPTY_FEATURE_COLLECTION =
    """{"type":"FeatureCollection","properties":{"generator":"meshtastic-kp1812"},"features":[]}"""
