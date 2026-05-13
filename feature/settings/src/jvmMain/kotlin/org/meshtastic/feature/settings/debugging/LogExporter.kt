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
package org.meshtastic.feature.settings.debugging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Composable
actual fun rememberLogExporter(logsProvider: suspend () -> List<DebugViewModel.UiMeshLog>): (fileName: String) -> Unit {
    val scope = rememberCoroutineScope()

    return { fileName ->
        scope.launch {
            val logs = logsProvider()
            if (logs.isEmpty()) {
                Logger.w { "MeshLog export aborted: no logs available" }
                return@launch
            }

            withContext(ioDispatcher) {
                // Run file dialog to ask user where to save
                val fileDialog = FileDialog(null as Frame?, "Export Logs", FileDialog.SAVE)
                fileDialog.file = fileName
                fileDialog.isVisible = true

                val directory = fileDialog.directory
                val selectedFile = fileDialog.file

                if (directory != null && selectedFile != null) {
                    val exportFile = File(directory, selectedFile)
                    try {
                        FileOutputStream(exportFile).use { fos ->
                            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer -> formatLogsTo(writer, logs) }
                        }
                        Logger.i { "MeshLog exported successfully to ${exportFile.absolutePath}" }
                    } catch (e: java.io.IOException) {
                        Logger.e(e) { "Failed to export logs to file: ${exportFile.absolutePath}" }
                    }
                } else {
                    Logger.w { "MeshLog export aborted: user canceled file dialog" }
                }
            }
        }
    }
}
