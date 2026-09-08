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

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Horn's-method hillshade, ported from the sibling iOS app's `HillshadeTileOverlay.swift`: shadow-only output (black
 * RGB, alpha = shadow depth) so it composites correctly over both light and dark basemaps without a separate light-mode
 * palette, plus the three cleanup passes iOS added against real artifacts in Puget Sound data — despiked water,
 * sea-surface noise, and flattened-valley noise all showing up as fake terrain.
 *
 * [elevations] must be padded by exactly 1px of real neighbor data on every side of the [width]×[height] output area (a
 * 3×3 kernel needs one ring of context per output pixel) — i.e. an [ElevationTile] of size `(width+2)×(height+2)`.
 * Getting that padding from adjacent tiles, not just clamping at this tile's own edge, is the caller's job (mirroring
 * iOS's `TerrainStore.elevationTile(margin:)`); this function only shades.
 *
 * Fixed light source (azimuth 315°/NW, altitude 45°) — not configurable, matching iOS: a single consistent light
 * direction is what makes shading readable across differently-oriented terrain on the same map.
 */
object Hillshade {

    /** Shadow alpha per output pixel, `0f` (no shading) to `1f` (full shadow), row-major, [width]×[height]. */
    fun shade(padded: ElevationTile, width: Int, height: Int, maxShadowAlpha: Float): FloatArray {
        require(padded.width == width + 2 * MARGIN && padded.height == height + 2 * MARGIN) {
            "padded tile must be exactly ${MARGIN}px larger than the output on every side: " +
                "expected ${width + 2 * MARGIN}x${height + 2 * MARGIN}, got ${padded.width}x${padded.height}"
        }

        val despiked = despike(padded)
        val shadow = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                shadow[y * width + x] = shadeOnePixel(despiked, x + MARGIN, y + MARGIN, maxShadowAlpha)
            }
        }
        return shadow
    }

    private fun shadeOnePixel(padded: ElevationTile, px: Int, py: Int, maxShadowAlpha: Float): Float {
        // 3x3 Horn window, named to match the standard a..i layout (a=NW ... i=SE, e is the center itself).
        val a = padded.elevationAt(px - 1, py - 1)
        val b = padded.elevationAt(px, py - 1)
        val c = padded.elevationAt(px + 1, py - 1)
        val d = padded.elevationAt(px - 1, py)
        val center = padded.elevationAt(px, py)
        val f = padded.elevationAt(px + 1, py)
        val g = padded.elevationAt(px - 1, py + 1)
        val h = padded.elevationAt(px, py + 1)
        val i = padded.elevationAt(px + 1, py + 1)

        val dzdx = ((c + 2 * f + i) - (a + 2 * d + g)) / (HORN_DENOMINATOR * CELL_SIZE_METERS)
        val dzdy = ((g + 2 * h + i) - (a + 2 * b + c)) / (HORN_DENOMINATOR * CELL_SIZE_METERS)

        val slope = atan(sqrt(dzdx * dzdx + dzdy * dzdy))
        val aspect = atan2(dzdy, -dzdx)
        val illumination =
            SIN_ALTITUDE * cos(slope) + COS_ALTITUDE * sin(slope) * cos(AZIMUTH_RADIANS - HALF_PI - aspect)
        var shadow = ((SIN_ALTITUDE - illumination.coerceIn(-1f, 1f)) / SIN_ALTITUDE).coerceIn(0f, 1f)

        // Sea-level fade: zero shading at/below 0.5m, full weight at/above 3m. Kills ~1m-stdev sea-surface
        // elevation noise the despike pass alone doesn't remove — real terrain rarely sits in that band anyway.
        shadow *= ((center - SEA_LEVEL_FADE_START) / SEA_LEVEL_FADE_RANGE).coerceIn(0f, 1f)

        // Local-relief fade: water sits within noise of its own local minimum everywhere; real terrain doesn't.
        // Also flattens valley-bottom noise, which is an acceptable side effect since valley bottoms are flat.
        val localMin = min(min(min(a, b), min(c, d)), min(min(f, g), min(h, i)))
        shadow *= ((center - localMin - LOCAL_RELIEF_FADE_START) / LOCAL_RELIEF_FADE_RANGE).coerceIn(0f, 1f)

        return shadow * maxShadowAlpha
    }

    /**
     * Median-of-9 despike: replaces an elevation that spikes well above its 3×3 neighborhood's median with that median,
     * so isolated positive spikes (observed up to ~12m on open water) don't shade as fake terrain while real ridgelines
     * and edges — which agree with several neighbors, not just their own outlier value — survive.
     *
     * Reconstructed from iOS's documented behavior rather than a byte-exact port of `HillshadeTileOverlay.swift` (the
     * source wasn't available to copy verbatim); [DESPIKE_THRESHOLD_METERS] is a starting estimate and may need
     * retuning against real Mapterhorn data once this renders somewhere visible.
     *
     * Only iterates the interior — the [MARGIN]-px ring is real neighbor-tile elevation data (see this file's own doc
     * comment on why the padding exists), not despike's to clean up. Despiking it would corrupt exactly the pixels the
     * padding exists to get right, before [shadeOnePixel] ever reads them as edge-pixel neighbor context. Internal, not
     * private, so [HillshadeTest][org.meshtastic.feature.map.terrain.HillshadeTest] can assert the margin ring survives
     * unchanged.
     */
    internal fun despike(tile: ElevationTile): ElevationTile {
        val out = tile.elevations.copyOf()
        // One scratch window per call, refilled per pixel: a fresh array per pixel is ~64k allocations per tile,
        // on getTile's cache-miss path. Local, not a field — Hillshade is an object and shade() runs concurrently.
        val neighborhood = FloatArray(NEIGHBORHOOD_SIZE)
        for (y in MARGIN until tile.height - MARGIN) {
            for (x in MARGIN until tile.width - MARGIN) {
                var n = 0
                for (dy in -MARGIN..MARGIN) {
                    for (dx in -MARGIN..MARGIN) {
                        neighborhood[n++] = tile.elevationAt(x + dx, y + dy)
                    }
                }
                neighborhood.sort()
                val median = neighborhood[MEDIAN_INDEX]
                val center = tile.elevationAt(x, y)
                if (center - median > DESPIKE_THRESHOLD_METERS) {
                    out[y * tile.width + x] = median
                }
            }
        }
        return ElevationTile(tile.width, tile.height, out)
    }

    /** How far outside the output area [ElevationTile]s passed to [shade] must be padded with real neighbor data. */
    const val MARGIN = 1

    private const val AZIMUTH_DEGREES = 315.0
    private const val ALTITUDE_DEGREES = 45.0
    private val AZIMUTH_RADIANS = (AZIMUTH_DEGREES * PI / 180.0).toFloat()
    private val ALTITUDE_RADIANS = (ALTITUDE_DEGREES * PI / 180.0).toFloat()
    private val SIN_ALTITUDE = sin(ALTITUDE_RADIANS)
    private val COS_ALTITUDE = cos(ALTITUDE_RADIANS)

    // Float, not the bare Double kotlin.math.PI: mixing that into the per-pixel illumination expression below
    // promotes the whole expression to Double, which is both a needless precision widening on a hot path and
    // (concretely) a compile error against this function's Float return type.
    private val HALF_PI = (PI / 2).toFloat()

    private const val HORN_DENOMINATOR = 8f

    // Terrarium tiles are 256px covering one Web Mercator tile edge at the tile's own zoom; the true meters-per-
    // pixel cell size varies with latitude and zoom, so this is a coarse constant tuned for legible shading
    // rather than a geodesic distance — matching how iOS's own Horn-method cellSize is a shading parameter, not
    // a precise ground measurement.
    private const val CELL_SIZE_METERS = 30f

    private const val SEA_LEVEL_FADE_START = 0.5f
    private const val SEA_LEVEL_FADE_RANGE = 2.5f
    private const val LOCAL_RELIEF_FADE_START = 0.3f
    private const val LOCAL_RELIEF_FADE_RANGE = 1.2f

    private const val DESPIKE_THRESHOLD_METERS = 2f
    private const val NEIGHBORHOOD_SIZE = 9
    private const val MEDIAN_INDEX = 4
}
