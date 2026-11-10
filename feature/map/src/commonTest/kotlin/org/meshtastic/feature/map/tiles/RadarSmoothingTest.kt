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
package org.meshtastic.feature.map.tiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadarSmoothingTest {
    private val radar = MapTileCatalogue.NoaaRadar.spec

    private fun url(zoom: Int, x: Int = 1 shl (zoom - 1), y: Int = 1 shl (zoom - 1)) = radar.tileUrl(x, y, zoom)!!

    private fun grid(zoom: Int): RadarCellGrid {
        val shift = ORIGIN_SHIFT_METERS
        val span = 2 * shift / (1 shl zoom)
        val x = 1 shl (zoom - 1)
        val west = -shift + x * span
        val north = shift - x * span
        return checkNotNull(RadarCellGrid.of(west, north - span, west + span, north, outputPixels = 512))
    }

    private fun cells(grid: RadarCellGrid, lit: List<Pair<Int, Int>>): IntArray {
        val out = IntArray(grid.requestWidth * grid.requestHeight)
        for ((column, row) in lit) out[row * grid.requestWidth + column] = RAIN
        return out
    }

    @Test
    fun `a tile pixel coarser than a cell is left to the server`() {
        assertNull(RadarSmoothRequest.from(url(zoom = 6)))
        assertNull(RadarSmoothRequest.from(url(zoom = 4)))
    }

    @Test
    fun `a tile pixel finer than a cell asks for one pixel per cell with padding`() {
        val request = assertNotNull(RadarSmoothRequest.from(url(zoom = 9)))
        val columns = (2 * ORIGIN_SHIFT_METERS / (1 shl 9) / RADAR_CELL_METERS).let { kotlin.math.round(it).toInt() }
        assertTrue("WIDTH=${columns + 2 * RADAR_CELL_PADDING}&" in request.paddedUrl, request.paddedUrl)
        assertFalse("WIDTH=256" in request.paddedUrl)
        val rows = checkNotNull(Regex("HEIGHT=(\\d+)").find(request.paddedUrl)).groupValues[1].toInt()
        assertTrue(rows > 2 * RADAR_CELL_PADDING, request.paddedUrl)
        val box =
            checkNotNull(Regex("BBOX=([^&]+)").find(request.paddedUrl)).groupValues[1].split(',').map { it.toDouble() }
        val original =
            checkNotNull(Regex("BBOX=([^&]+)").find(url(zoom = 9))).groupValues[1].split(',').map { it.toDouble() }
        assertTrue(box[0] < original[0] && box[1] < original[1] && box[2] > original[2] && box[3] > original[3])
        assertTrue(request.paddedUrl.startsWith("https://opengeo.ncep.noaa.gov/"))
    }

    @Test
    fun `smoothing starts where the drawn 512 pixel tile is finer than a cell`() {
        assertNull(RadarSmoothRequest.from(url(zoom = 6)))
        assertNotNull(RadarSmoothRequest.from(url(zoom = 7)))
    }

    @Test
    fun `padded box grows by the padding in cells on every side`() {
        val shift = ORIGIN_SHIFT_METERS
        val span = 2 * shift / (1 shl 9)
        val x = 1 shl 8
        val west = -shift + x * span
        val grid = checkNotNull(RadarCellGrid.of(west, -span, west + span, 0.0, outputPixels = 512))
        val cell = span / grid.columns
        assertEquals(west - RADAR_CELL_PADDING * cell, grid.paddedMinX, 1e-6)
        assertEquals(west + span + RADAR_CELL_PADDING * cell, grid.paddedMaxX, 1e-6)
    }

    @Test
    fun `only the NOAA radar layer is smoothed`() {
        assertNull(RadarSmoothRequest.from("https://tile.openweathermap.org/map/precipitation_new/9/1/1.png?appid=k"))
        assertNull(RadarSmoothRequest.from("https://example.com/wms?LAYERS=other&BBOX=0,0,1,1&WIDTH=256&HEIGHT=256"))
        assertFalse(RadarSmoothRequest.isRadarUrl("https://example.com/conus_bref_qcd"))
        assertTrue(RadarSmoothRequest.isRadarUrl(url(zoom = 9)))
    }

    @Test
    fun `a single cell is speckle and fades out`() {
        val grid = grid(zoom = 9)
        val centre = RADAR_CELL_PADDING + grid.columns / 2 to RADAR_CELL_PADDING + grid.rows / 2
        val out = assertNotNull(smoothRadar(cells(grid, listOf(centre)), grid, 512, 512))
        assertEquals(0, out.maxOf { it ushr 24 })
    }

    @Test
    fun `a block of cells keeps an opaque core in the rain colour`() {
        val grid = grid(zoom = 9)
        val cx = RADAR_CELL_PADDING + grid.columns / 2
        val cy = RADAR_CELL_PADDING + grid.rows / 2
        val block = (-1..1).flatMap { dy -> (-1..1).map { dx -> cx + dx to cy + dy } }
        val out = assertNotNull(smoothRadar(cells(grid, block), grid, 512, 512))
        val core = out[(512 * 0.5).toInt() * 512 + 256]
        assertEquals(255, core ushr 24)
        assertEquals(RAIN and 0xFFFFFF, core and 0xFFFFFF)
        assertEquals(0, out[0] ushr 24)
    }

    @Test
    fun `a faint pixel keeps its straight colour through premultiplying`() {
        val grid = grid(zoom = 9)
        val faint = (0x99 shl 24) or 0x336699
        val out = assertNotNull(smoothRadar(IntArray(grid.requestWidth * grid.requestHeight) { faint }, grid, 64, 64))
        // Alpha 0.6 sits on the edge curve's upper end, so the pixel is kept and its colour is unchanged.
        assertEquals(0x336699, out[32 * 64 + 32] and 0xFFFFFF)
    }

    @Test
    fun `an image of the wrong size is refused`() {
        val grid = grid(zoom = 9)
        assertNull(smoothRadar(IntArray(10), grid, 512, 512))
    }

    @Test
    fun `the edge is drawn where the rain is half there`() {
        assertEquals(0f, edgeAlpha(0.3f))
        assertEquals(0.5f, edgeAlpha(0.5f), 1e-6f)
        assertEquals(1f, edgeAlpha(0.7f))
    }

    @Test
    fun `neighboring tiles agree where they meet`() {
        // A soft edge across the seam, sampled the way the server does: one value per padded cell centre.
        val zoom = 9
        val y = 193
        val span = 2 * ORIGIN_SHIFT_METERS / (1 shl zoom)
        val seam = -ORIGIN_SHIFT_METERS + 257 * span
        val rampMeters = 12 * RADAR_CELL_METERS
        fun tile(x: Int): IntArray {
            val west = -ORIGIN_SHIFT_METERS + x * span
            val north = ORIGIN_SHIFT_METERS - y * span
            val grid = checkNotNull(RadarCellGrid.of(west, north - span, west + span, north, outputPixels = 512))
            val cellWidth = (grid.paddedMaxX - grid.paddedMinX) / grid.requestWidth
            val image =
                IntArray(grid.requestWidth * grid.requestHeight) {
                    val centre = grid.paddedMinX + (it % grid.requestWidth + 0.5) * cellWidth
                    val alpha = ((centre - seam) / rampMeters + 0.5).coerceIn(0.0, 1.0)
                    ((alpha * 255).toInt() shl 24) or (RAIN and 0xFFFFFF)
                }
            return assertNotNull(smoothRadar(image, grid, 512, 512))
        }
        val left = tile(256)
        val right = tile(257)
        for (row in 0 until 512 step 16) {
            val a = left[row * 512 + 511] ushr 24
            val b = right[row * 512] ushr 24
            assertTrue(kotlin.math.abs(a - b) <= SEAM_TOLERANCE, "row $row: $a vs $b")
        }
    }

    private companion object {
        const val SEAM_TOLERANCE = 30
        const val RAIN = 0xFFFF0000.toInt()
    }
}
