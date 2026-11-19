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
package org.meshtastic.feature.settings.radio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.export_configuration
import org.meshtastic.core.resources.import_configuration
import org.meshtastic.core.ui.util.rememberOpenFileLauncher
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.feature.settings.radio.component.EditDeviceProfileDialog
import org.meshtastic.feature.settings.util.deviceProfileExportFileName
import org.meshtastic.proto.DeviceProfile
import kotlin.time.Instant.Companion.fromEpochMilliseconds

/** The Import and Export configuration actions, backed by the platform's file pickers. */
class DeviceProfileTransfer(val onImport: () -> Unit, val onExport: () -> Unit)

/**
 * Import reads a profile file and offers it in the edit dialog before installing it; export offers the current profile
 * in that dialog and then asks where to save it. Hosts the dialog, so it must stay in composition while in use.
 */
@Composable
fun rememberDeviceProfileTransfer(viewModel: RadioConfigViewModel): DeviceProfileTransfer {
    // Non-null while the dialog shows an imported profile, and once an export is confirmed, the profile to write.
    var deviceProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val saveFile = rememberSaveFileLauncher { uri -> deviceProfile?.let { viewModel.exportProfile(uri, it) } }
    val openFile = rememberOpenFileLauncher { uri ->
        if (uri != null) {
            viewModel.importProfile(uri) { profile ->
                deviceProfile = profile
                showDialog = true
            }
        }
    }

    if (showDialog) {
        val importing = deviceProfile != null
        EditDeviceProfileDialog(
            title = stringResource(if (importing) Res.string.import_configuration else Res.string.export_configuration),
            deviceProfile = deviceProfile ?: viewModel.currentDeviceProfile,
            onConfirm = { edited ->
                showDialog = false
                if (importing) {
                    viewModel.installProfile(edited)
                } else {
                    deviceProfile = edited
                    saveFile(deviceProfileExportFileName(edited.long_name, edited.short_name, today()), MIME_TYPE)
                }
            },
            onDismiss = {
                showDialog = false
                deviceProfile = null
            },
        )
    }

    return remember(viewModel, saveFile, openFile) {
        DeviceProfileTransfer(
            onImport = {
                viewModel.clearPacketResponse()
                deviceProfile = null
                openFile(ANY_FILE)
            },
            onExport = {
                viewModel.clearPacketResponse()
                deviceProfile = null
                showDialog = true
            },
        )
    }
}

private fun today(): String = fromEpochMilliseconds(nowMillis)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .format(
        LocalDateTime.Format {
            year()
            monthNumber()
            day()
        },
    )

private const val MIME_TYPE = "application/octet-stream"

// A .cfg file has no registered type, so a narrower filter would grey it out.
private const val ANY_FILE = "*/*"
