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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.debug_export_failed
import org.meshtastic.core.resources.debug_logs_exported
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.ui.util.rememberFileExporter
import org.meshtastic.core.ui.util.rememberShowToast

/**
 * Remembers a launcher that writes [contentProvider]'s text to a user-chosen file and toasts the outcome. Blank text is
 * reported as a failure rather than written as an empty file.
 */
@Composable
fun rememberLogExporter(contentProvider: suspend () -> String): (fileName: String) -> Unit {
    val showToast = rememberShowToast()
    var requestedFileName by rememberSaveable { mutableStateOf("") }
    val export =
        rememberFileExporter(
            content = { contentProvider().takeIf { it.isNotBlank() }?.encodeToByteArray() },
            onResult = { exported ->
                showToast(
                    if (exported) {
                        getStringSuspend(Res.string.debug_logs_exported)
                    } else {
                        getStringSuspend(Res.string.debug_export_failed, requestedFileName)
                    },
                )
            },
        )
    return remember(export) {
        { fileName ->
            requestedFileName = fileName
            export(fileName, "text/plain")
        }
    }
}
