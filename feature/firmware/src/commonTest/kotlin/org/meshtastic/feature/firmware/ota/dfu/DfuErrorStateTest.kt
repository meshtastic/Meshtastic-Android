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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A run that wrote nothing owes no recovery record; a failure once the board is in its bootloader keeps it. */
class DfuErrorStateTest {

    @Test
    fun `a Secure DFU refusal wrote nothing`() {
        assertTrue(DfuException.BootloaderNotLegacy().toErrorState().nothingWritten)
    }

    @Test
    fun `an unreadable package wrote nothing`() {
        assertTrue(DfuException.InvalidPackage("manifest.json not found in DFU zip").toErrorState().nothingWritten)
    }

    @Test
    fun `a bootloader mismatch wrote nothing`() {
        val mismatch = DfuException.BootloaderMismatch(BootloaderIdentity(null, null), "model", "6.1.1")
        assertTrue(mismatch.toErrorState().nothingWritten)
    }

    @Test
    fun `a failure after the board entered its bootloader keeps the recovery record`() {
        assertFalse(DfuException.TransferFailed("write failed").toErrorState().nothingWritten)
        assertFalse(DfuException.ConnectionFailed("link lost").toErrorState().nothingWritten)
        assertFalse(DfuException.StartTimedOut(kotlin.time.Duration.ZERO).toErrorState().nothingWritten)
        assertFalse(BootloaderSwapTimeout().toErrorState().nothingWritten)
    }
}
