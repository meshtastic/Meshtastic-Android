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
package org.meshtastic.desktop.tray

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrayIconRasterTest {

    @Test
    fun `packs a pixel as ARGB in network byte order`() {
        // 0xAARRGGBB in, A R G B out — not the host-endian BGRA that Skia's N32 buffer holds.
        val bytes = argbPixelsToSniBytes(intArrayOf(0x12345678))

        assertEquals(listOf(0x12, 0x34, 0x56, 0x78), bytes.map { it.toInt() and 0xFF })
    }

    @Test
    fun `preserves a fully opaque white and a fully transparent pixel`() {
        val bytes = argbPixelsToSniBytes(intArrayOf(-0x1, 0x00000000))

        assertEquals(listOf(0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00), bytes.map { it.toInt() and 0xFF })
    }

    @Test
    fun `emits four bytes per pixel with no row padding`() {
        val pixels = IntArray(7) { 0x11223344 }

        assertEquals(pixels.size * 4, argbPixelsToSniBytes(pixels).size)
    }

    @Test
    fun `packs an empty raster to an empty payload`() {
        assertTrue(argbPixelsToSniBytes(IntArray(0)).isEmpty())
    }

    @Test
    fun `offers a panel size and a larger size to the host`() {
        assertEquals(TRAY_ICON_SIZES.distinct(), TRAY_ICON_SIZES)
        assertTrue(TRAY_ICON_SIZES.all { it > 0 })
        assertTrue(TRAY_ICON_SIZES.size >= 2, "a host picking between sizes needs more than one")
    }

    @Test
    fun `rasterizes the real tray glyphs to something visible`() {
        // The blank-tray regression: the glyphs declare absolute width/height and no viewBox, so a render
        // that does not scale them captures only their transparent top-left corner and every alpha byte
        // comes back zero. The host accepts that pixmap happily and draws nothing.
        listOf(TRAY_ICON_DARK, TRAY_ICON_LIGHT).forEach { resource ->
            val rasters = loadTrayIconRasters(resource)
            assertEquals(TRAY_ICON_SIZES.size, rasters.size, "$resource produced no rasters")
            rasters.forEach { raster ->
                assertEquals(raster.width * raster.height * 4, raster.argbBigEndian.size)
                val opaque = (raster.argbBigEndian.indices step 4).count { raster.argbBigEndian[it] != 0.toByte() }
                assertTrue(opaque > 0, "$resource at ${raster.width}px rasterized fully transparent")
            }
        }
    }

    @Test
    fun `returns no rasters when the icon resource is missing rather than throwing`() {
        // An icon failure must never take down the tray, because the tray is the only way to quit.
        assertTrue(loadTrayIconRasters("definitely_not_a_real_tray_icon.svg").isEmpty())
    }
}
