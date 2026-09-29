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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.meshtastic.core.ui.util.rememberFileExporter

/** Returns a launcher that saves [ExportResult.Success] content to a file the user picks. */
@Composable
fun rememberExportSaver(): ExportSaverLauncher {
    var pending by remember { mutableStateOf<ExportResult.Success?>(null) }
    val export = rememberFileExporter { pending?.content ?: ByteArray(0) }
    return remember(export) {
        ExportSaverLauncher { result ->
            pending = result
            export(result.fileName, result.mimeType)
        }
    }
}

/** Platform-agnostic handle for triggering a file-save from export data. */
fun interface ExportSaverLauncher {
    fun save(result: ExportResult.Success)
}
