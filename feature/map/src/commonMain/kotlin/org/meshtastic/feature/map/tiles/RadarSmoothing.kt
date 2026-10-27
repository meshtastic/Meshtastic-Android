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
@file:Suppress("MagicNumber") // B-spline and Gaussian coefficients; naming each term would hide the formula

package org.meshtastic.feature.map.tiles

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh

/** Web Mercator earth radius in metres. */
private const val MERCATOR_RADIUS_METERS = 6_378_137.0

/** NEXRAD cells are 0.01 degrees of longitude, so 36,000 of them span the world. */
private const val CELLS_AROUND_THE_WORLD = 36_000

/** Keeps the cell height finite as the latitude nears a pole. */
private const val MIN_COS_LATITUDE = 0.01

private const val CHANNELS = 4
private const val BYTE_MAX = 255
private const val BLUR_RADIUS = 2
private const val SPLINE_TAPS = 4

/**
 * Radar cells asked for past a tile on every side. An output pixel reads four cells through the spline, each softened
 * from two cells either side, so a tile's edge pixels reach four cells out; with all of them fetched, neighboring tiles
 * agree where they meet.
 */
internal const val RADAR_CELL_PADDING = 4

/** How far the cells are softened before scaling, in cells. A cell on its own is mostly speckle and fades out. */
private const val BLUR_CELLS = 0.5f

/** The scaled-up rain is fully drawn from [EDGE_HIGH] coverage and gone below [EDGE_LOW]. */
private const val EDGE_LOW = 0.4f
private const val EDGE_HIGH = 0.6f

/** One cell in Web Mercator metres across. */
internal const val RADAR_CELL_METERS = 2.0 * ORIGIN_SHIFT_METERS / CELLS_AROUND_THE_WORLD

/**
 * The radar cells across a tile, for asking the server for one pixel per cell.
 *
 * The server copies cells to fill a finer tile, which is what draws the squares; asked at the cells' own size it
 * returns the raw grid for [smoothRadar] to scale up properly.
 */
internal class RadarCellGrid
private constructor(
    val columns: Int,
    val rows: Int,
    val paddedMinX: Double,
    val paddedMinY: Double,
    val paddedMaxX: Double,
    val paddedMaxY: Double,
) {
    val requestWidth: Int
        get() = columns + 2 * RADAR_CELL_PADDING

    val requestHeight: Int
        get() = rows + 2 * RADAR_CELL_PADDING

    companion object {
        /**
         * The grid for a tile covering the Mercator box, drawn [outputPixels] pixels square, or null when a pixel
         * already covers a cell and there are no squares to smooth.
         */
        @Suppress("ReturnCount")
        fun of(minX: Double, minY: Double, maxX: Double, maxY: Double, outputPixels: Int): RadarCellGrid? {
            val width = maxX - minX
            val height = maxY - minY
            if (width <= 0.0 || height <= 0.0 || outputPixels <= 0) return null
            // Cells are square in degrees, so in Mercator they are taller by 1 / cos(latitude).
            val latitude = atan(sinh((minY + maxY) / 2 / MERCATOR_RADIUS_METERS))
            val cellHeight = RADAR_CELL_METERS / max(cos(latitude), MIN_COS_LATITUDE)
            val columns = max(1, (width / RADAR_CELL_METERS).roundToInt())
            val rows = max(1, (height / cellHeight).roundToInt())
            if (columns >= outputPixels || rows >= outputPixels) return null
            val padX = width / columns * RADAR_CELL_PADDING
            val padY = height / rows * RADAR_CELL_PADDING
            return RadarCellGrid(columns, rows, minX - padX, minY - padY, maxX + padX, maxY + padY)
        }
    }
}

/**
 * Scales a grid of radar cells up to [width] by [height] pixels, rounded and with a clean edge.
 *
 * [argb] is the server's [RadarCellGrid.requestWidth] by [RadarCellGrid.requestHeight] image of the padded box, as
 * straight (non-premultiplied) ARGB. The cells are softened, scaled with a cubic B-spline, and the edge pulled back to
 * a line where the rain is half there; the padding falls outside the result. Returns straight ARGB, or null when [argb]
 * is not the size [grid] asks for.
 */
