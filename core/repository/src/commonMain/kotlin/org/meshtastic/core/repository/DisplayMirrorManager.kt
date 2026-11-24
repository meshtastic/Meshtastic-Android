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
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.sdk.DisplayMirrorFrame

/**
 * Holds the connected device's mirrored display while mirroring is enabled (see `AdminMessage.set_display_mirror`). The
 * SDK reassembles the firmware's chunks; this validates each complete frame against its declared geometry and
 * composites RGB565 partial-update rectangles onto the panel.
 */
interface DisplayMirrorManager {
    /** Latest complete frame, or null before the first one arrives. */
    val frame: StateFlow<MirrorFrame?>

    /** Latest complete color palette, or null when the device renders monochrome. */
    val palette: StateFlow<MirrorPalette?>

    /** Publishes a complete frame from the SDK, or drops it when its bytes do not match its geometry. */
    fun handleFrame(frame: DisplayMirrorFrame)

    /** Publishes a complete palette from the SDK. */
    fun handlePalette(palette: DisplayPalette)

    /**
     * Clears the latest frame and partial reassembly buffers. Call when a session ends or a fresh mirror UI opens, so a
     * previous device's final screen is never shown as if live. The palette outlives a reset because the device sends
     * it once per connection.
     */
    fun reset()
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

/** Pixel encodings a [MirrorFrame] can carry. */
enum class MirrorFormat {
    /** 1 bit per pixel, vertical LSB-first pages — byte index `x + (y / 8) * width`, bit index `y % 8`. */
    MONO_VLSB,

    /** 16 bits per pixel, little-endian RGB565, rows tightly packed. */
    RGB565,
}

/** One complete device framebuffer snapshot in [format]. */
data class MirrorFrame(
    val width: Int,
    val height: Int,
    val frameId: Int,
    val paletteSignature: Int,
    val pixels: ByteArray,
    val format: MirrorFormat = MirrorFormat.MONO_VLSB,
) {
    override fun equals(other: Any?): Boolean = other is MirrorFrame &&
        other.width == width &&
        other.height == height &&
        other.frameId == frameId &&
        other.paletteSignature == paletteSignature &&
        other.format == format &&
        other.pixels.contentEquals(pixels)

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + frameId
        result = 31 * result + paletteSignature
        result = 31 * result + format.ordinal
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
