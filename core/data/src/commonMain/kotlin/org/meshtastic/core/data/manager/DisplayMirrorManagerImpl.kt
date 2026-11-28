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
import org.meshtastic.sdk.DisplayImage

/** This singleton outlives a session, so [reset] must be called when one ends. */
@Single
class DisplayMirrorManagerImpl : DisplayMirrorManager {

    private val _frame = MutableStateFlow<MirrorFrame?>(null)
    override val frame = _frame.asStateFlow()

    private val _palette = MutableStateFlow<MirrorPalette?>(null)
    override val palette = _palette.asStateFlow()

    override fun handleImage(image: DisplayImage) {
        _frame.value =
            MirrorFrame(
                width = image.width,
                height = image.height,
                frameId = image.frameId,
                paletteSignature = image.paletteSignature,
                pixels = image.pixels.toByteArray(),
                format =
                if (image.format == DisplayFrame.Format.RGB565) MirrorFormat.RGB565 else MirrorFormat.MONO_VLSB,
            )
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

    override fun reset() {
        _frame.value = null
    }

    private companion object {
        // The firmware's region table caps at 48; anything past this is a bug or an attack.
        const val MAX_PALETTE_REGIONS = 512
    }
}
