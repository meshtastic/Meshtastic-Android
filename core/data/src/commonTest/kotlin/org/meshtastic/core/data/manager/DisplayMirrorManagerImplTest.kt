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
import org.meshtastic.sdk.DisplayMirrorFrame
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayMirrorManagerImplTest {

    private val manager = DisplayMirrorManagerImpl()

    // 128x64 MONO_VLSB frame = 1024 bytes.
    private fun frame(
        data: ByteArray,
        frameId: Int = 1,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        format: DisplayFrame.Format = DisplayFrame.Format.MONO_VLSB,
        paletteSignature: Int = 0,
    ) = DisplayMirrorFrame(
        width = width,
        height = height,
        format = format,
        frameId = frameId,
        data = data.toByteString(),
        paletteSignature = paletteSignature,
    )

    private fun bytes(size: Int, fill: Int) = ByteArray(size) { fill.toByte() }

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

    @Test
    fun `publishes a complete mono frame with its palette signature`() {
        manager.handleFrame(frame(bytes(FRAME_BYTES, 3), frameId = 6, paletteSignature = 42))

        val frame = manager.frame.value!!
        assertEquals(WIDTH, frame.width)
        assertEquals(HEIGHT, frame.height)
        assertEquals(6, frame.frameId)
        assertEquals(42, frame.paletteSignature)
        assertContentEquals(bytes(FRAME_BYTES, 3), frame.pixels)
    }

    @Test
    fun `rejects geometry that does not match the delivered bytes`() {
        manager.handleFrame(frame(bytes(256, 1), width = 64, height = 64)) // 64x64 needs 512
        manager.handleFrame(frame(ByteArray(0), width = 0, height = 64))
        manager.handleFrame(frame(ByteArray(0), width = 128, height = 0))

        assertNull(manager.frame.value)
    }

    @Test
    fun `rejects geometry whose byte count overflows`() {
        manager.handleFrame(frame(ByteArray(0), width = 65536, height = 524288))
        assertNull(manager.frame.value)
    }

    @Test
    fun `rejects unsupported format`() {
        manager.handleFrame(
            frame(bytes(256, 1), width = 64, height = 32, format = DisplayFrame.Format.FORMAT_UNSPECIFIED),
        )

        assertNull(manager.frame.value)
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
    fun `reset clears frames and palettes`() {
        manager.handleFrame(frame(bytes(256, 7), width = 64, height = 32))
        manager.handlePalette(palette(signature = 9, regions = emptyList()))

        manager.reset()

        assertNull(manager.frame.value)
        assertNull(manager.palette.value)
    }

    @Test
    fun `an over-cap palette leaves the published palette intact`() {
        manager.handlePalette(palette(signature = 5, regions = listOf(region)))
        manager.handlePalette(palette(signature = 6, regions = List(513) { region }))

        assertEquals(5, manager.palette.value?.signature)
    }

    // ── RGB565 dirty-rect path ──────────────────────────────────────────────

    private fun rect(
        data: ByteArray,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        rectX: Int = 0,
        rectY: Int = 0,
        rectW: Int = 10,
        rectH: Int = 10,
    ) = DisplayMirrorFrame(
        width = width,
        height = height,
        format = DisplayFrame.Format.RGB565,
        frameId = 1,
        data = data.toByteString(),
        rectX = rectX,
        rectY = rectY,
        rectWidth = rectW,
        rectHeight = rectH,
    )

    @Test
    fun `composites a completed rect into an RGB565 frame`() {
        manager.handleFrame(rect(bytes(200, 0x11), rectX = 1, rectY = 2))

        val frame = manager.frame.value!!
        assertEquals(MirrorFormat.RGB565, frame.format)
        assertEquals(WIDTH * HEIGHT * 2, frame.pixels.size)
        val firstRowStart = (2 * WIDTH + 1) * 2
        assertEquals(0x11.toByte(), frame.pixels[firstRowStart])
        assertEquals(0.toByte(), frame.pixels[firstRowStart - 1])
    }

    @Test
    fun `rejects a rect whose size does not match its geometry`() {
        manager.handleFrame(rect(bytes(199, 1)))

        assertNull(manager.frame.value)
    }

    @Test
    fun `rejects a rect that would overflow the panel`() {
        manager.handleFrame(rect(bytes(200, 1), rectX = WIDTH - 2))
        manager.handleFrame(rect(bytes(200, 1), rectY = Int.MAX_VALUE))

        assertNull(manager.frame.value)
    }

    @Test
    fun `rejects an oversized panel without allocating`() {
        manager.handleFrame(rect(bytes(8, 1), width = 4000, height = 4000, rectW = 4000, rectH = 4000))

        assertNull(manager.frame.value)
    }

    private companion object {
        const val WIDTH = 128
        const val HEIGHT = 64
        const val FRAME_BYTES = 1024
    }
}
