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

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberExportSaver(): ExportSaverLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingExport = remember { mutableStateOf<ExportResult.Success?>(null) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            val export = pendingExport.value ?: return@rememberLauncherForActivityResult
            pendingExport.value = null
            scope.launch {
                withContext(Dispatchers.IO) {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(export.content) }
                    } catch (e: Exception) {
                        Logger.e(throwable = e) { "Failed to write export file" }
                    }
                }
            }
        }

    return ExportSaverLauncher { result ->
        pendingExport.value = result
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = result.mimeType
                putExtra(Intent.EXTRA_TITLE, result.fileName)
            }
        launcher.launch(intent)
    }
}
