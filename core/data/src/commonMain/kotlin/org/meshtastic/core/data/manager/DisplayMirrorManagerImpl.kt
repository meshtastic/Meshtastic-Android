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
import org.meshtastic.sdk.DisplayMirrorFrame

/** This singleton outlives a session, so [reset] must be called when one ends. */
@Single
class DisplayMirrorManagerImpl : DisplayMirrorManager {

    private val _frame = MutableStateFlow<MirrorFrame?>(null)
    override val frame = _frame.asStateFlow()

    private val _palette = MutableStateFlow<MirrorPalette?>(null)
    override val palette = _palette.asStateFlow()

    private val rects = MirrorRectCompositor()

    override fun handleFrame(frame: DisplayMirrorFrame) {
        if (frame.format == DisplayFrame.Format.RGB565) {
            if (rects.composite(frame)) emitRectFrame(frame)
        } else if (isAcceptableMono(frame)) {
            _frame.value =
                MirrorFrame(
                    width = frame.width,
                    height = frame.height,
                    frameId = frame.frameId,
                    paletteSignature = frame.paletteSignature,
                    pixels = frame.data.toByteArray(),
                )
        }
    }

    override fun handlePalette(palette: DisplayPalette) {
        if (palette.regions.size > MAX_PALETTE_REGIONS) {
            Logger.w { "DisplayMirror: dropping palette ${palette.signature} with ${palette.regions.size} regions" }
            return
        }
        _palette.value =
            MirrorPalette(
                signature = palette.signature,
                defaultOnColor = palette.default_on_color,
                defaultOffColor = palette.default_off_color,
                regions = palette.regions,
            )
    }

    /**
     * Format must be MONO_VLSB and width/height must describe exactly the bytes delivered; a zero or lying dimension
     * would otherwise reach the renderer (aspectRatio requires > 0).
     */
    private fun isAcceptableMono(frame: DisplayMirrorFrame): Boolean {
        val size = frame.data.size.toLong()
        val expectedSize = frame.width.toLong() * ((frame.height.toLong() + PIXELS_PER_PAGE - 1) / PIXELS_PER_PAGE)
        val acceptable =
            frame.format == DisplayFrame.Format.MONO_VLSB &&
                frame.width > 0 &&
                frame.height > 0 &&
                expectedSize == size &&
                size <= MAX_FRAME_BYTES
        if (!acceptable) {
            Logger.w {
                "DisplayMirror: dropping bad frame (format=${frame.format} ${frame.width}x${frame.height} size=$size)"
            }
        }
        return acceptable
    }

    private fun emitRectFrame(frame: DisplayMirrorFrame) {
        val pixels = rects.snapshot() ?: return
        _frame.value =
            MirrorFrame(
                width = rects.width,
                height = rects.height,
                frameId = frame.frameId,
                paletteSignature = 0,
                pixels = pixels,
                format = MirrorFormat.RGB565,
            )
    }

    override fun reset() {
        rects.reset()
        _frame.value = null
    }

    private companion object {
        // Generous sanity cap: 320x240 at 1bpp is 9600 bytes.
        const val MAX_FRAME_BYTES = 16384

        // The firmware's region table caps at 48; anything past this is a bug or an attack.
        const val MAX_PALETTE_REGIONS = 512

        // MONO_VLSB packs 8 vertically adjacent pixels per byte (one "page" row).
        const val PIXELS_PER_PAGE = 8
    }
}
