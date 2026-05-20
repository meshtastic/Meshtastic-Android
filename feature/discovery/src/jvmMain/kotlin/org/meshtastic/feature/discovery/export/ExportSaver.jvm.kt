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
package org.meshtastic.feature.discovery.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberExportSaver(): ExportSaverLauncher {
    val scope = rememberCoroutineScope()
    return ExportSaverLauncher { result ->
        scope.launch {
            withContext(Dispatchers.IO) {
                @Suppress("TooGenericExceptionCaught")
                try {
                    val chooser =
                        JFileChooser().apply {
                            dialogTitle = "Save Discovery Report"
                            selectedFile = File(result.fileName)
                            val ext = result.fileName.substringAfterLast('.', "txt")
                            fileFilter = FileNameExtensionFilter("${ext.uppercase()} files", ext)
                        }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        chooser.selectedFile.writeBytes(result.content)
                    }
                } catch (e: Exception) {
                    Logger.e(throwable = e) { "Failed to save export file on desktop" }
                }
            }
        }
    }
}
