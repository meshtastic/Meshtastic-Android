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
package org.meshtastic.core.data.manager

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.repository.MirrorFormat
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.sdk.DisplayImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayMirrorManagerImplTest {

    private val manager = DisplayMirrorManagerImpl()

    private fun palette(signature: Int, regions: List<DisplayPalette.ColorRegion>) = DisplayPalette.Builder()
        .also { wb ->
            wb.signature = signature
            wb.default_on_color = 0xFFFF
            wb.region_total = regions.size
            wb.regions = regions
        }
        .build()

    private val region =
        DisplayPalette.ColorRegion.Builder()
            .also { wb ->
                wb.width = 8
                wb.height = 8
            }
            .build()

    // Regression: the format mapping inverted, rendering RGB565 as 1-bit noise.
    @Test
    fun `maps a mono image to a mono frame with its palette signature`() {
        val pixels = ByteArray(256) { 3 }
        manager.handleImage(DisplayImage(64, 32, DisplayFrame.Format.MONO_VLSB, 6, 42, pixels.toByteString()))

        val frame = manager.frame.value!!
        assertEquals(64, frame.width)
        assertEquals(32, frame.height)
        assertEquals(6, frame.frameId)
        assertEquals(42, frame.paletteSignature)
        assertEquals(MirrorFormat.MONO_VLSB, frame.format)
        assertContentEquals(pixels, frame.pixels)
    }

    @Test
    fun `maps an RGB565 image to an RGB565 frame`() {
        val pixels = ByteArray(64) { 5 }
        manager.handleImage(DisplayImage(8, 4, DisplayFrame.Format.RGB565, 3, 0, pixels.toByteString()))

        val frame = manager.frame.value!!
        assertEquals(8, frame.width)
        assertEquals(4, frame.height)
        assertEquals(3, frame.frameId)
        assertEquals(MirrorFormat.RGB565, frame.format)
        assertContentEquals(pixels, frame.pixels)
    }

    @Test
    fun `publishes a complete palette`() {
        manager.handlePalette(palette(signature = 42, regions = listOf(region, region, region)))

        val palette = manager.palette.value!!
        assertEquals(42, palette.signature)
        assertEquals(0xFFFF, palette.defaultOnColor)
        assertEquals(3, palette.regions.size)
    }

    @Test
    fun `reset clears the frame but keeps the palette the device sends once per connection`() {
        val pixels = ByteArray(256) { 7 }.toByteString()
        manager.handleImage(DisplayImage(64, 32, DisplayFrame.Format.MONO_VLSB, 1, 0, pixels))
        manager.handlePalette(palette(signature = 42, regions = emptyList()))

        manager.reset()

        assertNull(manager.frame.value)
        assertEquals(42, manager.palette.value?.signature)
    }

    @Test
    fun `an over-cap palette leaves the published palette intact`() {
        manager.handlePalette(palette(signature = 5, regions = listOf(region)))
        manager.handlePalette(palette(signature = 6, regions = List(513) { region }))

        assertEquals(5, manager.palette.value?.signature)
    }
}
