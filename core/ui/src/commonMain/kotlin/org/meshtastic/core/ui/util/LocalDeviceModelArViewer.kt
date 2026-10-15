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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Null where there is no AR runtime at all: desktop and the fdroid flavor. */
@Suppress("CompositionLocalAllowlist") // A host-provided capability, like the barcode and NFC providers.
val LocalDeviceModelArViewer = staticCompositionLocalOf<DeviceModelArViewer?> { null }

/** Places a device's real-size GLB model in the camera view. */
interface DeviceModelArViewer {
    /** False on a phone the AR runtime has ruled out. Asked when a model opens, never at app start. */
    @Composable fun isAvailable(): Boolean

    /** [notice] is the model's own copyright line, shown with it. */
    @Composable fun Content(glb: ByteArray, title: String, notice: String?, onDismiss: () -> Unit)
}
