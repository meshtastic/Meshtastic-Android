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
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberMapLayerPicker(onPick: (PickedMapFile) -> Unit): MapLayerPickerLauncher {
    val scope = rememberCoroutineScope()
    return MapLayerPickerLauncher {
        scope.launch {
            val chosen =
                withContext(Dispatchers.IO) {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        // AWT's dialog rather than Swing's chooser: it is the platform's own, which matters for a
                        // file the user is hunting for in Downloads. `LogExporter` and `PrefExporter` use it too.
                        val dialog =
                            FileDialog(null as Frame?, "Import Map Layer", FileDialog.LOAD).apply {
                                // Filtered by extension rather than by a filter callback, which Windows ignores.
                                file = "*.geojson;*.json;*.kml;*.kmz"
                                isVisible = true
                            }
                        dialog.file?.let { File(dialog.directory ?: "", it) }
                    } catch (e: Exception) {
                        Logger.e(throwable = e) { "Failed to open the map layer picker" }
                        null
                    }
                }
            chosen ?: return@launch
            onPick(
                PickedMapFile(
                    displayName = chosen.name,
                    extensionOrMime = chosen.extension.ifBlank { null },
                    read = {
                        withContext(Dispatchers.IO) {
                            @Suppress("TooGenericExceptionCaught")
                            try {
                                chosen.readBytes()
                            } catch (e: Exception) {
                                Logger.e(throwable = e) { "Failed to read the picked map layer" }
                                null
                            }
                        }
                    },
                ),
            )
        }
    }
}
