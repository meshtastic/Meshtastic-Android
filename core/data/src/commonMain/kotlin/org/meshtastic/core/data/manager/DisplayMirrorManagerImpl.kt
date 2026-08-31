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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFormat
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette

/**
 * Display frame reassembly state machine.
 *
 * Chunks of one frame arrive contiguously and in offset order (FromRadio is a reliable ordered stream), so a chunk with
 * a new `frame_id` or `offset == 0` starts a new frame and an out-of-sequence chunk drops the partial frame. Calls are
 * serialized by the single receive-loop collector in MeshServiceOrchestrator; this singleton's state outlives a
 * session, so [reset] must be called when one ends.
 */
@Single
class DisplayMirrorManagerImpl : DisplayMirrorManager {

    private val _frame = MutableStateFlow<MirrorFrame?>(null)
    override val frame = _frame.asStateFlow()

    private val _palette = MutableStateFlow<MirrorPalette?>(null)
    override val palette = _palette.asStateFlow()

    private var buffer: ByteArray? = null
    private var frameId = 0
    private var received = 0
    private var frameWidth = 0
    private var frameHeight = 0

    private var paletteRegions = mutableListOf<DisplayPalette.ColorRegion>()
    private var paletteSignature = 0
    private var paletteReceived = 0
    private var paletteDefaultOn = 0
    private var paletteDefaultOff = 0

    override fun handleIncomingFrame(chunk: DisplayFrame) {
        if (chunk.format == DisplayFrame.Format.RGB565 && chunk.rect_width > 0) {
            handleRectChunk(chunk)
        } else {
            handleMonoChunk(chunk)
        }
    }

    private fun handleMonoChunk(chunk: DisplayFrame) {
        val data = chunk.data_.toByteArray()
        val total = chunk.total_size

        if (!isAcceptableChunk(chunk, data.size)) return

        if (chunk.offset == 0) {
            buffer = ByteArray(total)
            frameId = chunk.frame_id
            received = 0
            // Geometry is authoritative on the first chunk; a later chunk may not transpose it.
            frameWidth = chunk.width
            frameHeight = chunk.height
        }

        val buf = buffer
        if (buf == null || !matchesCurrentFrame(chunk, buf.size)) {
            Logger.w { "DisplayMirror: bad chunk ${chunk.frame_id}@${chunk.offset}, expected $frameId@$received" }
            buffer = null
            return
        }

        data.copyInto(buf, chunk.offset)
        received += data.size

        if (received == total) {
            _frame.value =
                MirrorFrame(
                    width = frameWidth,
                    height = frameHeight,
                    frameId = frameId,
                    paletteSignature = chunk.palette_signature,
                    pixels = buf,
                )
            buffer = null
        }
    }

    override fun handleIncomingPalette(chunk: DisplayPalette) {
        if (chunk.region_offset == 0) {
            paletteRegions = mutableListOf()
            paletteSignature = chunk.signature
            paletteReceived = 0
            // Defaults are authoritative on the first chunk; later chunks may omit them.
            paletteDefaultOn = chunk.default_on_color
            paletteDefaultOff = chunk.default_off_color
        }
        if (!isAcceptablePaletteChunk(chunk)) {
            paletteRegions.clear()
            paletteReceived = -1 // poison until the next offset-0 chunk restarts
            return
        }
        paletteRegions.addAll(chunk.regions)
        paletteReceived += chunk.regions.size

        if (paletteReceived >= chunk.region_total) {
            _palette.value =
                MirrorPalette(
                    signature = chunk.signature,
                    defaultOnColor = paletteDefaultOn,
                    defaultOffColor = paletteDefaultOff,
                    regions = paletteRegions.toList(),
                )
        }
    }

    private fun matchesCurrentFrame(chunk: DisplayFrame, bufSize: Int): Boolean {
        val inSequence = chunk.frame_id == frameId && chunk.offset == received && bufSize == chunk.total_size
        val geometryStable = chunk.width == frameWidth && chunk.height == frameHeight
        return inSequence && geometryStable
    }

    /** Sequenced continuation of the current palette, within the region cap; logs and rejects anything else. */
    private fun isAcceptablePaletteChunk(chunk: DisplayPalette): Boolean {
        val withinCap =
            chunk.region_total <= MAX_PALETTE_REGIONS &&
                paletteReceived >= 0 &&
                paletteReceived + chunk.regions.size <= MAX_PALETTE_REGIONS
        val inSequence = chunk.signature == paletteSignature && chunk.region_offset == paletteReceived
        val acceptable = withinCap && inSequence
        if (!acceptable) {
            Logger.w { "DisplayMirror: bad palette chunk ${chunk.signature}@${chunk.region_offset}" }
        }
        return acceptable
    }

