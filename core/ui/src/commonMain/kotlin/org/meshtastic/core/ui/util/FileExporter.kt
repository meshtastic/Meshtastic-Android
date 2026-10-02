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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.repository.FileService

/**
 * Returns a launcher that asks the user where to save a file, then writes the bytes [content] produces there through
 * [FileService] and reports whether the export landed to [onResult]. [content] runs only after a destination is chosen;
 * returning null means there is nothing to export.
 */
@Composable
fun rememberFileExporter(
    content: suspend () -> ByteArray?,
    onResult: suspend (exported: Boolean) -> Unit = {},
): (fileName: String, mimeType: String) -> Unit {
    val fileService: FileService = koinInject()
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    val currentOnResult by rememberUpdatedState(onResult)
    return rememberSaveFileLauncher { uri ->
        scope.launch { currentOnResult(writeExport(fileService, uri, currentContent)) }
    }
}

/**
 * Writes the bytes [content] produces to [uri]. Returns false without writing when [content] returns null or throws,
 * and false when [FileService] reports the write failed.
 */
internal suspend fun writeExport(fileService: FileService, uri: CommonUri, content: suspend () -> ByteArray?): Boolean {
    val bytes =
        safeCatching { withContext(ioDispatcher) { content() } }
            .onFailure { e -> Logger.e(e) { "Could not produce the export" } }
            .getOrNull() ?: return false
    return fileService.write(uri) { it.write(bytes) }
}
