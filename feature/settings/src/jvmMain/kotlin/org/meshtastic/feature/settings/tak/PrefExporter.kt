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
package org.meshtastic.feature.settings.tak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberDataPackageExporter(dataPackageProvider: suspend () -> ByteArray): (fileName: String) -> Unit {
    val scope = rememberCoroutineScope()
    return { fileName ->
        scope.launch {
            runCatching {
                val fileDialog =
                    FileDialog(null as Frame?, "Export TAK Data Package", FileDialog.SAVE).apply {
                        file = fileName
                        isVisible = true
                    }

                val directory = fileDialog.directory
                val file = fileDialog.file

                if (directory != null && file != null) {
                    val targetFile = File(directory, file)
                    val data = dataPackageProvider()
                    withContext(ioDispatcher) { targetFile.writeBytes(data) }
                    Logger.i { "TAK data package exported successfully to ${targetFile.absolutePath}" }
                }
            }
                .onFailure { e -> Logger.e(e) { "Failed to export TAK data package" } }
        }
    }
}
