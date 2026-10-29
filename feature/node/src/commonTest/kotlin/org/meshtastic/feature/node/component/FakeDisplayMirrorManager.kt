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
package org.meshtastic.feature.node.component

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette

internal class FakeDisplayMirrorManager : DisplayMirrorManager {
    override val frame: StateFlow<MirrorFrame?> = MutableStateFlow(null)
    override val palette: StateFlow<MirrorPalette?> = MutableStateFlow(null)

    var resets = 0

    override fun handleIncomingFrame(chunk: DisplayFrame) {}

    override fun handleIncomingPalette(chunk: DisplayPalette) {}

    override fun reset() {
        resets++
    }
}
