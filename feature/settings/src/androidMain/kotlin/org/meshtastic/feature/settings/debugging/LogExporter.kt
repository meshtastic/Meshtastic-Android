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
import org.meshtastic.core.resources.debug_logs_exported
import org.meshtastic.core.ui.util.showToast
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Composable
actual fun rememberLogExporter(contentProvider: suspend () -> String): (fileName: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLogsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { createdUri ->
            if (createdUri != null) {
                scope.launch { exportTextToUri(context, createdUri, contentProvider()) }
            }
        }
    return { fileName -> exportLogsLauncher.launch(fileName) }
}

private suspend fun exportTextToUri(context: Context, targetUri: Uri, content: String) = withContext(ioDispatcher) {
    try {
        if (content.isBlank()) {
            withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_failed, "No logs to export") }
            Logger.w { "Log export aborted: no content" }
            return@withContext
        }
        val stream = context.contentResolver.openOutputStream(targetUri)
        if (stream == null) {
            Logger.w { "Log export aborted: could not open output stream for $targetUri" }
            withContext(Dispatchers.Main) {
                context.showToast(Res.string.debug_export_failed, "Could not open file")
            }
            return@withContext
        }
        stream.use { os -> OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer -> writer.write(content) } }
        Logger.i { "Logs exported successfully to $targetUri" }
        withContext(Dispatchers.Main) { context.showToast(Res.string.debug_logs_exported) }
    } catch (e: java.io.IOException) {
        Logger.e(e) { "Failed to export logs to URI: $targetUri" }
        withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_failed, e.message ?: "") }
    }
}

/**
 * Dumps this app's own logcat, filtered to our process id via `--pid` (API 24+, minSdk is 26). Without READ_LOGS the OS
 * already limits us to our own entries, but `--pid` guarantees it even if that permission is ever granted (e.g. via adb
 * on an emulator) so a shared bug report can't leak other apps' logs. Best-effort: a capture failure returns a marker
 * rather than throwing. ProcessBuilder with a merged stderr avoids a pipe-buffer deadlock, and the bounded wait keeps a
 * stuck capture from tying up the IO thread. ponytail: `-t 5000` tail-caps the dump so the exported file stays sane.
 */
actual fun captureAppLogcat(): String = try {
    val pid = android.os.Process.myPid()
    val process =
        ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid", "-t", "5000").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
    output
} catch (e: java.io.IOException) {
    Logger.e(e) { "Failed to capture logcat" }
    "logcat capture failed: ${e.message}"
}

private const val LOGCAT_TIMEOUT_SECONDS = 5L
