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

import org.meshtastic.core.resources.UiText
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.feature.firmware.ProgressState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChirpyHopUpdateStatusTest {

    private val progress = ProgressState(UiText.DynamicString("Uploading"), 0.4f)

    @Test
    fun `an upload is playable and carries its progress`() {
        val status = FirmwareUpdateState.Updating(progress).toChirpyHopStatus()

        assertEquals(ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Active, progress.message, 0.4f), status)
    }

    @Test
    fun `a download is playable`() {
        assertEquals(
            ChirpyHopUpdatePhase.Active,
            FirmwareUpdateState.Downloading(progress).toChirpyHopStatus()?.phase,
        )
    }

    @Test
    fun `processing and verifying are playable with no percentage`() {
        val processing = FirmwareUpdateState.Processing(progress).toChirpyHopStatus()
        val verifying = FirmwareUpdateState.Verifying.toChirpyHopStatus()

        assertEquals(ChirpyHopUpdatePhase.Active, processing?.phase)
        assertNull(processing?.progress)
        assertEquals(ChirpyHopUpdatePhase.Active, verifying?.phase)
        assertNull(verifying?.progress)
    }

    @Test
    fun `checking a picked file before confirmation is not an update`() {
        assertNull(FirmwareUpdateState.Processing(progress, beforeConfirmation = true).toChirpyHopStatus())
    }

    @Test
    fun `success completes and errors fail`() {
        val error = UiText.DynamicString("Connection lost")

        assertEquals(ChirpyHopUpdatePhase.Complete, FirmwareUpdateState.Success().toChirpyHopStatus()?.phase)
        assertEquals(
            ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Failed, error, null),
            FirmwareUpdateState.Error(error).toChirpyHopStatus(),
        )
        assertEquals(
            ChirpyHopUpdatePhase.Failed,
            FirmwareUpdateState.VerificationFailed.toChirpyHopStatus()?.phase,
        )
    }

    @Test
    fun `states that need the user close the game`() {
        assertNull(FirmwareUpdateState.Idle.toChirpyHopStatus())
        assertNull(FirmwareUpdateState.Checking.toChirpyHopStatus())
        assertNull(FirmwareUpdateState.AwaitingFileSave(uf2Artifact = null, fileName = null).toChirpyHopStatus())
    }
}
