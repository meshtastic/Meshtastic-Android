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
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette

/**
 * Display frame reassembly state machine.
 *
 * Chunks of one frame arrive contiguously and in offset order (FromRadio is a reliable ordered stream), so a chunk with
 * a new `frame_id` or `offset == 0` starts a new frame and an out-of-sequence chunk drops the partial frame. Calls are
 * serialized by the single receive-loop collector in MeshServiceOrchestrator (recreated per session).
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

    private var paletteRegions = mutableListOf<DisplayPalette.ColorRegion>()
    private var paletteSignature = 0
    private var paletteReceived = 0
    private var paletteDefaultOn = 0
    private var paletteDefaultOff = 0

    override fun handleIncomingFrame(chunk: DisplayFrame) {
        val data = chunk.data_.toByteArray()
        val total = chunk.total_size

        if (!isAcceptableChunk(chunk, data.size)) return

        if (chunk.offset == 0) {
            buffer = ByteArray(total)
            frameId = chunk.frame_id
            received = 0
        }

        val buf = buffer
        if (buf == null || chunk.frame_id != frameId || chunk.offset != received || buf.size != total) {
            Logger.w { "DisplayMirror: bad chunk ${chunk.frame_id}@${chunk.offset}, expected $frameId@$received" }
            buffer = null
            return
        }

        data.copyInto(buf, chunk.offset)
        received += data.size

        if (received == total) {
            _frame.value =
                MirrorFrame(
                    width = chunk.width,
                    height = chunk.height,
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
        if (chunk.signature != paletteSignature || chunk.region_offset != paletteReceived) {
            Logger.w { "DisplayMirror: bad palette chunk ${chunk.signature}@${chunk.region_offset}" }
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

    private companion object {
        // Generous sanity cap: 320x240 at 1bpp is 9600 bytes.
        const val MAX_FRAME_BYTES = 16384

        // MONO_VLSB packs 8 vertically adjacent pixels per byte (one "page" row).
        const val PIXELS_PER_PAGE = 8
    }
}
