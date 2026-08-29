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
package org.meshtastic.feature.map.layers

import androidx.compose.runtime.Composable

/**
 * Returns a launcher that asks the user for a map file and hands it back as a [PickedMapFile].
 *
 * Android opens the storage-access document picker; desktop opens an AWT file dialog. Both hand back the same thing,
 * which is what lets the layers sheet be one composable rather than one per platform.
 */
@Composable expect fun rememberMapLayerPicker(onPick: (PickedMapFile) -> Unit): MapLayerPickerLauncher

/** Platform-agnostic handle for opening the picker. */
fun interface MapLayerPickerLauncher {
    fun pick()
}
