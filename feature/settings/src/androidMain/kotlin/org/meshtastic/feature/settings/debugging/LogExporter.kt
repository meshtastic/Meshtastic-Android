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

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.debug_export_failed
import org.meshtastic.core.resources.debug_export_success
import org.meshtastic.core.ui.util.showToast
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Composable
actual fun rememberLogExporter(logsProvider: suspend () -> List<DebugViewModel.UiMeshLog>): (fileName: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLogsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { createdUri ->
            if (createdUri != null) {
                scope.launch { exportAllLogsToUri(context, createdUri, logsProvider()) }
            }
        }
    return { fileName -> exportLogsLauncher.launch(fileName) }
}

private suspend fun exportAllLogsToUri(context: Context, targetUri: Uri, logs: List<DebugViewModel.UiMeshLog>) =
    withContext(ioDispatcher) {
        try {
            if (logs.isEmpty()) {
                withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_failed, "No logs to export") }
                Logger.w { "MeshLog export aborted: no logs available" }
                return@withContext
            }

            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer -> formatLogsTo(writer, logs) }
            }
            Logger.i { "MeshLog exported successfully to $targetUri" }
            withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_success, logs.size) }
        } catch (e: java.io.IOException) {
            Logger.e(e) { "Failed to export logs to URI: $targetUri" }
            withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_failed, e.message ?: "") }
        }
    }
