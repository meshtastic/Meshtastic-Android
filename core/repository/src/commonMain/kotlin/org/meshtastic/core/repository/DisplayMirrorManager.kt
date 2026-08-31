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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette

/**
 * Reassembles the chunked [DisplayFrame] stream from the connected device into complete framebuffer snapshots.
 *
 * The device streams its 1bpp framebuffer as `FromRadio.display_frame` chunks while display mirroring is enabled (see
 * `AdminMessage.set_display_mirror`). Chunks of one frame share a `frame_id`; a frame is complete when `offset +
 * data.size == total_size`.
 */
interface DisplayMirrorManager {
    /** Latest completely reassembled frame, or null before the first one arrives. */
    val frame: StateFlow<MirrorFrame?>

    /** Latest completely reassembled color palette, or null when the device renders monochrome. */
    val palette: StateFlow<MirrorPalette?>

    /** Routes an incoming display frame chunk from the device to the reassembly state machine. */
    fun handleIncomingFrame(chunk: DisplayFrame)

    /** Routes an incoming palette chunk from the device to the reassembly state machine. */
    fun handleIncomingPalette(chunk: DisplayPalette)
}

/**
 * A complete color palette for [MirrorFrame]s whose `paletteSignature` matches [signature]: per-region RGB565 on/off
 * colors (later regions override earlier ones where they overlap) plus defaults for pixels outside all regions.
 */
data class MirrorPalette(
    val signature: Int,
    val defaultOnColor: Int,
    val defaultOffColor: Int,
    val regions: List<DisplayPalette.ColorRegion>,
)

/**
 * One complete device framebuffer snapshot.
 *
 * [pixels] is MONO_VLSB: 1 bit per pixel in vertical LSB-first pages — byte index = `x + (y / 8) * width`, bit index =
 * `y % 8`.
 */
data class MirrorFrame(
    val width: Int,
    val height: Int,
    val frameId: Int,
    val paletteSignature: Int,
    val pixels: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is MirrorFrame &&
        other.width == width &&
        other.height == height &&
        other.frameId == frameId &&
        other.paletteSignature == paletteSignature &&
        other.pixels.contentEquals(pixels)

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + frameId
        result = 31 * result + paletteSignature
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
