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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
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

    /**
     * Shadow alpha per output pixel, `0f` (no shading) to `1f` (full shadow), row-major, [width]×[height].
     *
     * [padded] carries [margin] pixels of context on every side, and [metersPerSample] is its ground resolution
     * ([Hillshade.metersPerSample]), which sets the slope. With [shadeValleys], the margin must reach [valleyMargin] so
     * each pixel can look out across the terrain around it, and ground that sees little sky darkens whichever way it
     * faces the light.
     */
    fun shade(
        padded: ElevationTile,
        width: Int,
        height: Int,
        maxShadowAlpha: Float,
        metersPerSample: Float,
        margin: Int = MARGIN,
        shadeValleys: Boolean = false,
    ): FloatArray {
        require(margin >= MARGIN) { "margin ($margin) must be at least $MARGIN" }
        require(padded.width == width + 2 * margin && padded.height == height + 2 * margin) {
            "padded tile must be exactly ${margin}px larger than the output on every side: " +
                "expected ${width + 2 * margin}x${height + 2 * margin}, got ${padded.width}x${padded.height}"
        }

        val despiked = despike(padded)
        val occlusion =
            if (shadeValleys) SkyOcclusion.compute(despiked, margin, width, height, metersPerSample) else null
        val cellSize = max(metersPerSample, MIN_CELL_SIZE_METERS)
        val shadow = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                shadow[index] =
                    shadeOnePixel(despiked, x + margin, y + margin, cellSize, maxShadowAlpha, occlusion?.get(index))
            }
        }
        return shadow
    }

    /** Margin, in pixels, a tile needs for [shade] to light valleys at [metersPerSample]. */
    fun valleyMargin(metersPerSample: Float): Int = max(MARGIN, SkyOcclusion.radius(metersPerSample))

    /**
     * How strongly to shade a tile at [zoom] whose terrain data stops at [dataMaxZoom]: full to one zoom past the data,
     * half over the next, none beyond.
     */
    fun strength(zoom: Int, dataMaxZoom: Int): Float {
        val past = zoom - (dataMaxZoom + OVERZOOM_FULL_LEVELS)
        return when {
            past <= 0 -> 1f
            past > FADE_LEVELS -> 0f
            else -> 1f - past / (FADE_LEVELS + 1f)
        }
    }

    /** Ground distance between adjacent samples of a [tileSize]-pixel Web Mercator tile at [zoom], row [tileY]. */
    fun metersPerSample(zoom: Int, tileY: Int, tileSize: Int): Float {
        val n = 2.0.pow(zoom)
        val latitude = atan(sinh(PI * (1 - 2 * (tileY + HALF) / n)))
        return (EQUATOR_METERS * cos(latitude) / (tileSize * n)).toFloat()
    }

    /** Margin, in parent pixels, [overzoom] needs to fill [margin] output pixels at [levels] zooms past the parent. */
    fun overzoomMargin(margin: Int, levels: Int): Int {
        val scale = 1 shl levels
        return (margin + scale - 1) / scale + 1
    }

    /**
     * Resamples the sub-tile at ([childX], [childY]) of [parent], [levels] zooms deeper, to a [size]×[size] grid padded
     * by [margin], bilinear over [parent]'s elevations. [parent] is padded by at least [overzoomMargin] pixels.
     */
    fun overzoom(
        parent: ElevationTile,
        parentMargin: Int,
        levels: Int,
        childX: Int,
        childY: Int,
        size: Int,
        margin: Int,
    ): ElevationTile {
        val scale = 1 shl levels
        require(childX in 0 until scale && childY in 0 until scale) {
            "child ($childX, $childY) outside 0 until $scale"
        }
        val out = size + 2 * margin
        val result = FloatArray(out * out)
        for (oy in 0 until out) {
            val v = ((childY * size + oy - margin) + HALF) / scale - HALF
            val y0 = floor(v).toInt()
            val fy = v - y0
            for (ox in 0 until out) {
                val u = ((childX * size + ox - margin) + HALF) / scale - HALF
                val x0 = floor(u).toInt()
                val fx = u - x0
                val px = x0 + parentMargin
                val py = y0 + parentMargin
                val top = parent.elevationAt(px, py) * (1 - fx) + parent.elevationAt(px + 1, py) * fx
                val bottom = parent.elevationAt(px, py + 1) * (1 - fx) + parent.elevationAt(px + 1, py + 1) * fx
                result[oy * out + ox] = top * (1 - fy) + bottom * fy
            }
        }
        return ElevationTile(out, out, result)
    }

    private fun shadeOnePixel(
        padded: ElevationTile,
        px: Int,
        py: Int,
        cellSize: Float,
        maxShadowAlpha: Float,
        occlusion: Float?,
    ): Float {
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

        val dzdx = ((c + 2 * f + i) - (a + 2 * d + g)) / (HORN_DENOMINATOR * cellSize)
        val dzdy = ((g + 2 * h + i) - (a + 2 * b + c)) / (HORN_DENOMINATOR * cellSize)

        val slope = atan(sqrt(dzdx * dzdx + dzdy * dzdy))
        val aspect = atan2(dzdy, -dzdx)
        val illumination =
            SIN_ALTITUDE * cos(slope) + COS_ALTITUDE * sin(slope) * cos(AZIMUTH_RADIANS - HALF_PI - aspect)
        var shadow = ((SIN_ALTITUDE - illumination.coerceIn(-1f, 1f)) / SIN_ALTITUDE).coerceIn(0f, 1f)

        // Sea-level fade: zero shading at/below 0.5m, full weight at/above 3m. Kills ~1m-stdev sea-surface
        // elevation noise the despike pass alone doesn't remove — real terrain rarely sits in that band anyway.
        val seaFade = ((center - SEA_LEVEL_FADE_START) / SEA_LEVEL_FADE_RANGE).coerceIn(0f, 1f)
        shadow *= seaFade

        // Local-relief fade: water sits within noise of its own local minimum everywhere; real terrain doesn't.
        // Also flattens valley-bottom noise, which is an acceptable side effect since valley bottoms are flat.
        val localMin = min(min(min(a, b), min(c, d)), min(min(f, g), min(h, i)))
        shadow *= ((center - localMin - LOCAL_RELIEF_FADE_START) / LOCAL_RELIEF_FADE_RANGE).coerceIn(0f, 1f)

        // Only the sea fade applies to occlusion: valley floors are flat, so the relief fade would clear them where
        // they should be darkest.
        if (occlusion != null) {
            val hidden = VALLEY_STRENGTH * occlusion * seaFade
            shadow = 1 - (1 - shadow) * (1 - hidden)
        }

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

    private const val MIN_CELL_SIZE_METERS = 0.0001f

    private const val SEA_LEVEL_FADE_START = 0.5f
    private const val SEA_LEVEL_FADE_RANGE = 2.5f
    private const val LOCAL_RELIEF_FADE_START = 0.3f
    private const val LOCAL_RELIEF_FADE_RANGE = 1.2f

    private const val HALF = 0.5f
    private const val OVERZOOM_FULL_LEVELS = 1
    private const val FADE_LEVELS = 1
    private const val EQUATOR_METERS = 40_075_016.686

    private const val VALLEY_STRENGTH = 0.6f

    private const val DESPIKE_THRESHOLD_METERS = 2f
    private const val NEIGHBORHOOD_SIZE = 9
    private const val MEDIAN_INDEX = 4
}

