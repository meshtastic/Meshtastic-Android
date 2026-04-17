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

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher

@Composable
actual fun rememberDataPackageExporter(dataPackageProvider: suspend () -> ByteArray): (fileName: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { createdUri ->
            if (createdUri != null) {
                scope.launch { exportZipToUri(context, createdUri, dataPackageProvider()) }
            }
        }
    return { fileName -> exportLauncher.launch(fileName) }
}

private suspend fun exportZipToUri(context: Context, targetUri: Uri, data: ByteArray) = withContext(ioDispatcher) {
    try {
        context.contentResolver.openOutputStream(targetUri)?.use { os -> os.write(data) }
        Logger.i { "TAK data package exported successfully to $targetUri" }
    } catch (e: java.io.IOException) {
        Logger.e(e) { "Failed to export TAK data package to URI: $targetUri" }
    }
}