    /**
     * Format must be MONO_VLSB and width/height must describe exactly total_size bytes; a zero or lying dimension would
     * otherwise reach the renderer (aspectRatio requires > 0).
     */
    private fun isAcceptableChunk(chunk: DisplayFrame, dataSize: Int): Boolean {
        val total = chunk.total_size
        val expectedSize = chunk.width * ((chunk.height + PIXELS_PER_PAGE - 1) / PIXELS_PER_PAGE)
        val acceptable =
            chunk.format == DisplayFrame.Format.MONO_VLSB &&
                chunk.width > 0 &&
                chunk.height > 0 &&
                expectedSize == total &&
                total <= MAX_FRAME_BYTES &&
                chunk.offset + dataSize <= total
        if (!acceptable) {
            Logger.w {
                "DisplayMirror: dropping bad chunk (format=${chunk.format} ${chunk.width}x${chunk.height} " +
                    "offset=${chunk.offset} size=$dataSize total=$total)"
            }
        }
        return acceptable
    }

    // ── RGB565 dirty-rect stream (LVGL/MUI devices) ─────────────────────────
    // Rects composite into a persistent little-endian RGB565 canvas; frames
    // are emitted coalesced so a burst of rects costs one canvas copy.

    private var canvas: ByteArray? = null
    private var canvasWidth = 0
    private var canvasHeight = 0
    private var rectBuffer: ByteArray? = null
    private var rectReceived = 0
    private var rectFrameId = 0

    private fun handleRectChunk(chunk: DisplayFrame) {
        val data = chunk.data_.toByteArray()
        if (chunk.offset == 0) {
            rectBuffer = ByteArray(chunk.total_size)
            rectFrameId = chunk.frame_id
            rectReceived = 0
        }
        if (!isAcceptableRectChunk(chunk, data.size)) {
            rectBuffer = null
            return
        }
        val buf = rectBuffer ?: return
        data.copyInto(buf, chunk.offset)
        rectReceived += data.size
        if (rectReceived == chunk.total_size) {
            compositeRect(chunk, buf)
            rectBuffer = null
        }
    }

    private fun isAcceptableRectChunk(chunk: DisplayFrame, dataSize: Int): Boolean {
        val bytes = chunk.rect_width * chunk.rect_height * 2
        val withinPanel =
            chunk.width in 1..MAX_PANEL_EDGE &&
                chunk.height in 1..MAX_PANEL_EDGE &&
                chunk.rect_x + chunk.rect_width <= chunk.width &&
                chunk.rect_y + chunk.rect_height <= chunk.height
        val inSequence = rectBuffer != null && chunk.frame_id == rectFrameId && chunk.offset == rectReceived
        val acceptable =
            withinPanel && inSequence && bytes == chunk.total_size && chunk.offset + dataSize <= chunk.total_size
        if (!acceptable) {
            Logger.w {
                "DisplayMirror: dropping bad rect ${chunk.rect_width}x${chunk.rect_height} total=${chunk.total_size}"
            }
        }
        return acceptable
    }

    private fun compositeRect(chunk: DisplayFrame, rect: ByteArray) {
        if (canvas == null || canvasWidth != chunk.width || canvasHeight != chunk.height) {
            canvas = ByteArray(chunk.width * chunk.height * 2)
            canvasWidth = chunk.width
            canvasHeight = chunk.height
        }
        val target = canvas ?: return
        val rowBytes = chunk.rect_width * 2
        for (row in 0 until chunk.rect_height) {
            val src = row * rowBytes
            val dst = ((chunk.rect_y + row) * canvasWidth + chunk.rect_x) * 2
            rect.copyInto(target, dst, src, src + rowBytes)
        }
        // One emission per completed rect: rects are already flush-coalesced
        // device-side, and StateFlow conflates under a slow collector.
        emitCanvas(chunk.frame_id)
    }

    private fun emitCanvas(frameId: Int) {
        val target = canvas ?: return
        _frame.value =
            MirrorFrame(
                width = canvasWidth,
                height = canvasHeight,
                frameId = frameId,
                paletteSignature = 0,
                pixels = target.copyOf(),
                format = MirrorFormat.RGB565,
            )
    }

    override fun reset() {
        buffer = null
        received = 0
        canvas = null
        rectBuffer = null
        rectReceived = 0
        paletteRegions = mutableListOf()
        paletteReceived = 0
        paletteSignature = 0
        _frame.value = null
        _palette.value = null
    }

    private companion object {
        // Generous sanity cap: 320x240 at 1bpp is 9600 bytes.
        const val MAX_FRAME_BYTES = 16384

        // The firmware's region table caps at 48; anything past this is a bug or an attack.
        const val MAX_PALETTE_REGIONS = 512

        // Sanity bound for RGB565 panels (largest realistic is 800x480).
        const val MAX_PANEL_EDGE = 1024

        // MONO_VLSB packs 8 vertically adjacent pixels per byte (one "page" row).
        const val PIXELS_PER_PAGE = 8
    }
}
