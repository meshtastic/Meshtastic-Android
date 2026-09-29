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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.meshtastic.core.repository.FileService

/**
 * Returns a launcher that asks the user where to save a file, then writes the bytes [content] produces there through
 * [FileService]. [content] runs only after a destination is chosen, inside the write, so a failure producing it is
 * logged by [FileService] like any write failure.
 */
@Composable
fun rememberFileExporter(content: suspend () -> ByteArray): (fileName: String, mimeType: String) -> Unit {
    val fileService: FileService = koinInject()
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    return rememberSaveFileLauncher { uri -> scope.launch { fileService.write(uri) { it.write(currentContent()) } } }
}