/** Per-pixel sky visibility for [Hillshade]'s valley shading. */
private object SkyOcclusion {

    fun radius(metersPerSample: Float): Int = min(
        MAX_VALLEY_RADIUS_SAMPLES,
        max(
            MIN_VALLEY_RADIUS_SAMPLES,
            ceil(VALLEY_RADIUS_METERS / max(metersPerSample, MIN_METERS_PER_SAMPLE)).toInt(),
        ),
    )

    /**
     * Per output pixel, `0f` to `1f`: the mean over eight directions of the sine of the steepest rise (rise / run) of
     * the ground out to [VALLEY_RADIUS_METERS]. Open slopes and summits stay at 0, valley floors approach 1.
     */
    fun compute(tile: ElevationTile, margin: Int, width: Int, height: Int, metersPerSample: Float): FloatArray {
        val radius = min(margin, radius(metersPerSample))
        val distances = lookDistances(radius)
        val looks = IntArray(VALLEY_DIRECTIONS * distances.size * 2)
        for (direction in 0 until VALLEY_DIRECTIONS) {
            val angle = 2 * PI * direction / VALLEY_DIRECTIONS
            for ((step, distance) in distances.withIndex()) {
                val at = (direction * distances.size + step) * 2
                looks[at] = (cos(angle) * distance).roundToInt()
                looks[at + 1] = (sin(angle) * distance).roundToInt()
            }
        }
        val result = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                for (direction in 0 until VALLEY_DIRECTIONS) {
                    val steepest =
                        steepestRise(tile, x + margin, y + margin, direction, distances, looks, metersPerSample)
                    sum += steepest / sqrt(1 + steepest * steepest)
                }
                result[y * width + x] = sum / VALLEY_DIRECTIONS
            }
        }
        return result
    }

    @Suppress("LongParameterList")
    private fun steepestRise(
        tile: ElevationTile,
        gx: Int,
        gy: Int,
        direction: Int,
        distances: IntArray,
        looks: IntArray,
        metersPerSample: Float,
    ): Float {
        val base = tile.elevationAt(gx, gy)
        var steepest = 0f
        for ((step, distance) in distances.withIndex()) {
            val at = (direction * distances.size + step) * 2
            val sx = gx + looks[at]
            val sy = gy + looks[at + 1]
            if (sx < 0 || sx >= tile.width || sy < 0 || sy >= tile.height) break
            steepest = max(steepest, (tile.elevationAt(sx, sy) - base) / (distance * metersPerSample))
        }
        return steepest
    }

    /** Sample distances along a look: every sample to 6, then a quarter further each step, ending at [radius]. */
    private fun lookDistances(radius: Int): IntArray {
        val distances = ArrayList<Int>()
        var distance = 1
        while (distance <= radius) {
            distances.add(distance)
            distance = max(distance + 1, (distance * LOOK_GROWTH).toInt())
        }
        if (distances.last() < radius) distances.add(radius)
        return distances.toIntArray()
    }

    private const val VALLEY_DIRECTIONS = 8
    private const val VALLEY_RADIUS_METERS = 1_000f
    private const val MAX_VALLEY_RADIUS_SAMPLES = 32
    private const val MIN_VALLEY_RADIUS_SAMPLES = 2
    private const val MIN_METERS_PER_SAMPLE = 0.01f
    private const val LOOK_GROWTH = 1.25f
}
