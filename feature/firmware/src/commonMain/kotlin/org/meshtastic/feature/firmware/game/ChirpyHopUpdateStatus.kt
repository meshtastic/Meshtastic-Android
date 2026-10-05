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
package org.meshtastic.feature.firmware.game

import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_failed
import org.meshtastic.core.resources.firmware_update_success
import org.meshtastic.core.resources.firmware_update_verifying
import org.meshtastic.feature.firmware.FirmwareUpdateState

enum class ChirpyHopUpdatePhase {
    Active,
    Complete,
    Failed,
}

/** The update as the game's status band shows it. A null [progress] draws an indeterminate bar. */
data class ChirpyHopUpdateStatus(val phase: ChirpyHopUpdatePhase, val message: UiText, val progress: Float?)

/**
 * The game is offered only while an update runs unattended. Null means the update needs the user (a file to save, a
 * bootloader to review, a cancel back to Ready), so the game closes rather than covering the screen that asks.
 */
fun FirmwareUpdateState.toChirpyHopStatus(): ChirpyHopUpdateStatus? = when (this) {
    is FirmwareUpdateState.Downloading ->
        ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Active, progressState.message, progressState.progress)

    is FirmwareUpdateState.Updating ->
        ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Active, progressState.message, progressState.progress)

    is FirmwareUpdateState.Processing ->
        if (beforeConfirmation) {
            null
        } else {
            ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Active, progressState.message, null)
        }

    FirmwareUpdateState.Verifying ->
        ChirpyHopUpdateStatus(
            ChirpyHopUpdatePhase.Active,
            UiText.Resource(Res.string.firmware_update_verifying),
            null,
        )

    is FirmwareUpdateState.Success ->
        ChirpyHopUpdateStatus(
            ChirpyHopUpdatePhase.Complete,
            UiText.Resource(Res.string.firmware_update_success),
            1f,
        )

    is FirmwareUpdateState.Error -> ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Failed, error, null)

    FirmwareUpdateState.VerificationFailed ->
        ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Failed, UiText.Resource(Res.string.firmware_update_failed), null)

    FirmwareUpdateState.Idle,
    FirmwareUpdateState.Checking,
    is FirmwareUpdateState.Ready,
    is FirmwareUpdateState.AwaitingFileSave,
    is FirmwareUpdateState.ReviewingBootloader,
    -> null
}
