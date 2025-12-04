/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.firmware

import android.net.Uri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import java.io.File

sealed interface FirmwareUpdateState {
    data object Idle : FirmwareUpdateState

    data object Checking : FirmwareUpdateState

    data class Ready(
        val release: FirmwareRelease?,
        val deviceHardware: DeviceHardware,
        val address: String,
        val showBootloaderWarning: Boolean,
        val updateMethod: FirmwareUpdateMethod,
        val currentFirmwareVersion: String? = null,
    ) : FirmwareUpdateState

    data class Downloading(val progress: Float) : FirmwareUpdateState

    data class Processing(val message: String) : FirmwareUpdateState

    data class Updating(val progress: Float, val message: String) : FirmwareUpdateState

    data class Error(val error: String) : FirmwareUpdateState

    data object Success : FirmwareUpdateState

    data class AwaitingFileSave(val uf2File: File?, val fileName: String, val sourceUri: Uri? = null) :
        FirmwareUpdateState
}
