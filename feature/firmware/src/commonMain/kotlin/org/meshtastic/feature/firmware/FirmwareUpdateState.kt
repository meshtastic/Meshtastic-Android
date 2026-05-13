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
package org.meshtastic.feature.firmware

import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.resources.UiText

/**
 * Represents the progress of a long-running firmware update task.
 *
 * @property message A high-level status message (e.g., "Downloading...").
 * @property progress A value between 0.0 and 1.0 representing completion percentage.
 * @property details Optional high-frequency detail text (e.g., "1.2 MiB/s, 45%").
 */
data class ProgressState(
    val message: UiText = UiText.DynamicString(""),
    val progress: Float = 0f,
    val details: String? = null,
)

/** State machine for the firmware update flow, observed by [FirmwareUpdateScreen]. */
sealed interface FirmwareUpdateState {
    /** No update activity — initial state before [FirmwareUpdateViewModel.checkForUpdates] runs. */
    data object Idle : FirmwareUpdateState

    /** Resolving device hardware and fetching available firmware releases. */
    data object Checking : FirmwareUpdateState

    /** Device and release info resolved; the user may initiate an update. */
    data class Ready(
        val release: FirmwareRelease?,
        val deviceHardware: DeviceHardware,
        /** Bare device address with the `InterfaceId` transport prefix stripped (e.g. MAC or IP). */
        val address: String,
        val showBootloaderWarning: Boolean,
        val updateMethod: FirmwareUpdateMethod,
        val currentFirmwareVersion: String? = null,
    ) : FirmwareUpdateState

    /** Firmware file is being downloaded from the release server. */
    data class Downloading(val progressState: ProgressState) : FirmwareUpdateState

    /** Intermediate processing (e.g. extracting, preparing DFU). */
    data class Processing(val progressState: ProgressState) : FirmwareUpdateState

    /** Firmware is actively being written to the device. */
    data class Updating(val progressState: ProgressState) : FirmwareUpdateState

    /** Waiting for the device to reboot and reconnect after a successful flash. */
    data object Verifying : FirmwareUpdateState

    /** The device did not reconnect within the expected timeout after flashing. */
    data object VerificationFailed : FirmwareUpdateState

    /** An error occurred at any stage of the update pipeline. */
    data class Error(val error: UiText) : FirmwareUpdateState

    /** The firmware update completed and the device reconnected successfully. */
    data object Success : FirmwareUpdateState

    /** UF2 file is ready; waiting for the user to choose a save location (USB flow). */
    data class AwaitingFileSave(val uf2Artifact: FirmwareArtifact, val fileName: String) : FirmwareUpdateState
}

private val FORMAT_ARG_REGEX = Regex(":?\\s*%1\\\$d%?")

/** Strip positional format arguments (e.g. `%1$d`) from a localized template to get a clean base message. */
internal fun String.stripFormatArgs(): String = replace(FORMAT_ARG_REGEX, "").trim()