internal fun smoothRadar(argb: IntArray, grid: RadarCellGrid, width: Int, height: Int): IntArray? {
    val inWidth = grid.requestWidth
    val inHeight = grid.requestHeight
    if (argb.size != inWidth * inHeight || width <= 0 || height <= 0) return null

    val cells = premultiplied(argb)
    val softened = blur(cells, inWidth, inHeight)
    val across = scale(softened, rows = inHeight, from = inWidth, to = width, cells = grid.columns)
    val down = scale(across, rows = width, from = inHeight, to = height, cells = grid.rows)

    val out = IntArray(width * height)
    for (index in out.indices) {
        val alpha = down[index * CHANNELS + 3]
        val kept = edgeAlpha(alpha)
        if (alpha <= 0f || kept <= 0f) continue
        // Premultiplied colour over coverage is the straight colour, and the edge only changes the alpha.
        val red = channel(down[index * CHANNELS] / alpha)
        val green = channel(down[index * CHANNELS + 1] / alpha)
        val blue = channel(down[index * CHANNELS + 2] / alpha)
        out[index] = (channel(kept) shl 24) or (red shl 16) or (green shl 8) or blue
    }
    return out
}

/** How much of a pixel the sharpened edge keeps, from its softened coverage. */
internal fun edgeAlpha(alpha: Float): Float {
    val t = ((alpha - EDGE_LOW) / (EDGE_HIGH - EDGE_LOW)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * BYTE_MAX).roundToInt()

private fun premultiplied(argb: IntArray): FloatArray {
    val out = FloatArray(argb.size * CHANNELS)
    for (index in argb.indices) {
        val pixel = argb[index]
        val alpha = (pixel ushr 24) / BYTE_MAX.toFloat()
        out[index * CHANNELS] = ((pixel shr 16) and BYTE_MAX) / BYTE_MAX.toFloat() * alpha
        out[index * CHANNELS + 1] = ((pixel shr 8) and BYTE_MAX) / BYTE_MAX.toFloat() * alpha
        out[index * CHANNELS + 2] = (pixel and BYTE_MAX) / BYTE_MAX.toFloat() * alpha
        out[index * CHANNELS + 3] = alpha
    }
    return out
}

/** A small Gaussian across and then down, holding the edge pixel past the image's edge. */
private fun blur(pixels: FloatArray, width: Int, height: Int): FloatArray {
    val raw =
        FloatArray(2 * BLUR_RADIUS + 1) {
            exp(-((it - BLUR_RADIUS) * (it - BLUR_RADIUS)) / (2 * BLUR_CELLS * BLUR_CELLS))
        }
    val total = raw.sum()
    val weights = FloatArray(raw.size) { raw[it] / total }
    // Each pass writes its result transposed, so two passes run across and then down.
    return convolve(convolve(pixels, rows = height, columns = width, weights), rows = width, columns = height, weights)
}

/** Runs [weights], centered, along each row, clamping at the ends; the result is transposed. */
private fun convolve(pixels: FloatArray, rows: Int, columns: Int, weights: FloatArray): FloatArray {
    val radius = weights.size / 2
    val result = FloatArray(pixels.size)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val target = (column * rows + row) * CHANNELS
            for (offset in weights.indices) {
                val source = (row * columns + (column + offset - radius).coerceIn(0, columns - 1)) * CHANNELS
                for (channel in 0 until CHANNELS) result[target + channel] += pixels[source + channel] * weights[offset]
            }
        }
    }
    return result
}

/**
 * Scales each row from [from] to [to] pixels with a cubic B-spline, which is smooth through every cell rather than
 * passing through them in kinks. [to] covers [cells] cells, starting past the padding. The result is transposed, so a
 * second call scales the other way and puts the image back the right way round.
 */
private fun scale(pixels: FloatArray, rows: Int, from: Int, to: Int, cells: Int): FloatArray {
    val step = cells.toFloat() / to
    val bases = IntArray(to)
    val weights = FloatArray(to * SPLINE_TAPS)
    for (out in 0 until to) {
        // Cell centers sit at index + 0.5, and the first output pixel is past the padding.
        val position = RADAR_CELL_PADDING + (out + 0.5f) * step - 0.5f
        val base = floor(position).toInt()
        val t = position - base
        bases[out] = base - 1
        weights[out * SPLINE_TAPS] = (1 - t) * (1 - t) * (1 - t) / 6
        weights[out * SPLINE_TAPS + 1] = (3 * t * t * t - 6 * t * t + 4) / 6
        weights[out * SPLINE_TAPS + 2] = (-3 * t * t * t + 3 * t * t + 3 * t + 1) / 6
        weights[out * SPLINE_TAPS + 3] = t * t * t / 6
    }
    val result = FloatArray(rows * to * CHANNELS)
    for (row in 0 until rows) {
        for (out in 0 until to) {
            val target = (out * rows + row) * CHANNELS
            for (tap in 0 until SPLINE_TAPS) {
                val weight = weights[out * SPLINE_TAPS + tap]
                val source = (row * from + min(max(bases[out] + tap, 0), from - 1)) * CHANNELS
                for (channel in 0 until CHANNELS) result[target + channel] += pixels[source + channel] * weight
            }
        }
    }
    return result
}
