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
package org.meshtastic.feature.firmware.ota.dfu

import org.meshtastic.core.resources.UiText
import org.meshtastic.feature.firmware.FirmwareUpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DfuUploadPhaseStateTest {
    private val uploadMsg = UiText.DynamicString("Uploading firmware...")
    private val hint = UiText.DynamicString("slow bootloader")

    @Test
    fun `PREPARING is a Processing state rather than Uploading at zero`() {
        val state = dfuUploadPhaseState(DfuUploadPhase.PREPARING, uploadMsg, hint)
        assertIs<FirmwareUpdateState.Processing>(state)
    }

    @Test
    fun `STREAMING returns to Uploading at zero with the slow hint preserved`() {
        val state = dfuUploadPhaseState(DfuUploadPhase.STREAMING, uploadMsg, hint)
        assertIs<FirmwareUpdateState.Updating>(state)
        assertEquals(uploadMsg, state.progressState.message)
        assertEquals(0f, state.progressState.progress)
        assertEquals(hint, state.progressState.hint)
    }
}
